FROM maven:3-eclipse-temurin-24@sha256:a137a467ec89b5713d0be817b55bdba6b4d6ef16e3d05565a79bc08d8e775a1c AS build
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
# Volume nomeado novo montado em /app/uploads herda dono root do diretório
# da imagem; cria e ajusta ownership antes de trocar para o usuário não-root.
RUN mkdir -p /app/uploads && chown -R app:app /app/uploads
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
