package me.akashmaj.demomarketplaceservice;

import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.*;
import akka.actor.typed.Behavior;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.EntityRef;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Gateway extends AbstractBehavior<Gateway.Command> {
    private final ClusterSharding sharding;
    private final Map<Integer, ActorRef<Order.Command>> orderActors = new ConcurrentHashMap<>();
    private final List<Integer> userIdList = new ArrayList<>();
    private int orderIdCounter = 1; // Simple order ID generator.

    public static Behavior<Command> create() {
        return Behaviors.setup(Gateway::new);
    }

    private Gateway(ActorContext<Command> context) {
        super(context);
        this.sharding = ClusterSharding.get(context.getSystem());
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            // Products endpoints.
            .onMessage(GetAllProducts.class, this::onGetAllProducts)
            .onMessage(GetProduct.class, this::onGetProduct)
            // Orders endpoints.
            .onMessage(CreateOrder.class, this::onCreateOrder)
            .onMessage(GetOrder.class, this::onGetOrder)
            .onMessage(UpdateOrder.class, this::onUpdateOrder)
            .onMessage(DeleteOrderRequest.class, this::onDeleteOrder)
            .onMessage(GlobalReset.class, this::onGlobalReset)
            .build();
    }

    // GET /products – list all products.
    private Behavior<Command> onGetAllProducts(GetAllProducts msg) {
        // For demo, we return a list of product IDs (this assumes IDs are known).
        List<Integer> productIds = new ArrayList<>(); // Replace with logic to fetch product IDs.
        msg.replyTo.tell(new ProductsResponse(productIds));
        return this;
    }

    // GET /products/{productId}
    private Behavior<Command> onGetProduct(GetProduct msg) {
        EntityRef<Product.Command> productRef = sharding.entityRefFor(Product.ENTITY_TYPE_KEY, String.valueOf(msg.productId));
        productRef.tell(new Product.GetProductInfo(msg.productId, msg.replyTo));
        return this;
    }

    private Behavior<Command> onCreateOrder(CreateOrder msg) {
        String uniqueName = "PostOrder-" + java.util.UUID.randomUUID().toString();
        getContext().spawn(PostOrder.create(msg.orderData, msg.replyTo, sharding, orderIdCounter, userIdList, getContext().getSystem().scheduler()), uniqueName);
        orderIdCounter++;
        return this;
    }

    // GET /orders/{orderId}
    private Behavior<Command> onGetOrder(GetOrder msg) {
        ActorRef<Order.Command> orderActor = orderActors.get(msg.orderId);
        if (orderActor != null) {
            orderActor.tell(new Order.GetOrder(msg.orderId, msg.replyTo));
        } else {
            msg.replyTo.tell(new Gateway.OrderInfo(-1, -1, 0, "", new ArrayList<>()));
        }
        return this;
    }

    // PUT /orders/{orderId} – update order (e.g. mark delivered).
    private Behavior<Command> onUpdateOrder(UpdateOrder msg) {
        ActorRef<Order.Command> orderActor = orderActors.get(msg.orderId);
        if (orderActor != null) {
            orderActor.tell(new Order.UpdateOrder(msg.orderId, msg.updateData, msg.replyTo));
        } else {
            msg.replyTo.tell(new Gateway.OrderInfo(-1, -1, 0, "", new ArrayList<>()));
        }
        return this;
    }

    // DELETE /orders/{orderId} – cancel order by spawning a DeleteOrder worker.
    // DELETE /orders/{orderId} – cancel order by spawning a DeleteOrder worker.
    private Behavior<Command> onDeleteOrder(DeleteOrderRequest msg) {
        String uniqueName = "DeleteOrder-" + java.util.UUID.randomUUID().toString();
        getContext().spawn(DeleteOrder.create(msg.orderId, msg.replyTo, sharding), uniqueName);
        return this;
    }

        // DELETE /marketplace – global reset (cancel all orders in PLACED status).
        private Behavior<Command> onGlobalReset(GlobalReset msg) {
            // Iterate over all order IDs (assuming you have a way to get all order IDs).
            for (int orderId : orderActors.keySet()) { // Replace with a proper way to fetch all order IDs if needed.
                String uniqueName = "DeleteOrder-" + java.util.UUID.randomUUID().toString();
                getContext().spawn(DeleteOrder.create(orderId, msg.replyTo, sharding), uniqueName);
            }
            msg.replyTo.tell(new GeneralResponse(true, "Global reset: Cancelled all orders"));
            return this;
        }

    // ----- Message definitions -----
    public interface Command {}

    // Products messages.
    public static class GetAllProducts implements Command {
        public final ActorRef<ProductsResponse> replyTo;
        public GetAllProducts(ActorRef<ProductsResponse> replyTo) { this.replyTo = replyTo; }
    }

    public static class GetProduct implements Command {
        public final int productId;
        public final ActorRef<ProductInfo> replyTo;
        public GetProduct(int productId, ActorRef<ProductInfo> replyTo) { this.productId = productId; this.replyTo = replyTo; }
    }

    // Orders messages.
    public static class CreateOrder implements Command {
        public final String orderData; // JSON string.
        public final ActorRef<OrderInfo> replyTo;
        public CreateOrder(String orderData, ActorRef<OrderInfo> replyTo) { this.orderData = orderData; this.replyTo = replyTo; }
    }

    public static class GetOrder implements Command {
        public final int orderId;
        public final ActorRef<OrderInfo> replyTo;
        public GetOrder(int orderId, ActorRef<OrderInfo> replyTo) { this.orderId = orderId; this.replyTo = replyTo; }
    }

    public static class UpdateOrder implements Command {
        public final int orderId;
        public final String updateData; // JSON string with "status":"DELIVERED"
        public final ActorRef<OrderInfo> replyTo;
        public UpdateOrder(int orderId, String updateData, ActorRef<OrderInfo> replyTo) { this.orderId = orderId; this.updateData = updateData; this.replyTo = replyTo; }
    }

    public static class DeleteOrderRequest implements Command {
        public final int orderId;
        public final ActorRef<GeneralResponse> replyTo;
        public DeleteOrderRequest(int orderId, ActorRef<GeneralResponse> replyTo) {
            this.orderId = orderId;
            this.replyTo = replyTo;
        }
    }

    public static class GlobalReset implements Command {
        public final ActorRef<GeneralResponse> replyTo;
        public GlobalReset(ActorRef<GeneralResponse> replyTo) { this.replyTo = replyTo; }
    }

    // ----- Response message types -----
    public static class ProductInfo {
        public final int productId;
        public final String name;
        public final String description;
        public final int price;
        public final int stock_quantity;
        public ProductInfo(int productId, String name, String description, int price, int stock_quantity) {
            this.productId = productId;
            this.name = name;
            this.description = description;
            this.price = price;
            this.stock_quantity = stock_quantity;
        }
        public String toJson() {
            return String.format("{\"id\":%d,\"name\":\"%s\",\"description\":\"%s\",\"price\":%d,\"stock_quantity\":%d}",
                productId, name, description, price, stock_quantity);
        }
    }

    public static class ProductsResponse {
        public final List<Integer> productIds;
        public ProductsResponse(List<Integer> productIds) { this.productIds = productIds; }
        public String toJson() {
            StringBuilder sb = new StringBuilder("[");
            for (int id : productIds) {
                sb.append(String.format("{\"id\":%d},", id));
            }
            if (!productIds.isEmpty()) sb.deleteCharAt(sb.length()-1);
            sb.append("]");
            return sb.toString();
        }
    }

    public static class OrderInfo {
        public final int orderId;
        public final int user_id;
        public final int total_price;
        public final String status;
        public final List<Order.OrderItemInfo> items;
        public OrderInfo(int orderId, int user_id, int total_price, String status, List<Order.OrderItemInfo> items) {
            this.orderId = orderId;
            this.user_id = user_id;
            this.total_price = total_price;
            this.status = status;
            this.items = items;
        }
        public String toJson() {
            StringBuilder itemsJson = new StringBuilder("[");
            for (Order.OrderItemInfo item : items) {
                itemsJson.append(item.toJson()).append(",");
            }
            if (!items.isEmpty()) itemsJson.deleteCharAt(itemsJson.length()-1);
            itemsJson.append("]");
            return String.format("{\"order_id\":%d,\"user_id\":%d,\"total_price\":%d,\"status\":\"%s\",\"items\":%s}",
                orderId, user_id, total_price, status, itemsJson.toString());
        }
    }

    public static class GeneralResponse {
        public final boolean success;
        public final String message;
        public GeneralResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
        
        public String toJson() {
            return String.format("{\"success\": %b, \"message\": \"%s\"}", success, message);
        }
    }
}