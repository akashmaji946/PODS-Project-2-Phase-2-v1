package me.akashmaj.demomarketplaceservice;

import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.*;
import akka.actor.typed.Behavior;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;

import java.util.ArrayList;
import java.util.List;

public class Order extends AbstractBehavior<Order.Command> {
    private final int orderId;
    private final int user_id;
    private int total_price;
    private String status; // PLACED, CANCELLED, DELIVERED.
    private final List<OrderItem> items;
    private final List<Integer> userIdList;

    public static final EntityTypeKey<Command> ENTITY_TYPE_KEY = EntityTypeKey.create(Command.class, "Order");

    public static Behavior<Command> create(int orderId, int user_id, int total_price, List<OrderItem> items, List<Integer> userIdList) {
        return Behaviors.setup(context -> new Order(context, orderId, user_id, total_price, items, userIdList));
    }

    public static Behavior<Command> create() {
        return Behaviors.setup(context -> new Order(context, 0, 0, 0, new ArrayList<>(), new ArrayList<>()));
    }

    private Order(ActorContext<Command> context, int orderId, int user_id, int total_price, List<OrderItem> items, List<Integer> userIdList) {
        super(context);
        this.orderId = orderId;
        this.user_id = user_id;
        this.total_price = total_price;
        this.status = "PLACED";
        this.items = items;
        this.userIdList = userIdList;
        if (!this.userIdList.contains(user_id)) {
            this.userIdList.add(user_id);
        }
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(GetOrder.class, this::onGetOrder)
            .onMessage(UpdateOrder.class, this::onUpdateOrder)
            .onMessage(CancelOrder.class, this::onCancelOrder)
            .onMessage(PlaceOrder.class, this::onPlaceOrder)
            .build();
    }

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
        if ("PLACED".equals(status)) {
            status = "CANCELLED";
            msg.replyTo.tell(new Gateway.OrderInfo(orderId, user_id, total_price, status, getItemsInfo()));
        } else {
            msg.replyTo.tell(new Gateway.OrderInfo(-1, -1, 0, "", new ArrayList<>()));
        }
        return this;
    }

    private Behavior<Command> onPlaceOrder(PlaceOrder msg) {
        this.status = "PLACED";
        this.total_price = msg.totalPrice;
        this.items.clear();
        this.items.addAll(msg.items);

        if (!userIdList.contains(msg.userId)) {
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

    public interface Command {}

    public static class GetOrder implements Command {
        public final int orderId;
        public final ActorRef<Gateway.OrderInfo> replyTo;
        public GetOrder(int orderId, ActorRef<Gateway.OrderInfo> replyTo) {
            this.orderId = orderId;
            this.replyTo = replyTo;
        }
    }

    public static class UpdateOrder implements Command {
        public final int orderId;
        public final String updateData;
        public final ActorRef<Gateway.OrderInfo> replyTo;
        public UpdateOrder(int orderId, String updateData, ActorRef<Gateway.OrderInfo> replyTo) {
            this.orderId = orderId;
            this.updateData = updateData;
            this.replyTo = replyTo;
        }
    }

    public static class CancelOrder implements Command {
        public final int orderId;
        public final ActorRef<Gateway.OrderInfo> replyTo;
        public CancelOrder(int orderId, ActorRef<Gateway.OrderInfo> replyTo) {
            this.orderId = orderId;
            this.replyTo = replyTo;
        }
    }

    public static class PlaceOrder implements Command {
        public final int orderId;
        public final int userId;
        public final int totalPrice;
        public final List<OrderItem> items;

        public PlaceOrder(int orderId, int userId, int totalPrice, List<OrderItem> items) {
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
        public OrderItem(int id, int product_id, int quantity) {
            this.id = id;
            this.product_id = product_id;
            this.quantity = quantity;
        }
    }

    public static class OrderItemInfo {
        public final int id;
        public final int product_id;
        public final int quantity;
        public OrderItemInfo(int id, int product_id, int quantity) {
            this.id = id;
            this.product_id = product_id;
            this.quantity = quantity;
        }
        public String toJson() {
            return String.format("{\"id\":%d,\"product_id\":%d,\"quantity\":%d}", id, product_id, quantity);
        }
    }
}