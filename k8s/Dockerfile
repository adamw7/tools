# Kubernetes deployment image for running SampleApp (the CSV uniqueness checker).
#
# Why this exists (and is separate from ../assembly/Dockerfile):
# The assembly fat jar bundles the `data` module's spring-boot:repackage output,
# so it has a nested Spring Boot layout (BOOT-INF/classes + BOOT-INF/lib) while its
# manifest Main-Class points at SampleApp. `java -jar` therefore cannot find the
# class. This image expands the fat jar into a flat classpath in a JDK build stage
# (the runtime JRE has no `jar`/`unzip`), and adds a console log4j2 config so the
# result reaches stdout / `kubectl logs` instead of a rolling file.
#
# Build from the repo root AFTER `mvn -DskipTests package`:
#   docker build -f k8s/Dockerfile -t tools-k8s:local .

FROM eclipse-temurin:25-jdk AS build
WORKDIR /build
COPY assembly/target/tools.assembly-*-jar-with-dependencies.jar app.jar
# Expand the fat jar into a flat classpath (BOOT-INF/classes + BOOT-INF/lib).
RUN jar xf app.jar BOOT-INF/classes BOOT-INF/lib

FROM eclipse-temurin:25-jre
LABEL org.opencontainers.image.title="tools-k8s" \
      org.opencontainers.image.description="SampleApp (CSV uniqueness checker) for Kubernetes/minikube." \
      org.opencontainers.image.source="https://github.com/adamw7/tools" \
      org.opencontainers.image.licenses="MIT"

WORKDIR /app
COPY --from=build /build/BOOT-INF/classes ./classes
COPY --from=build /build/BOOT-INF/lib ./lib
# Console log4j config, first on the classpath so it overrides the bundled one.
COPY k8s/log4j2.properties ./config/log4j2.properties

RUN useradd --system --no-create-home --shell /usr/sbin/nologin appuser \
    && chown -R appuser:appuser /app
USER appuser

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

# /app/config is first so its console log4j2.properties wins over classes/.
ENTRYPOINT ["sh", "-c", \
  "exec java $JAVA_OPTS -cp '/app/config:/app/classes:/app/lib/*' io.github.adamw7.tools.data.SampleApp \"$@\"", \
  "--"]
