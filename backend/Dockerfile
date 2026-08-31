FROM maven:3.9.6-eclipse-temurin-17@sha256:29a1658b1f3078e07c2b17f7b519b45eb47f65d9628e887eac45a8c5c8f939d4 AS build
WORKDIR /app

# Cache de dependências: só re-baixa quando pom.xml muda
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Build do código
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:25-jre-alpine@sha256:3137541deb3cac6626b5d9a4a2187bc0d6a34312f858bd2c67dd01e732e6b682
# apk upgrade: a imagem base ainda nao incorporou patches do Alpine upstream
# para libexpat (CVE-2026-56131/56407/56408), p11-kit/p11-kit-trust
# (CVE-2026-2100), libssl3/libcrypto3 (CVE-2026-14456) e sqlite-libs
# (CVE-2026-11822/11824) — corrigidas aqui em vez de esperar nova build do Temurin.
RUN apk add --no-cache wget && apk upgrade --no-cache libexpat p11-kit p11-kit-trust libssl3 libcrypto3 sqlite-libs && addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build --chown=app:app /app/target/portalcursos-backend.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
