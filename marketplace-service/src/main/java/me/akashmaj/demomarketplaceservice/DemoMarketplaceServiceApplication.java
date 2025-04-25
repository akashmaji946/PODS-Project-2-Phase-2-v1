package me.akashmaj.demomarketplaceservice;

import java.io.IOException;
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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import akka.cluster.sharding.typed.javadsl.EntityRef; 

import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoMarketplaceServiceApplication {
    public static ActorSystem<Void> system;
    public static Duration askTimeout;
    public static Scheduler scheduler;
    public static HttpClient httpClient;

    public static void main(String[] args) throws IOException {
        // Get port from command-line arguments
        String port = System.getProperty("exec.args", "8083");
        Config config = ConfigFactory.parseString("akka.remote.artery.canonical.port=" + port)
                                     .withFallback(ConfigFactory.load("application.conf"));

        system = ActorSystem.create(Behaviors.setup(context -> {
            ClusterSharding sharding = ClusterSharding.get(context.getSystem());

            // Initialize sharded entities
            sharding.init(Entity.of(Product.ENTITY_TYPE_KEY, ctx -> Product.create(sharding)));
            sharding.init(Entity.of(Order.ENTITY_TYPE_KEY, ctx -> Order.create()));

            // Initialize sharded entities
    sharding.init(Entity.of(Product.ENTITY_TYPE_KEY, ctx -> Product.create(sharding)));
    sharding.init(Entity.of(Order.ENTITY_TYPE_KEY, ctx -> Order.create()));

            // Pre-create product actors
            // List<Product.InitializeProduct> products = List.of(
            //     new Product.InitializeProduct(1, "Product 1", "Description for Product 1", 100, 50),
            //     new Product.InitializeProduct(2, "Product 2", "Description for Product 2", 200, 30),
            //     new Product.InitializeProduct(3, "Product 3", "Description for Product 3", 150, 20)
            // );

            // for (Product.InitializeProduct product : products) {
            //     EntityRef<Product.Command> productRef = sharding.entityRefFor(Product.ENTITY_TYPE_KEY, String.valueOf(product.id));
            //     productRef.tell(product);
            // }

            // Pre-create product actors
            List<Product.InitializeProduct> products = List.of(
                new Product.InitializeProduct(101, "Laptop Pro 1", "Powerful laptop", 55000, 10),
                new Product.InitializeProduct(102, "Laptop Air 2", "Lightweight laptop", 45000, 8),
                new Product.InitializeProduct(103, "Gaming Keyboard", "RGB mechanical keyboard", 3000, 15),
                new Product.InitializeProduct(104, "Wireless Mouse", "2.4 GHz wireless mouse", 700, 20),
                new Product.InitializeProduct(105, "Smartphone X", "Android phone with 128GB", 20000, 12),
                new Product.InitializeProduct(106, "Smart TV", "50-inch 4K UHD", 35000, 5),
                new Product.InitializeProduct(107, "Headphones", "Noise-cancelling headphones", 3000, 25),
                new Product.InitializeProduct(108, "Bluetooth Speaker", "Portable and waterproof", 2000, 5),
                new Product.InitializeProduct(109, "Smartwatch", "Fitness tracking smartwatch", 5000, 10),
                new Product.InitializeProduct(110, "Tablet Pro", "10-inch tablet", 15000, 7),
                new Product.InitializeProduct(111, "External HDD", "1TB USB 3.0", 4000, 12),
                new Product.InitializeProduct(112, "USB-C Charger", "Fast charging adapter", 1200, 16),
                new Product.InitializeProduct(113, "Electric Kettle", "1.5L stainless steel", 1800, 9),
                new Product.InitializeProduct(114, "Air Purifier", "HEPA filter device", 6000, 4),
                new Product.InitializeProduct(115, "Microwave Oven", "20L capacity", 8000, 6),
                new Product.InitializeProduct(116, "Refrigerator Mini", "100L single door", 12000, 3),
                new Product.InitializeProduct(117, "Vacuum Cleaner", "Handheld vacuum", 3000, 10),
                new Product.InitializeProduct(118, "Fitness Band", "Heart rate monitor", 2500, 14),
                new Product.InitializeProduct(119, "Desktop Monitor", "24-inch LED", 8000, 8),
                new Product.InitializeProduct(120, "Wireless Earbuds", "Bluetooth 5.0 earbuds", 2500, 15)
            );

            for (Product.InitializeProduct product : products) {
                EntityRef<Product.Command> productRef = sharding.entityRefFor(Product.ENTITY_TYPE_KEY, String.valueOf(product.id));
                productRef.tell(product);
            }

            // If primary node (port 8083), start HTTP server and Gateway actor
            if ("8083".equals(port)) {
                ActorRef<Gateway.Command> gateway = context.spawn(Gateway.create(), "Gateway");
                startHttpServer(gateway, context.getSystem());
            }

            // Spawn worker actors for GroupRouter
            spawnWorkerActors(context, sharding, scheduler);

            return Behaviors.empty();
        }), "ClusterSystem", config);

        askTimeout = Duration.ofSeconds(30);
        scheduler = system.scheduler();
        httpClient = HttpClient.newHttpClient();
    }

    private static void startHttpServer(ActorRef<Gateway.Command> gateway, ActorSystem<?> system) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8081), 1000);
        server.createContext("/", new HttpHandlerImpl(gateway));
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
            8, 16, 30L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(1000)
        );
        server.setExecutor(threadPoolExecutor);
        server.start();
        System.out.println(">>> HTTP server started on port 8081 localhost");
    }

        private static void spawnWorkerActors(akka.actor.typed.javadsl.ActorContext<Void> context, ClusterSharding sharding, Scheduler scheduler) {
            for (int i = 0; i < 50; i++) {
                // Replace with appropriate arguments for PostOrder
                context.spawn(PostOrder.create("{}", null, sharding, i, new ArrayList<>(), scheduler), "PostOrder-" + i);

                // Replace with appropriate arguments for DeleteOrder
                context.spawn(DeleteOrder.create(i, null, sharding), "DeleteOrder-" + i);
            }
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
                    (ActorRef<Gateway.GeneralResponse> replyTo) -> new Gateway.DeleteOrderRequest(orderId, replyTo),
                    askTimeout, scheduler)
                .thenAccept(resp -> {
                    if (!resp.success)
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
                    (ActorRef<Gateway.GeneralResponse> replyTo) -> new Gateway.GlobalReset(replyTo),
                    askTimeout, scheduler)
                .thenAccept(resp -> sendResponse(exchange, 200, resp.message));
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