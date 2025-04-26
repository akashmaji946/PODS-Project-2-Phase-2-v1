package me.akashmaj.demomarketplaceservice;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.EntityRef;

public class Product extends AbstractBehavior<Product.Command> {

    // Key Definitions
    public static final EntityTypeKey<Command> ENTITY_TYPE_KEY = EntityTypeKey.create(Command.class, "Product");

    // Fields
    private final ClusterSharding sharding;
    private int id;
    private String name;
    private String description;
    private int price;
    private int stock_quantity;

    // Create Method
    public static Behavior<Command> create(ClusterSharding sharding) {
        return Behaviors.setup(context -> new Product(context, sharding, 0, "Default", "Default Description", 0, 0));
    }

    // Constructor
    private Product(ActorContext<Command> context, ClusterSharding sharding, int id, String name, String description, int price, int stock_quantity) {
        super(context);
        this.sharding = sharding;
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock_quantity = stock_quantity;
    }

    // On Receive
    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(InitializeProduct.class, this::onInitializeProduct)
            .onMessage(GetProduct.class, this::onGetProduct)
            .onMessage(GetProductInfo.class, this::onGetProductInfo)
            .onMessage(ReduceStock.class, this::onReduceStock)
            .onMessage(RestoreStock.class, this::onRestoreStock)
            .build();
    }

    // Message Definitions
    public interface Command {}

    public static class InitializeProduct implements Command {
        public final int id;
        public final String name;
        public final String description;
        public final int price;
        public final int stockQuantity;

        @JsonCreator
        public InitializeProduct(
            @JsonProperty("id") int id,
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("price") int price,
            @JsonProperty("stockQuantity") int stockQuantity
        ) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.price = price;
            this.stockQuantity = stockQuantity;
        }
    }

    public static class GetProduct implements Command {
        public final int productId;
        public final ActorRef<Gateway.ProductInfo> replyTo;

        @JsonCreator
        public GetProduct(
            @JsonProperty("productId") int productId,
            @JsonProperty("replyTo") ActorRef<Gateway.ProductInfo> replyTo
        ) {
            this.productId = productId;
            this.replyTo = replyTo;
        }
    }

    public static class GetProductInfo implements Command {
        public final int productId;
        public final ActorRef<Gateway.ProductInfo> replyTo;

        @JsonCreator
        public GetProductInfo(
            @JsonProperty("productId") int productId,
            @JsonProperty("replyTo") ActorRef<Gateway.ProductInfo> replyTo
        ) {
            this.productId = productId;
            this.replyTo = replyTo;
        }
    }

    public static class ReduceStock implements Command {
        public final int quantity;
        public final ActorRef<OperationResponse> replyTo;

        @JsonCreator
        public ReduceStock(
            @JsonProperty("quantity") int quantity,
            @JsonProperty("replyTo") ActorRef<OperationResponse> replyTo
        ) {
            this.quantity = quantity;
            this.replyTo = replyTo;
        }
    }

    public static class RestoreStock implements Command {
        public final int quantity;
        public final ActorRef<OperationResponse> replyTo;

        @JsonCreator
        public RestoreStock(
            @JsonProperty("quantity") int quantity,
            @JsonProperty("replyTo") ActorRef<OperationResponse> replyTo
        ) {
            this.quantity = quantity;
            this.replyTo = replyTo;
        }
    }

    public static class OperationResponse {
        public final boolean success;
        public final String message;
        public final int currentStock;

        @JsonCreator
        public OperationResponse(
            @JsonProperty("success") boolean success,
            @JsonProperty("message") String message,
            @JsonProperty("currentStock") int currentStock
        ) {
            this.success = success;
            this.message = message;
            this.currentStock = currentStock;
        }
    }

    // On Handlers
    private Behavior<Command> onInitializeProduct(InitializeProduct msg) {
        this.id = msg.id;
        this.name = msg.name;
        this.description = msg.description;
        this.price = msg.price;
        this.stock_quantity = msg.stockQuantity;
        getContext().getLog().info("Product {} initialized: {}", id, name);
        return this;
    }

    private Behavior<Command> onGetProduct(GetProduct msg) {
        EntityRef<Product.Command> productRef = sharding.entityRefFor(Product.ENTITY_TYPE_KEY, String.valueOf(msg.productId));

        // Send an initialization message if this is the first time the product is accessed.
        productRef.tell(new Product.InitializeProduct(msg.productId, "Product " + msg.productId, "Description for product " + msg.productId, 100, 50));

        productRef.tell(new Product.GetProductInfo(msg.productId, msg.replyTo));
        return this;
    }

    private Behavior<Command> onGetProductInfo(GetProductInfo msg) {
        msg.replyTo.tell(new Gateway.ProductInfo(id, name, description, price, stock_quantity));
        return this;
    }

    private Behavior<Command> onReduceStock(ReduceStock msg) {
        if (stock_quantity >= msg.quantity) {
            stock_quantity -= msg.quantity;
            msg.replyTo.tell(new OperationResponse(true, "Stock reduced", stock_quantity));
        } else {
            msg.replyTo.tell(new OperationResponse(false, "Insufficient stock", stock_quantity));
        }
        return this;
    }

    private Behavior<Command> onRestoreStock(RestoreStock msg) {
        stock_quantity += msg.quantity;
        msg.replyTo.tell(new OperationResponse(true, "Stock restored", stock_quantity));
        return this;
    }
}

