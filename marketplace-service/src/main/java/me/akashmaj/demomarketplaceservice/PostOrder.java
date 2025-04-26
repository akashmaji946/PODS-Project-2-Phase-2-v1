package me.akashmaj.demomarketplaceservice;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Scheduler;
import akka.actor.typed.javadsl.*;
import akka.actor.typed.receptionist.ServiceKey;
import akka.actor.typed.Behavior;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.net.URI;
import java.net.http.*;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.*;
import java.util.stream.Collectors;

import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty; 

public class PostOrder extends AbstractBehavior<PostOrder.Command> {

    public static final EntityTypeKey<Command> ENTITY_TYPE_KEY = EntityTypeKey.create(Command.class, "PostOrder");
    public static final ServiceKey<Command> SERVICE_KEY = ServiceKey.create(Command.class, "PostOrderService");
    
    public interface Command {}

    public static final class Initialize implements Command {
        private  String orderData;
        private  ActorRef<Gateway.OrderInfo> replyTo;
        private  ClusterSharding sharding;
        private  int orderId;
        private  List<Integer> userIdList;
        private  Scheduler scheduler;

        public Initialize(String orderData,
                           ActorRef<Gateway.OrderInfo> replyTo,
                           ClusterSharding sharding,
                           int orderId,
                           List<Integer> userIdList,
                           Scheduler scheduler) {
            this.orderData = orderData;
            this.replyTo = replyTo;
            this.sharding = sharding;
            this.orderId = orderId;
            this.userIdList = userIdList;
            this.scheduler = scheduler;


        }
    }

    // Message to receive product details from Product actors.
    public static final class ProductDetailResponse implements Command {
        public final Gateway.ProductInfo productInfo;
        public ProductDetailResponse(Gateway.ProductInfo productInfo) {
            this.productInfo = productInfo;
        }
    }

    // Message to receive stock reduction responses.
    public static final class StockReductionResponse implements Command {
        public final int productId;
        public final Product.OperationResponse opResponse;
        public StockReductionResponse(int productId, Product.OperationResponse opResponse) {
            this.productId = productId;
            this.opResponse = opResponse;
        }
    }

    private  ObjectMapper objectMapper = new ObjectMapper();
    private  String orderData;
    private  ActorRef<Gateway.OrderInfo> replyTo;
    private  ClusterSharding sharding;
    private  int orderId;
    private  List<Integer> userIdList;
    private  Scheduler scheduler;

    // Parsed order details.
    private int userId;
    private List<Map<String, Object>> items;

    // State for product detail collection.
    private final Map<Integer, Gateway.ProductInfo> collectedProductInfos = new HashMap<>();
    private int pendingProductResponses = 0;

    // State for stock reduction phase.
    private final Map<Integer, Boolean> stockReductionResults = new HashMap<>();
    private int pendingStockReductionResponses = 0;

    // Cost fields.
    private int totalCost = 0;
    private int finalCost = 0;

    private PostOrder(ActorContext<Command> context,
        String orderData,
        ActorRef<Gateway.OrderInfo> replyTo,
        ClusterSharding sharding,
        int orderId,
        List<Integer> userIdList,
        Scheduler scheduler) {
        super(context);
        this.orderData = orderData;
        this.replyTo = replyTo;
        this.sharding = sharding;
        this.orderId = orderId;
        this.userIdList = userIdList;
        this.scheduler = scheduler;

        // Only send Initialize if replyTo is not null
        if (replyTo != null) {
            getContext().getSelf().tell(new Initialize(orderData, replyTo, sharding, orderId, userIdList, scheduler));
        }
    }

    public static Behavior<Command> create(String orderData,
                                           ActorRef<Gateway.OrderInfo> replyTo,
                                           ClusterSharding sharding,
                                           int orderId,
                                           List<Integer> userIdList,
                                           Scheduler scheduler) {
        return Behaviors.setup(context -> new PostOrder(context, orderData, replyTo, sharding, orderId, userIdList, scheduler));
    }

    // --- Phase 1: Parse order data and collect product details ---
    private Behavior<Command> onInitialize(Initialize msg) {
        this.orderData = msg.orderData;
        this.replyTo = msg.replyTo;
        this.sharding = msg.sharding;
        this.orderId = msg.orderId;
        this.userIdList = msg.userIdList;
        this.scheduler = msg.scheduler;
        try {
            getContext().getLog().info("Received order data: {}", orderData);
    
            Map<String, Object> orderRequest = objectMapper.readValue(orderData, new TypeReference<Map<String, Object>>() {});
            
            // Validate user_id
            if (!orderRequest.containsKey("user_id") || orderRequest.get("user_id") == null) {
                replyTo.tell(new Gateway.OrderInfo(orderId, 0, 0, "Invalid order data: Missing user_id", new ArrayList<>()));
                return Behaviors.stopped();
            }
            userId = (Integer) orderRequest.get("user_id");
            if (getUser(userId) == 404) {
                replyTo.tell(new Gateway.OrderInfo(orderId, 0, 0, "user actor missing for id " + userId, new ArrayList<>()));
                return Behaviors.stopped();
            }
    
            // Validate items
            if (!orderRequest.containsKey("items") || !(orderRequest.get("items") instanceof List)) {
                replyTo.tell(new Gateway.OrderInfo(orderId, 0, 0, "Invalid order data: Missing or invalid items", new ArrayList<>()));
                return Behaviors.stopped();
            }
            items = (List<Map<String, Object>>) orderRequest.get("items");
    
            // Proceed with processing
            pendingProductResponses = items.size();
            for (Map<String, Object> item : items) {
                int prodId = (Integer) item.get("product_id");
                ActorRef<Gateway.ProductInfo> adapter = getContext().messageAdapter(Gateway.ProductInfo.class,
                        info -> new ProductDetailResponse(info));
                EntityRef<Product.Command> productRef = sharding.entityRefFor(Product.ENTITY_TYPE_KEY, String.valueOf(prodId));
                if(productRef != null) {
                    productRef.tell(new Product.GetProductInfo(prodId, adapter));
                } else {
    
                    replyTo.tell(new Gateway.OrderInfo(orderId, 0, 0, "Product not found: " + prodId, convertItemsToOrderItemInfo(items)));
                    return Behaviors.stopped();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            replyTo.tell(new Gateway.OrderInfo(orderId, 0, 0, "Invalid order data", new ArrayList<>()));
            return Behaviors.stopped();
        }
        return this;
    }

    @Override
    public Receive<Command> createReceive() {
        return waitingForProductDetails();
    }

    // --- Phase 1 state: Waiting for product details ---
    private Receive<Command> waitingForProductDetails() {
        return newReceiveBuilder()
            .onMessage(Initialize.class, this::onInitialize)
            .onMessage(ProductDetailResponse.class, this::onProductDetailResponse)
            .build();
    }

    private Behavior<Command> onProductDetailResponse(ProductDetailResponse msg) {
        Gateway.ProductInfo info = msg.productInfo;
        collectedProductInfos.put(info.productId, info);
        pendingProductResponses--;
        if (pendingProductResponses == 0) {
            // All product details received; now compute total cost.
            totalCost = 0;
            for (Map<String, Object> item : items) {
                int prodId = (Integer) item.get("product_id");
                int quantity = (Integer) item.get("quantity");
                Gateway.ProductInfo pi = collectedProductInfos.get(prodId);
                if (pi == null || pi.stock_quantity < quantity) {
                    replyTo.tell(new Gateway.OrderInfo(orderId, 0, 0, "Insufficient stock for product " + prodId, convertItemsToOrderItemInfo(items)));
                    return Behaviors.stopped();
                }
                totalCost += quantity * pi.price;
            }
            // Query Account Service for discount detail (simulate with isFirstOrder check)
            boolean discountApplicable = !userIdList.contains(userId);
            finalCost = discountApplicable ? (int)(totalCost * 0.9) : totalCost;
            // Debit wallet.
            if (!debitWallet(userId, finalCost)) {
                replyTo.tell(new Gateway.OrderInfo(orderId, 0, 0, "Insufficient wallet balance", convertItemsToOrderItemInfo(items)));
                return Behaviors.stopped();
            }
            // Move to Phase 2: Reduce stock.
            pendingStockReductionResponses = items.size();
            for (Map<String, Object> item : items) {
                int prodId = (Integer) item.get("product_id");
                int quantity = (Integer) item.get("quantity");
                ActorRef<Product.OperationResponse> adapter = getContext().messageAdapter(Product.OperationResponse.class,
                        op -> new StockReductionResponse(prodId, op));
                EntityRef<Product.Command> productRef = sharding.entityRefFor(Product.ENTITY_TYPE_KEY, String.valueOf(prodId));
                
                if(productRef != null) {
                    productRef.tell(new Product.ReduceStock(quantity, adapter));
                } else {
                    refundWallet(userId, finalCost);
                    replyTo.tell(new Gateway.OrderInfo(orderId, 0, 0, "Product not found: " + prodId, convertItemsToOrderItemInfo(items)));
                    return Behaviors.stopped();
                }

            }
            return waitingForStockReduction();
        }
        return this;
    }
    
    // Helper method to convert List<Map<String, Object>> to List<Order.OrderItemInfo>
    private List<Order.OrderItemInfo> convertItemsToOrderItemInfo(List<Map<String, Object>> items) {
        List<Order.OrderItemInfo> orderItemInfos = new ArrayList<>();
        for (Map<String, Object> item : items) {
            int productId = (Integer) item.get("product_id");
            int quantity = (Integer) item.get("quantity");
            orderItemInfos.add(new Order.OrderItemInfo(0, productId, quantity)); // Assuming `id` is not required here
        }
        return orderItemInfos;
    }

    // --- Phase 2 state: Waiting for stock reduction responses ---
    private Receive<Command> waitingForStockReduction() {
        return newReceiveBuilder()
            .onMessage(StockReductionResponse.class, this::onStockReductionResponse)
            .build();
    }

    private Behavior<Command> onStockReductionResponse(StockReductionResponse msg) {
        stockReductionResults.put(msg.productId, msg.opResponse.success);
        pendingStockReductionResponses--;

        if (pendingStockReductionResponses == 0) {
            boolean allSuccess = stockReductionResults.values().stream().allMatch(s -> s);
            if (allSuccess) {

                try{

                    updateUserDiscount(userId, true);

                    int orderItemIdCounter = 1;
                    // All stock reductions succeeded.
                    List<Order.OrderItem> orderItems = new ArrayList<>();

                    for (Map<String, Object> item : items) {
                        int prodId = (Integer) item.get("product_id");
                        int quantity = (Integer) item.get("quantity");
                        orderItems.add(new Order.OrderItem(orderItemIdCounter++, prodId, quantity));
                    }

                    // Convert Order.OrderItem list into Order.OrderItemInfo list.
                    List<Order.OrderItemInfo> orderItemInfos = orderItems.stream()
                            .map(oi -> new Order.OrderItemInfo(oi.id, oi.product_id, oi.quantity))
                            .collect(Collectors.toList());

                    EntityRef<Order.Command> orderRef = sharding.entityRefFor(Order.ENTITY_TYPE_KEY, String.valueOf(orderId));


                    orderRef.tell(new Order.PlaceOrder(orderId, userId, finalCost, orderItems));

                    // replyTo.tell(new Gateway.OrderInfo(orderId, userId, finalCost, "PLACED", convertItemsToOrderItemInfo(items)));
                    replyTo.tell(new Gateway.OrderInfo(orderId, userId, finalCost, "PLACED", orderItemInfos));

                    // note here
                    return Behaviors.stopped();

                }catch (Exception e){
                    e.printStackTrace();

                    refundWallet(userId, finalCost);
                    
                    for (Map<String, Object> item : items) {

                        int prodId = (Integer) item.get("product_id");
                        int quantity = (Integer) item.get("quantity");
                        Boolean reduced = stockReductionResults.get(prodId);

                        if(reduced != null && !reduced) {
                            EntityRef<Product.Command> productRef = sharding.entityRefFor(Product.ENTITY_TYPE_KEY, String.valueOf(prodId));
                            productRef.tell(new Product.RestoreStock(quantity,
                                            getContext().messageAdapter(Product.OperationResponse.class, 
                                                    op -> new StockReductionResponse(prodId, op))));
                        }
                    }
                    replyTo.tell(new Gateway.OrderInfo(orderId, 0, 0, "Stock reduction failed, order cancelled", new ArrayList<>()));
                    return Behaviors.stopped();
                }
           
           
            } else {
                // At least one stock reduction failed; perform compensation.
                refundWallet(userId, finalCost);
                for (Map<String, Object> item : items) {
                    int prodId = (Integer) item.get("product_id");
                    int quantity = (Integer) item.get("quantity");
                    Boolean reduced = stockReductionResults.get(prodId);

                    if(reduced != null && reduced) {
                        EntityRef<Product.Command> productRef = sharding.entityRefFor(Product.ENTITY_TYPE_KEY, String.valueOf(prodId));
                        productRef.tell(new Product.RestoreStock(quantity,
                                        getContext().messageAdapter(Product.OperationResponse.class, 
                                                op -> new StockReductionResponse(prodId, op))));
                    }
                }
                replyTo.tell(new Gateway.OrderInfo(orderId, 0, 0, "Stock reduction failed, order cancelled", new ArrayList<>()));
                return Behaviors.stopped();
            }
        }
        return this;
    }


    private boolean debitWallet(int user_id, int amount) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("action", "debit");
            data.put("amount", amount);
            String json = objectMapper.writeValueAsString(data);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI("http://localhost:8082/wallets/" + user_id))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = DemoMarketplaceServiceApplication.httpClient.send(request, BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void refundWallet(int user_id, int amount) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("action", "credit");
            data.put("amount", amount);
            String json = objectMapper.writeValueAsString(data);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI("http://localhost:8082/wallets/" + user_id))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            DemoMarketplaceServiceApplication.httpClient.send(request, BodyHandlers.ofString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateUserDiscount(int user_id, boolean discountAvailed) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("id", user_id);
            data.put("discount_availed", discountAvailed);
            String json = objectMapper.writeValueAsString(data);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI("http://localhost:8080/users"))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            DemoMarketplaceServiceApplication.httpClient.send(request, BodyHandlers.ofString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int getUser(int user_id) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI("http://localhost:8080/users/" + user_id))
                    .header("Content-Type", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = DemoMarketplaceServiceApplication.httpClient.send(request, BodyHandlers.ofString());
            System.out.println("Response: " + response.body());
            return response.statusCode();
        } catch (Exception e) {
            e.printStackTrace();
            return 404;
        }
    }

}