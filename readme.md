# Marketplace Microservice

This project includes three microservices:
- **Account Service** (`account-service`)
- **Marketplace Service** (`marketplace-service`)
- **Wallet Service** (`wallet-service`)

These services are built with Spring Boot and Akka Cluster (for marketplace service) and can be run **locally** or using **Docker**.

---

## 📦 Build & Run Locally

Refer to the `dev` branch

---

## 🐳 Docker Instructions

> **⚠️ Note:** Docker steps are provided for reference only.  
> **Do not run Docker steps now without changing code (:refer this branch:) if running locally.**

### Build Docker Images

```bash
docker build -t account-service .
docker build -t marketplace-service .
docker build -t wallet-service .
```

### Deploy Containers
Run the two services like this:
```bash
docker run -p 8080:8080 --rm --name account \
           --add-host=host.docker.internal:host-gateway \
           account-service

docker run -p 8082:8080 --rm --name wallet \
           --add-host=host.docker.internal:host-gateway \
           wallet-service
```
Run the marketplace with replicas
```bash
docker run --net=host --rm --name marketplace \
           marketplace-service -Dexec.args=8083

docker run --net=host --rm --name marketplace-1 \
           marketplace-service -Dexec.args=8084

docker run --net=host --rm --name marketplace-2 \
           marketplace-service -Dexec.args=8085

```

### Stop Running Containers
```bash
docker stop account wallet marketplace marketplace-1 marketplace-2
```

### Remove Containers
```bash
docker rm account wallet marketplace marketplace-1 marketplace-2
```

### Clean Up Docker Images
```bash
docker rmi account-service marketplace-service wallet-service
```

---

## 📚 Notes
- Marketplace service uses **Akka Cluster Sharding** internally.
- Running two instances of marketplace service (ports 8083 and 8084, and possible more) enables **cluster node communication**.
- The HTTP server inside marketplace-service listens on port **8081** (for client requests).
- Use provided `products.xlsx` file to initialize products (required inside marketplace-service resources).

## Access them at:
- https://localhost:8080 [account-service]
- https://localhost:8081 [marketplace-service]
- https://localhost:8082 [wallet-service]

### Thank You
#### Akash Maji [akashmaji@iisc.ac.in] 
