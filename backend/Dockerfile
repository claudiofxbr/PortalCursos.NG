FROM maven:3.9.11-eclipse-temurin-21@sha256:6fdc855a6ed81d288ca7ca37ac6ff5e9308b612485c0801d70b25a858c83d237 AS build
WORKDIR /app

# Cache de dependências: só re-baixa quando pom.xml muda
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Build do código
COPY src ./src
RUN mvn clean package -DskipTests

# JDK 21 no runtime = o mesmo do build e do CI (deploy.yml); evita rodar em producao uma versao nunca testada.
FROM eclipse-temurin:21-jre-alpine@sha256:b48e02e0ea88d7bfb305335df94923bdef394905f4147dccd52e873cf17b1088
# apk upgrade: a imagem base ainda nao incorporou patches do Alpine upstream
# para libexpat (CVE-2026-56131/56407/56408), p11-kit/p11-kit-trust
# (CVE-2026-2100), libssl3/libcrypto3 (CVE-2026-14456) e sqlite-libs
# (CVE-2026-11822/11824) e brotli-libs (CVE-2026-111497) — corrigidas aqui em vez de esperar nova build do Temurin.
RUN apk add --no-cache wget && apk upgrade --no-cache libexpat p11-kit p11-kit-trust libssl3 libcrypto3 sqlite-libs brotli-libs && addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build --chown=app:app /app/target/portalcursos-backend.jar app.jar
# Volume nomeado novo montado em /app/uploads herda dono root do diretório
# da imagem; cria e ajusta ownership antes de trocar para o usuário não-root.
RUN mkdir -p /app/uploads && chown -R app:app /app/uploads
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
