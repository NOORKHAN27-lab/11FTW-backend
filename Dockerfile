# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

# ---- Runtime stage ----
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# All the real secrets (JWT_SECRET, DB_*, SMTP_*, etc.) are passed as
# environment variables at `docker run` / docker-compose time — see
# application.properties for the full list and SETUP_GUIDE.md for what
# each one does. Nothing sensitive is baked into this image.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
