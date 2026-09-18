FROM eclipse-temurin:17-jre-alpine AS runner
WORKDIR /app

# Non-root security user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy pre-packaged jar
COPY target/*.jar app.jar

EXPOSE 8080
ENV SERVER_PORT=8080

ENTRYPOINT ["java", "-jar", "app.jar"]