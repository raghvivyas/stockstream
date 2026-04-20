# ── Stage 1: Build ─────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-8 AS builder

WORKDIR /build
COPY pom.xml .
# Download deps first (cached layer — only re-runs when pom.xml changes)
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn clean package -DskipTests -q

# ── Stage 2: Runtime ───────────────────────────────────────────────────────
FROM eclipse-temurin:8-jre-alpine

WORKDIR /app

# Non-root user for security
RUN addgroup -S stockstream && adduser -S stockstream -G stockstream
USER stockstream

COPY --from=builder /build/target/stockstream-1.0.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
  "-Xms256m", "-Xmx512m", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
