# # Stage 1: Build
# FROM eclipse-temurin:21 AS build

# WORKDIR /app

# # Install Maven
# RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

# # Copy only the POM file first to leverage Docker caching for dependencies
# COPY pom.xml ./

# # Download dependencies (cached unless pom.xml changes)
# RUN mvn dependency:go-offline

# # Copy the rest of the source code
# COPY src ./src

# # Build the application
# RUN mvn clean

# RUN mvn compile -DskipTests

# # # Stage 2: Run
# # FROM eclipse-temurin:21

# # WORKDIR /app

# # # Copy the built JAR from the build stage, keeping the original name
# # COPY --from=build /app/target/account-service-0.0.1-SNAPSHOT.jar /app/

# # Expose application port
# EXPOSE 8080

# # Run the application with the original JAR name
# ENTRYPOINT ["mvn", "exec:java", "-Dexec.mainClass='me.akashmaj.demomarketplaceservice.DemoMarketplaceServiceApplication'"]

# Stage 1: Build
FROM eclipse-temurin:21 AS build

WORKDIR /app

# Install Maven
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

# Copy only the POM file first to cache dependencies
COPY pom.xml ./

# Download dependencies (cached unless pom.xml changes)
RUN mvn dependency:go-offline

# Copy the rest of the source code
COPY src ./src

# Build the application (skip tests for faster build)
RUN mvn clean package -DskipTests

# Stage 2: Run
FROM eclipse-temurin:21

WORKDIR /app

# Copy the built JAR from the build stage, keeping the original name
COPY --from=build /app/target/marketplace-service-0.0.1-SNAPSHOT.jar /app/

# Expose application port
EXPOSE 8080

# Run the application with the original JAR name
ENTRYPOINT ["java", "-jar", "/app/marketplace-service-0.0.1-SNAPSHOT.jar"]
    