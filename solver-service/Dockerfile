# Cloud Run image for the Stratum CP-SAT program-synthesis worker.
#
# Build fat jar first (from monorepo root):
#   ./gradlew :solver-service:shadowJar
#   docker build -t stratum-solver solver-service/
#
# Or with build context = monorepo root:
#   docker build -f solver-service/Dockerfile -t stratum-solver .

FROM eclipse-temurin:17-jre-jammy

# OR-Tools JNI uses OpenMP (libgomp).
RUN apt-get update \
    && apt-get install -y --no-install-recommends libgomp1 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Prefer monorepo-root build context path; fall back to module-local context.
COPY solver-service/build/libs/solver-service-all.jar /app/app.jar

ENV PORT=8080
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
EXPOSE 8080

# Natives extract under java.io.tmpdir; keep /tmp writable for non-root.
USER nobody

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
