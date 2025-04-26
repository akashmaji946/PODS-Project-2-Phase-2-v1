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

    public static final EntityTypeKey<Command> ENTITY_TYPE_KEY = EntityTypeKey.create(Command.class, "DeleteOrder");
    public static final ServiceKey<Command> SERVICE_KEY = ServiceKey.create(Command.class, "DeleteOrderService");

    // Message interface for DeleteOrder.
    public interface Command {}
    public static final class InitiateCancellation implements Command {}

    // Message sent when the Order actor replies to the cancellation request.
    public static class CancelOrderResponse implements Command {
        public final Gateway.OrderInfo orderInfo;
        public CancelOrderResponse(Gateway.OrderInfo orderInfo) {
            this.orderInfo = orderInfo;
        }
    }

    private final int orderId;
    private final ActorRef<Gateway.GeneralResponse> replyTo;
    private final ClusterSharding sharding;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private DeleteOrder(ActorContext<Command> context,
                        int orderId,
                        ActorRef<Gateway.GeneralResponse> replyTo,
                        ClusterSharding sharding) {
        super(context);
        this.orderId = orderId;
        this.replyTo = replyTo;
        this.sharding = sharding;

        // Start the cancellation process.
        getContext().getSelf().tell(new InitiateCancellation());
    }

    private Behavior<Command> onInitiateCancellation(InitiateCancellation msg) {
        // Use ClusterSharding to get the EntityRef for the Order actor.
        EntityRef<Order.Command> orderRef = sharding.entityRefFor(Order.ENTITY_TYPE_KEY, String.valueOf(orderId));
        ActorRef<Gateway.OrderInfo> adapter =
                getContext().messageAdapter(Gateway.OrderInfo.class, CancelOrderResponse::new);

        // Send the cancellation request to the Order actor.
        orderRef.tell(new Order.CancelOrder(orderId, adapter));
        return this;
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(InitiateCancellation.class, this::onInitiateCancellation)
            .onMessage(CancelOrderResponse.class, this::onCancelOrderResponse)
            .onMessage(StockRestored.class, this::onStockRestored)
            .build();
    }

    private Behavior<Command> onStockRestored(StockRestored msg) {
        getContext().getLog().info("Stock restored: {}", msg.response.message);
        return this;
    }

    private final ActorRef<Product.OperationResponse> stockResponseAdapter =
    getContext().messageAdapter(Product.OperationResponse.class, StockRestored::new);

    public static class StockRestored implements Command {
        public final Product.OperationResponse response;
    
        public StockRestored(Product.OperationResponse response) {
            this.response = response;
        }
    }

    private Behavior<Command> onCancelOrderResponse(CancelOrderResponse response) {
        if (replyTo == null) {
            getContext().getLog().warn("replyTo is null. Skipping response.");
            return Behaviors.stopped();
        }
    
        Gateway.OrderInfo info = response.orderInfo;
    
        if ("CANCELLED".equals(info.status)) {
            // Restore stock for each product in the canceled order
            for (Order.OrderItemInfo item : info.items) {
                EntityRef<Product.Command> productRef = sharding.entityRefFor(Product.ENTITY_TYPE_KEY, String.valueOf(item.product_id));
                productRef.tell(new Product.RestoreStock(item.quantity, stockResponseAdapter));
            }
            refundWallet(info.user_id, info.total_price);
            replyTo.tell(new Gateway.GeneralResponse(true, "Order " + orderId + " cancelled successfully"));
        } else {
            replyTo.tell(new Gateway.GeneralResponse(false, "Failed to cancel order"));
        }
    
        // return Behaviors.stopped();
        return Behaviors.same();
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

    public static Behavior<Command> create(int orderId,
                                           ActorRef<Gateway.GeneralResponse> replyTo,
                                           ClusterSharding sharding) {
        return Behaviors.setup(context -> new DeleteOrder(context, orderId, replyTo, sharding));
    }
}