# Runtime-only image: the jar-with-dependencies is built beforehand by
# `mvn package` (see .github/workflows/docker.yml) and lives in target/.
# Use the slimmer JRE variant since we only need to run the jar, not compile.
FROM eclipse-temurin:25-jre

# OCI image metadata.
LABEL org.opencontainers.image.title="tools" \
      org.opencontainers.image.description="Runnable SampleApp distribution for the adamw7/tools project." \
      org.opencontainers.image.source="https://github.com/adamw7/tools" \
      org.opencontainers.image.licenses="MIT"

WORKDIR /app

# Copy the assembled fat jar under a fixed name.
COPY target/tools.assembly-*-jar-with-dependencies.jar app.jar

# Run as an unprivileged user rather than root.
RUN useradd --system --no-create-home --shell /usr/sbin/nologin appuser \
    && chown appuser:appuser app.jar
USER appuser

# Container-aware JVM defaults; override with `docker run -e JAVA_OPTS=...`.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar \"$@\"", "--"]
