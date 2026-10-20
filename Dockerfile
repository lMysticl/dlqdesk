# Builds DLQDesk from source: React UI, then the Spring Boot JAR, then a JRE runtime.
FROM node:24-bookworm-slim AS ui
WORKDIR /src/ui
COPY ui/package.json ui/package-lock.json ./
RUN npm ci
COPY ui/ ./
RUN npm run build

FROM eclipse-temurin:21-jdk AS app
WORKDIR /src
COPY .mvn/ .mvn/
COPY mvnw pom.xml LICENSE NOTICE ./
COPY src/ src/
COPY --from=ui /src/ui/dist ui/dist
RUN ./mvnw -B -q -DskipTests package && cp target/dlqdesk-*.jar /src/dlqdesk.jar

FROM eclipse-temurin:21-jre
LABEL org.opencontainers.image.source="https://github.com/lMysticl/dlqdesk" \
      org.opencontainers.image.description="Inspect, fix and replay failed Kafka messages" \
      org.opencontainers.image.licenses="MIT"
RUN useradd --system --uid 10001 --home-dir /app dlqdesk \
    && mkdir -p /app/data \
    && chown -R dlqdesk /app
WORKDIR /app
COPY --from=app /src/dlqdesk.jar /app/dlqdesk.jar
# The JAR binds to 127.0.0.1 by default; inside the container it must listen on all
# interfaces. Publish the port on the host's loopback only (see compose.yaml).
ENV SERVER_ADDRESS=0.0.0.0
EXPOSE 8092
VOLUME /app/data
USER dlqdesk
HEALTHCHECK --interval=5s --timeout=3s --start-period=20s --retries=24 \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8092' || exit 1
ENTRYPOINT ["java", "-jar", "/app/dlqdesk.jar"]
