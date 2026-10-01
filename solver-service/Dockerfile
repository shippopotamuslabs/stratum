# Cloud Run image for the Stratum CP-SAT program-synthesis worker.
#
# Build fat jar first (from monorepo root):
#   ./gradlew :solver-service:shadowJar
#
# Build image with monorepo root as context (required — COPY path below):
#   docker build -f solver-service/Dockerfile -t stratum-solver .
#
# Or via Cloud Build from a staging dir that preserves the same layout:
#   solver-service/build/libs/solver-service-all.jar + this Dockerfile at root.

FROM eclipse-temurin:25-jre-jammy

# OR-Tools JNI uses OpenMP (libgomp).
RUN apt-get update \
    && apt-get install -y --no-install-recommends libgomp1 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Build context must be the monorepo root (or a layout-compatible staging tree).
COPY solver-service/build/libs/solver-service-all.jar /app/app.jar

ENV PORT=8080
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
EXPOSE 8080

# Natives extract under java.io.tmpdir; keep /tmp writable for non-root.
USER nobody

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
