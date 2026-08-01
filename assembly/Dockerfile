# Runtime image for SampleApp. The distribution directory it copies is built
# beforehand by `mvn package` (see .github/workflows/docker.yml) and lives in
# assembly/target/tools.assembly-<version>-dist/.
#
# Build from the repository root, so the shared console log4j2 config under
# docker/ is in the context:
#   mvn -B -DskipTests package
#   docker build -f assembly/Dockerfile -t tools:local .

# Build stage: only here to shrink the DuckDB driver. It ships one native
# library per platform, each around 100 MB unpacked, and this image runs on
# Linux alone — so the macOS and Windows libraries are pure image weight.
# Both Linux architectures stay, because docker.yml publishes linux/amd64 and
# linux/arm64. Needs the JDK image for `jar`; the JRE has no archive tooling.
#
# Pinned to BUILDPLATFORM: repacking a jar produces the same bytes on any
# architecture, so there is no reason to run this stage under QEMU emulation
# during the multi-arch build.
FROM --platform=$BUILDPLATFORM eclipse-temurin:25-jdk AS trim
WORKDIR /work
COPY assembly/target/tools.assembly-*-dist/ ./dist/
RUN set -eux; \
    driver="$(ls dist/lib/duckdb_jdbc-*.jar)"; \
    mkdir unpacked; \
    (cd unpacked && jar xf "/work/${driver}"); \
    rm -f unpacked/libduckdb_java.so_osx_universal unpacked/libduckdb_java.so_windows_amd64; \
    mv unpacked/META-INF/MANIFEST.MF ./MANIFEST.MF; \
    rm -f "${driver}"; \
    jar cfm "${driver}" MANIFEST.MF -C unpacked .

# Use the slimmer JRE variant since we only need to run the app, not compile.
FROM eclipse-temurin:25-jre

# Build metadata, supplied by docker.yml; the defaults keep a local
# `docker build` working without --build-arg.
ARG VERSION=dev
ARG REVISION=unknown
ARG CREATED=unknown

# OCI image metadata.
LABEL org.opencontainers.image.title="tools" \
      org.opencontainers.image.description="Runnable SampleApp distribution for the adamw7/tools project." \
      org.opencontainers.image.source="https://github.com/adamw7/tools" \
      org.opencontainers.image.licenses="MIT" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${REVISION}" \
      org.opencontainers.image.created="${CREATED}"

# Ubuntu 26.04 — what eclipse-temurin:25-jre is built on — ships Canonical's
# Pebble service manager at /usr/bin/pebble. Nothing here uses it: the entrypoint
# below is the JVM itself. It is a statically linked Go binary carrying its own
# copy of golang.org/x/net and the Go standard library, and it is not tracked by
# dpkg, so a `apt-get upgrade` cannot patch it and every CVE fixed upstream stays
# reported against this image — enough on its own to fail the Trivy scan in
# docker.yml, which is set to fail on fixable HIGH/CRITICAL findings. Deleting it
# drops that dead code and its findings; it reclaims no space, since the file
# lives in a base-image layer this only whites out.
RUN rm -f /usr/bin/pebble && rm -rf /var/lib/pebble

# Create the unprivileged user before copying, so COPY --chown can set ownership
# as it writes. A `RUN chown` afterwards would rewrite every copied file into a
# second layer, storing the whole distribution twice in the image.
#
# The UID/GID are fixed and numeric because Kubernetes' runAsNonRoot admission
# check can only verify a non-root image user when USER is a number, and k8s/
# runs this image under the restricted Pod Security Standard.
RUN groupadd --system --gid 10001 appuser \
    && useradd --system --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin appuser

WORKDIR /app

# Dependencies first, in their own layer: they change only when the dependency
# set does, while the launcher jar changes on every build.
COPY --from=trim --chown=10001:10001 /work/dist/lib ./lib
COPY --from=trim --chown=10001:10001 /work/dist/tools.assembly-*.jar app.jar
# Console log config: the bundled one writes to a relative logs/ directory that
# nothing reads and that an unprivileged user cannot create under /app.
COPY --chown=10001:10001 docker/log4j2-console.properties config/log4j2.properties

USER 10001:10001

# Container-aware JVM defaults; override with `docker run -e JDK_JAVA_OPTIONS=...`.
# The `java` launcher reads JDK_JAVA_OPTIONS itself, so the entrypoint needs no
# shell to expand it and the JVM is PID 1 directly.
ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError --enable-native-access=ALL-UNNAMED -Dlog4j2.configurationFile=/app/config/log4j2.properties"

# app.jar's manifest carries Main-Class and a lib/-prefixed Class-Path.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
