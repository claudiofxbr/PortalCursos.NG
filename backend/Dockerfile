FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# Cache de dependências: só re-baixa quando pom.xml muda
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Build do código
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
RUN apk add --no-cache wget && addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build --chown=app:app /app/target/portalcursos-backend.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
