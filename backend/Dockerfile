# ============================================================
# Stage 1: Build Spring Boot application
# ============================================================
FROM maven:3.9.9-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy Maven configuration first for better Docker layer caching
COPY pom.xml .

# Download dependencies
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build application
RUN mvn clean package -DskipTests -B


# ============================================================
# Stage 2: Run Spring Boot application
# ============================================================
FROM eclipse-temurin:17-jre-alpine AS runner

WORKDIR /app

# Create non-root user
RUN addgroup -S spring && adduser -S spring -G spring

# Copy generated JAR
COPY --from=builder /app/target/*.jar app.jar

# Use non-root user
USER spring:spring

# Spring Boot port inside container
EXPOSE 8080

ENV SERVER_PORT=8080

ENTRYPOINT ["java", "-jar", "app.jar"]