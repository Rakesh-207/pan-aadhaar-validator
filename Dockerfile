# syntax=docker/dockerfile:1

# ---- Stage 1: builder (Maven + JDK 21) ----
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package

# ---- Stage 2: runtime (JRE 21 only) ----
FROM eclipse-temurin:21-jre AS runtime
RUN groupadd -r appuser && useradd -r -g appuser appuser
WORKDIR /app
COPY --from=builder --chown=appuser:appuser /build/target/pan-aadhaar-validator-*.jar /app/app.jar
ENV PORT=8080
EXPOSE 8080
USER appuser
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
