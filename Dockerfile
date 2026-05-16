# ── Stage 1: Build ────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /build

# Cache dependencies separately so re-builds are fast
COPY pom.xml .
RUN mvn dependency:go-offline -B -q

COPY src ./src
RUN mvn package -DskipTests -B -q

# ── Stage 2: SkyWalking Java Agent ───────────────────────────────
FROM apache/skywalking-java-agent:9.3.0-java21 AS sw-agent

# ── Stage 3: Runtime ──────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring

COPY --from=sw-agent /skywalking/agent /app/skywalking-agent
COPY --from=builder /build/target/Ling-innerflow-0.0.1-SNAPSHOT.jar app.jar

RUN chown -R spring:spring /app
USER spring

EXPOSE 8080

ENTRYPOINT ["java", \
  "-Xms256m", "-Xmx512m", \
  "-javaagent:/app/skywalking-agent/skywalking-agent.jar", \
  "-Dskywalking.agent.service_name=innerflow-app", \
  "-Dskywalking.collector.backend_service=skywalking-oap:11800", \
  "-Dspring.profiles.active=prod", \
  "-jar", "app.jar"]
