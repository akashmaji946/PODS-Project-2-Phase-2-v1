# Demo Marketplace Microservices

This project includes three microservices:
- **Account Service** (`account-service`)
- **Marketplace Service** (`marketplace-service`)
- **Wallet Service** (`wallet-service`)

These services are built with Spring Boot and Akka Cluster (for marketplace service) and can be run **locally** or using **Docker**.

---

## 📦 Build & Run Locally

### 1. Account Service
```bash
cd account-service
mvn clean package
# Start the user node
java -jar target/account-service-0.0.1-SNAPSHOT.jar
```

### 2. Marketplace Service
```bash
cd marketplace-service
mvn clean package
# Start the primary marketplace node
java -Dexec.args=8083 -jar target/marketplace-service-0.0.1-SNAPSHOT.jar
# Start an additional marketplace node
java -Dexec.args=8084 -jar target/marketplace-service-0.0.1-SNAPSHOT.jar
```

### 3. Wallet Service
```bash
cd wallet-service
mvn clean package
# Start the wallet node
java -jar target/wallet-service-0.0.1-SNAPSHOT.jar
```

---

## 🐳 Docker Instructions
Refer `docker` branch

---

## 📚 Notes
- Marketplace service uses **Akka Cluster Sharding** internally.
- Running two instances of marketplace service (ports 8083 and 8084) enables **cluster node communication**.
- The HTTP server inside marketplace-service listens on port **8081** (for client requests).
- Use provided `products.xlsx` file to initialize products (required inside marketplace-service resources).

