FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# Cache de dependências: só re-baixa quando pom.xml muda
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Build do código
COPY src ./src
RUN mvn clean package -DskipTests -o

FROM eclipse-temurin:17-jre-alpine
RUN apk add --no-cache wget
WORKDIR /app
COPY --from=build /app/target/portalcursos-backend.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
