package me.akashmaj.demomarketplaceservice;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Scheduler;
import akka.actor.typed.javadsl.*;
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

public class PostOrder extends AbstractBehavior<PostOrder.Command> {

    public interface Command {}
    public static final class Initialize implements Command {}

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

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String orderData;
    private final ActorRef<Gateway.OrderInfo> replyTo;
    private final ClusterSharding sharding;
    private final int orderId;
    private final List<Integer> userIdList;
    private final Scheduler scheduler;

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
        getContext().getSelf().tell(new Initialize());
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
        try {
            getContext().getLog().info("Received order data: {}", orderData);
    
            Map<String, Object> orderRequest = objectMapper.readValue(orderData, new TypeReference<Map<String, Object>>() {});
            
            // Validate user_id
            if (!orderRequest.containsKey("user_id") || orderRequest.get("user_id") == null) {
                replyTo.tell(new Gateway.OrderInfo(orderId, 0, 0, "Invalid order data: Missing user_id", new ArrayList<>()));
                return Behaviors.stopped();
            }
            userId = (Integer) orderRequest.get("user_id");
    
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
                productRef.tell(new Product.GetProductInfo(prodId, adapter));
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
                    replyTo.tell(new Gateway.OrderInfo(orderId, 0, 0, "Insufficient stock for product " + prodId, new ArrayList<>()));
                    return Behaviors.stopped();
                }
                totalCost += quantity * pi.price;
            }
            // Query Account Service for discount detail (simulate with isFirstOrder check)
            boolean discountApplicable = !userIdList.contains(userId);
            finalCost = discountApplicable ? (int)(totalCost * 0.9) : totalCost;
            // Debit wallet.
            if (!debitWallet(userId, finalCost)) {
                replyTo.tell(new Gateway.OrderInfo(orderId, 0, 0, "Insufficient wallet balance", new ArrayList<>()));
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
                productRef.tell(new Product.ReduceStock(quantity, adapter));
            }
            return waitingForStockReduction();
        }
        return this;
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
                // All stock reductions succeeded.
                List<Order.OrderItem> orderItems = new ArrayList<>();
                for (Map<String, Object> item : items) {
                    int prodId = (Integer) item.get("product_id");
                    int quantity = (Integer) item.get("quantity");
                    orderItems.add(new Order.OrderItem(orderId, prodId, quantity));
                }
                EntityRef<Order.Command> orderRef = sharding.entityRefFor(Order.ENTITY_TYPE_KEY, String.valueOf(orderId));
                orderRef.tell(new Order.PlaceOrder(orderId, userId, finalCost, orderItems));
                replyTo.tell(new Gateway.OrderInfo(orderId, userId, finalCost, "PLACED", new ArrayList<>()));
                return Behaviors.stopped();
            } else {
                // At least one stock reduction failed; perform compensation.
                refundWallet(userId, finalCost);
                replyTo.tell(new Gateway.OrderInfo(orderId, 0, 0, "Stock reduction failed, order cancelled", new ArrayList<>()));
                return Behaviors.stopped();
            }
        }
        return this;
    }

    private boolean debitWallet(int user_id, int amount) {
        // Implementation for debiting wallet
        return true;
    }

    private void refundWallet(int user_id, int amount) {
        // Implementation for refunding wallet
    }

    private int getUser(int user_id) {
        // Implementation for getting user details
        return 200;
    }
}