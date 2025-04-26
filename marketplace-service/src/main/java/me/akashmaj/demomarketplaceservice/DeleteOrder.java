package me.akashmaj.demomarketplaceservice;

import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.*;
import akka.actor.typed.receptionist.ServiceKey;
import akka.actor.typed.Behavior;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.HashMap;
import java.util.Map;

import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DeleteOrder extends AbstractBehavior<DeleteOrder.Command> {

    // Key Definitions
    public static final EntityTypeKey<Command> ENTITY_TYPE_KEY = EntityTypeKey.create(Command.class, "DeleteOrder");
    public static final ServiceKey<Command> SERVICE_KEY = ServiceKey.create(Command.class, "DeleteOrderService");

    // Fields
    private final ObjectMapper objectMapper = new ObjectMapper();
    private int orderId;
    private ActorRef<Gateway.OrderInfo> replyTo;
    private ClusterSharding sharding;
    private final ActorRef<Product.OperationResponse> stockResponseAdapter =
            getContext().messageAdapter(Product.OperationResponse.class, StockRestored::new);

    // Create Method
    public static Behavior<Command> create(int orderId,
                                           ActorRef<Gateway.OrderInfo> replyTo,
                                           ClusterSharding sharding) {
        return Behaviors.setup(context -> new DeleteOrder(context, orderId, replyTo, sharding));
    }

    // Constructor
    private DeleteOrder(ActorContext<Command> context,
                        int orderId,
                        ActorRef<Gateway.OrderInfo> replyTo,
                        ClusterSharding sharding) {
        super(context);
        this.orderId = orderId;
        this.replyTo = replyTo;
        this.sharding = sharding;

        // Start the cancellation process.
        if (replyTo != null) {
            getContext().getSelf().tell(new InitiateCancellation(orderId, replyTo, sharding));
        }
    }

    // On Receive
    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(InitiateCancellation.class, this::onInitiateCancellation)
                .onMessage(CancelOrderResponse.class, this::onCancelOrderResponse)
                .onMessage(StockRestored.class, this::onStockRestored)
                .build();
    }

    // Message Definitions
    public interface Command {}

    public static class InitiateCancellation implements Command {
        private final int orderId;
        private final ActorRef<Gateway.OrderInfo> replyTo;
        private final ClusterSharding sharding;

        public InitiateCancellation(int orderId, ActorRef<Gateway.OrderInfo> replyTo, ClusterSharding sharding) {
            this.orderId = orderId;
            this.replyTo = replyTo;
            this.sharding = sharding;
        }
    }

    public static class CancelOrderResponse implements Command {
        public final Gateway.OrderInfo orderInfo;

        public CancelOrderResponse(Gateway.OrderInfo orderInfo) {
            this.orderInfo = orderInfo;
        }
    }

    public static class StockRestored implements Command {
        public final Product.OperationResponse response;

        public StockRestored(Product.OperationResponse response) {
            this.response = response;
        }
    }

    // On Handlers
    private Behavior<Command> onInitiateCancellation(InitiateCancellation msg) {
        this.replyTo = msg.replyTo;
        this.orderId = msg.orderId;
        this.sharding = msg.sharding;

        // Use ClusterSharding to get the EntityRef for the Order actor.
        System.out.println(">>> DELETE Order ID: " + msg.orderId);
        EntityRef<Order.Command> orderRef = msg.sharding.entityRefFor(Order.ENTITY_TYPE_KEY, String.valueOf(msg.orderId));
        ActorRef<Gateway.OrderInfo> adapter =
                getContext().messageAdapter(Gateway.OrderInfo.class, CancelOrderResponse::new);

        // Send the cancellation request to the Order actor.
        System.out.println("ADAPTER: " + adapter);
        System.out.println("REPLY TO: " + msg.replyTo);
        orderRef.tell(new Order.CancelOrder(msg.orderId, msg.replyTo, adapter));
        return this;
    }

    private Behavior<Command> onCancelOrderResponse(CancelOrderResponse response) {
        if (replyTo == null) {
            getContext().getLog().warn("replyTo is null. Skipping response.");
            return Behaviors.same();
        }
        System.out.println("I am called? " + response.orderInfo.orderId);
        Gateway.OrderInfo info = response.orderInfo;

        if ("CANCELLED".equals(info.status)) {
            // Restore stock for each product in the canceled order
            for (Order.OrderItemInfo item : info.items) {
                EntityRef<Product.Command> productRef = sharding.entityRefFor(Product.ENTITY_TYPE_KEY, String.valueOf(item.product_id));
                productRef.tell(new Product.RestoreStock(item.quantity, stockResponseAdapter));
            }
            refundWallet(info.user_id, info.total_price);
            replyTo.tell(new Gateway.OrderInfo(orderId, info.user_id, info.total_price, "CANCELLED", info.items));
        } else {
            replyTo.tell(new Gateway.OrderInfo(orderId, info.user_id, info.total_price, "FAILED", info.items));
        }

        return Behaviors.same();
    }

    private Behavior<Command> onStockRestored(StockRestored msg) {
        getContext().getLog().info("Stock restored: {}", msg.response.message);
        return this;
    }

    // Helper Methods
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
}