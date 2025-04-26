package me.akashmaj.demomarketplaceservice;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.net.http.HttpClient;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Scheduler;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.actor.typed.javadsl.AskPattern;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import akka.cluster.sharding.typed.javadsl.EntityRef;
import akka.actor.typed.javadsl.Routers;
import akka.actor.typed.receptionist.Receptionist;
import java.lang.Thread;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoMarketplaceServiceApplication {

    public static ActorSystem<Void> system;
    public static Duration askTimeout;
    public static Scheduler scheduler;
    public static HttpClient httpClient;
    public static ActorRef<Gateway.Command> gateway;
    
    public static ActorRef<PostOrder.Command> postOrderRouter;
    public static ActorRef<DeleteOrder.Command> deleteOrderRouter;

    public static void main(String[] args) throws IOException {
        // Get port from command-line arguments
        String port = System.getProperty("exec.args", "8083");
        Config config = ConfigFactory.parseString("akka.remote.artery.canonical.port=" + port)
                                     .withFallback(ConfigFactory.load("application.conf"));

        system = ActorSystem.create(Behaviors.setup(context -> {


            ClusterSharding sharding = ClusterSharding.get(context.getSystem());

            // Initialize sharded entities (MUST be done by every node)
            sharding.init(Entity.of(Product.ENTITY_TYPE_KEY, ctx -> Product.create(sharding)));
            sharding.init(Entity.of(Order.ENTITY_TYPE_KEY, ctx -> Order.create()));

            // Only primary node will load products
            if ("8083".equals(port)) {
                // Load products from Excel file
                List<Product.InitializeProduct> products = loadProductsFromExcel("products.xlsx");

                // Send products to sharded actors
                for (Product.InitializeProduct product : products) {
                    EntityRef<Product.Command> productRef = sharding.entityRefFor(Product.ENTITY_TYPE_KEY, String.valueOf(product.id));
                    productRef.tell(product);
                    context.getLog().info("Sent init to product shard {}", product.id);
                }

                spawnWorkerActors(context, sharding, scheduler);

            }

            return Behaviors.empty();

        }), "ClusterSystem", config);

        askTimeout = Duration.ofSeconds(30);
        scheduler = system.scheduler();
        httpClient = HttpClient.newHttpClient();
        
    }

        private static List<Product.InitializeProduct> loadProductsFromExcel(String fileName) {
        List<Product.InitializeProduct> products = new ArrayList<>();
        try (InputStream inputStream = DemoMarketplaceServiceApplication.class.getClassLoader().getResourceAsStream(fileName);
             Workbook workbook = new XSSFWorkbook(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0); // Assuming the first sheet contains the data
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // Skip header row

                int id = (int) row.getCell(0).getNumericCellValue();
                String name = row.getCell(1).getStringCellValue();
                String description = row.getCell(2).getStringCellValue();
                int price = (int) row.getCell(3).getNumericCellValue();
                int stock = (int) row.getCell(4).getNumericCellValue();

                products.add(new Product.InitializeProduct(id, name, description, price, stock));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return products;
    }

    private static void startHttpServer(ActorRef<Gateway.Command> gateway, ActorSystem<?> system) throws IOException {

        HttpServer server = HttpServer.create(new InetSocketAddress(8081), 1000);
        server.createContext("/", new HttpHandlerImpl(gateway));
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
            8, 16, 30L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(1000)
        );

        server.setExecutor(threadPoolExecutor);
        server.start();
        System.out.println(">>> HTTP server started on port 8081 <<<");
    }

        private static void spawnWorkerActors(akka.actor.typed.javadsl.ActorContext<Void> context, ClusterSharding sharding, Scheduler scheduler) 
        throws Exception {

            // Create DeleteOrder workers and register them with the receptionist
            for (int i = 0; i < 1000; i++) {
                ActorRef<DeleteOrder.Command> worker = context.spawn(
                    DeleteOrder.create(i, null, sharding),
                    "DeleteOrder-" + i
                );
                // Register each DeleteOrder worker with the receptionist
                context.getSystem().receptionist().tell(Receptionist.register(DeleteOrder.SERVICE_KEY, worker));
            }

            Thread.sleep(2000);

            // Register DeleteOrder GroupRouter
            deleteOrderRouter = context.spawn(
                Routers.group(DeleteOrder.SERVICE_KEY).withRoundRobinRouting(),
                "DeleteOrderRouter"
            );

            Thread.sleep(2000);

            // Create PostOrder workers and register them with the receptionist
            for (int i = 0; i < 1000; i++) {
                ActorRef<PostOrder.Command> worker = context.spawn(
                    PostOrder.create("{}", null, sharding, i, new ArrayList<>(), scheduler),
                    "PostOrder-" + i
                );
                // Register each PostOrder worker with the receptionist
                context.getSystem().receptionist().tell(Receptionist.register(PostOrder.SERVICE_KEY, worker));
            }
            Thread.sleep(2000);
            // Register PostOrder GroupRouter
            postOrderRouter = context.spawn(
                Routers.group(PostOrder.SERVICE_KEY).withRoundRobinRouting(),
                "PostOrderRouter"
            );

            Thread.sleep(2000);

            // Pass the routers to the Gateway actor
            gateway = context.spawn(
                Gateway.create(postOrderRouter, deleteOrderRouter, sharding),
                "Gateway"
            );

            Thread.sleep(2000);
            startHttpServer(gateway, context.getSystem());
        }

    static class HttpHandlerImpl implements HttpHandler {
        private final ActorRef<Gateway.Command> gateway;

        public HttpHandlerImpl(ActorRef<Gateway.Command> gateway) {
            this.gateway = gateway;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String query = exchange.getRequestURI().getQuery();
            System.out.println("HTTP " + method + " " + path + (query != null ? "?" + query : ""));

            try {
                String[] parts = path.split("/");
                if (parts.length >= 2) {
                    if (parts[1].equals("products")) {
                        handleProductRequests(exchange, method, parts);
                    } else if (parts[1].equals("orders")) {
                        handleOrderRequests(exchange, method, parts);
                    } else if (parts[1].equals("marketplace")) {
                        handleMarketplaceRequests(exchange, method);
                    } else {
                        sendResponse(exchange, 404, "Not Found");
                    }
                } else {
                    sendResponse(exchange, 404, "Not Found");
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                sendResponse(exchange, 500, "Internal Server Error: " + ex.getMessage());
            }
        }

        private void handleProductRequests(HttpExchange exchange, String method, String[] parts) throws IOException {
            if (parts.length == 2 && method.equalsIgnoreCase("GET")) {
                AskPattern.ask(gateway,
                    (ActorRef<Gateway.ProductsResponse> replyTo) -> new Gateway.GetAllProducts(replyTo),
                    askTimeout, scheduler)
                .thenAccept(resp -> sendResponse(exchange, 200, resp.toJson()));
            } else if (parts.length == 3 && method.equalsIgnoreCase("GET")) {
                int productId = Integer.parseInt(parts[2]);
                AskPattern.ask(gateway,
                    (ActorRef<Gateway.ProductInfo> replyTo) -> new Gateway.GetProduct(productId, replyTo),
                    askTimeout, scheduler)
                .thenAccept(productInfo -> {
                    if (productInfo.productId == -1)
                        sendResponse(exchange, 404, "Product not found");
                    else
                        sendResponse(exchange, 200, productInfo.toJson());
                });
            } else {
                sendResponse(exchange, 404, "Not Found");
            }
        }

        private void handleOrderRequests(HttpExchange exchange, String method, String[] parts) throws IOException {
            if (parts.length == 2 && method.equalsIgnoreCase("POST")) {
                String body = new String(exchange.getRequestBody().readAllBytes());
                AskPattern.ask(gateway,
                    (ActorRef<Gateway.OrderInfo> replyTo) -> new Gateway.CreateOrder(body, replyTo),
                    askTimeout, scheduler)
                .thenAccept(orderInfo -> {
                    if (!"PLACED".equals(orderInfo.status))
                        sendResponse(exchange, 400, orderInfo.status);
                    else
                        sendResponse(exchange, 201, orderInfo.toJson());
                });
            } else if (parts.length == 3 && method.equalsIgnoreCase("GET")) {
                int orderId = Integer.parseInt(parts[2]);
                AskPattern.ask(gateway,
                    (ActorRef<Gateway.OrderInfo> replyTo) -> new Gateway.GetOrder(orderId, replyTo),
                    askTimeout, scheduler)
                .thenAccept(orderInfo -> {
                    if (orderInfo.orderId == -1)
                        sendResponse(exchange, 404, "Order not found");
                    else
                        sendResponse(exchange, 200, orderInfo.toJson());
                });
            } else if (parts.length == 3 && method.equalsIgnoreCase("DELETE")) {
                int orderId = Integer.parseInt(parts[2]);
                AskPattern.ask(gateway,
                    (ActorRef<Gateway.OrderInfo> replyTo) -> new Gateway.DeleteOrderRequest(orderId, replyTo),
                    askTimeout, scheduler)
                .thenAccept(resp -> {
                    if (!resp.status.equals("CANCELLED"))
                        sendResponse(exchange, 400, "Order cancellation failed");
                    else
                        sendResponse(exchange, 200, resp.toJson());
                });
            } else {
                sendResponse(exchange, 404, "Not Found");
            }
        }

        private void handleMarketplaceRequests(HttpExchange exchange, String method) throws IOException {
            if (method.equalsIgnoreCase("DELETE")) {
                AskPattern.ask(gateway,
                    (ActorRef<Gateway.OrderInfo> replyTo) -> new Gateway.GlobalReset(replyTo),
                    askTimeout, scheduler)
                .thenAccept(resp -> sendResponse(exchange, 200, resp.toJson()));
            } else {
                sendResponse(exchange, 404, "Not Found");
            }
        }

        private void sendResponse(HttpExchange exchange, int statusCode, String response) {
            try {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                byte[] responseBytes = response.getBytes("UTF-8");
                exchange.sendResponseHeaders(statusCode, responseBytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(responseBytes);
                os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}