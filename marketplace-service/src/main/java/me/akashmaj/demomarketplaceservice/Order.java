package me.akashmaj.demomarketplaceservice;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class Order extends AbstractBehavior<Order.Command> {

    // Key Definitions
    public static final EntityTypeKey<Command> ENTITY_TYPE_KEY = EntityTypeKey.create(Command.class, "Order");

    // Fields
    private int orderId;
    private int user_id;
    private int total_price;
    private String status; // PLACED, CANCELLED, DELIVERED.
    private final List<OrderItem> items;
    private final List<Integer> userIdList;

    // Create Methods
    public static Behavior<Command> create(int orderId, int user_id, int total_price, List<OrderItem> items, List<Integer> userIdList) {
        return Behaviors.setup(context -> new Order(context, orderId, user_id, total_price, items, userIdList));
    }

    public static Behavior<Command> create() {
        return Behaviors.setup(context -> new Order(context, -1, -1, -1, new ArrayList<>(), new ArrayList<>()));
    }

    // Constructor
    private Order(ActorContext<Command> context, int orderId, int user_id, int total_price, List<OrderItem> items, List<Integer> userIdList) {
        super(context);
        this.orderId = orderId;
        this.user_id = user_id;
        this.total_price = total_price;
        this.status = "";
        this.items = items;
        this.userIdList = userIdList;
        if (!this.userIdList.contains(user_id)) {
            this.userIdList.add(user_id);
        }
    }

    // On Receive
    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(GetOrder.class, this::onGetOrder)
            .onMessage(UpdateOrder.class, this::onUpdateOrder)
            .onMessage(CancelOrder.class, this::onCancelOrder)
            .onMessage(PlaceOrder.class, this::onPlaceOrder)
            .build();
    }

    // Message Definitions
    public interface Command {}

    public static class GetOrder implements Command {
        public final int orderId;
        public final ActorRef<Gateway.OrderInfo> replyTo;

        @JsonCreator
        public GetOrder(
            @JsonProperty("orderId") int orderId,
            @JsonProperty("replyTo") ActorRef<Gateway.OrderInfo> replyTo
        ) {
            this.orderId = orderId;
            this.replyTo = replyTo;
        }
    }

    public static class UpdateOrder implements Command {
        public final int orderId;
        public final String updateData;
        public final ActorRef<Gateway.OrderInfo> replyTo;

        @JsonCreator
        public UpdateOrder(
            @JsonProperty("orderId") int orderId,
            @JsonProperty("updateData") String updateData,
            @JsonProperty("replyTo") ActorRef<Gateway.OrderInfo> replyTo
        ) {
            this.orderId = orderId;
            this.updateData = updateData;
            this.replyTo = replyTo;
        }
    }

    public static class CancelOrder implements Command {
        public final int orderId;
        public final ActorRef<Gateway.OrderInfo> replyTo;
        public final ActorRef<Gateway.OrderInfo> adapter;

        @JsonCreator
        public CancelOrder(
            @JsonProperty("orderId") int orderId,
            @JsonProperty("replyTo") ActorRef<Gateway.OrderInfo> replyTo,
            @JsonProperty("adapter") ActorRef<Gateway.OrderInfo> adapter
        ) {
            this.orderId = orderId;
            this.replyTo = replyTo;
            this.adapter = adapter;
        }
    }

    public static class PlaceOrder implements Command {
        public final int orderId;
        public final int userId;
        public final int totalPrice;
        public final List<OrderItem> items;

        @JsonCreator
        public PlaceOrder(
            @JsonProperty("orderId") int orderId,
            @JsonProperty("userId") int userId,
            @JsonProperty("totalPrice") int totalPrice,
            @JsonProperty("items") List<OrderItem> items
        ) {
            this.orderId = orderId;
            this.userId = userId;
            this.totalPrice = totalPrice;
            this.items = items;
        }
    }

    public static class OrderItem {
        public final int id;
        public final int product_id;
        public final int quantity;

        @JsonCreator
        public OrderItem(
            @JsonProperty("id") int id,
            @JsonProperty("product_id") int product_id,
            @JsonProperty("quantity") int quantity
        ) {
            this.id = id;
            this.product_id = product_id;
            this.quantity = quantity;
        }
    }

    public static class OrderItemInfo {
        public final int id;
        public final int product_id;
        public final int quantity;

        @JsonCreator
        public OrderItemInfo(
            @JsonProperty("id") int id,
            @JsonProperty("product_id") int product_id,
            @JsonProperty("quantity") int quantity
        ) {
            this.id = id;
            this.product_id = product_id;
            this.quantity = quantity;
        }

        public String toJson() {
            return String.format("{\"id\":%d,\"product_id\":%d,\"quantity\":%d}", id, product_id, quantity);
        }
    }

    // On Handlers
    private Behavior<Command> onGetOrder(GetOrder msg) {
        msg.replyTo.tell(new Gateway.OrderInfo(orderId, user_id, total_price, status, getItemsInfo()));
        return this;
    }

    private Behavior<Command> onUpdateOrder(UpdateOrder msg) {
        if ("PLACED".equals(status) && msg.updateData.contains("DELIVERED")) {
            status = "DELIVERED";
            msg.replyTo.tell(new Gateway.OrderInfo(orderId, user_id, total_price, status, getItemsInfo()));
        } else {
            msg.replyTo.tell(new Gateway.OrderInfo(-1, -1, 0, "", new ArrayList<>()));
        }
        return this;
    }

    private Behavior<Command> onCancelOrder(CancelOrder msg) {
        System.out.println(" ------------------------------------> STATUS:" + status);
        if ("PLACED".equals(status)) {
            status = "CANCELLED";
            System.out.println("replyTo: " + msg.replyTo);
            msg.replyTo.tell(new Gateway.OrderInfo(orderId, user_id, total_price, status, getItemsInfo()));

            // Restore stock for each product in the canceled order
            msg.adapter.tell(new Gateway.OrderInfo(orderId, user_id, total_price, status, getItemsInfo()));

        } else {
            msg.replyTo.tell(new Gateway.OrderInfo(-1, -1, 0, "", new ArrayList<>()));
        }
        System.out.println(">< Order " + orderId + " cancelled successfully");

        return this;
    }

    private Behavior<Command> onPlaceOrder(PlaceOrder msg) {
        this.status = "PLACED";
        this.orderId = msg.orderId;
        this.user_id = msg.userId;
        this.total_price = msg.totalPrice;
        this.items.clear();
        this.items.addAll(msg.items);

        if (!userIdList.contains(msg.userId)) {
            System.out.println(" > Discounted: " + user_id);
            userIdList.add(msg.userId);
        }

        getContext().getLog().info("Order {} placed successfully for user {}", msg.orderId, msg.userId);

        return this;
    }

    private List<OrderItemInfo> getItemsInfo() {
        List<OrderItemInfo> infos = new ArrayList<>();
        for (OrderItem item : items) {
            infos.add(new OrderItemInfo(item.id, item.product_id, item.quantity));
        }
        return infos;
    }
}
