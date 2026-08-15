FROM eclipse-temurin:21-jdk-noble AS build

WORKDIR /workspace

COPY gradlew gradlew
COPY gradle gradle
RUN ./gradlew --version --no-daemon

COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY config config
RUN ./gradlew --no-daemon dependencies --configuration runtimeClasspath

COPY src src
RUN ./gradlew --no-daemon installDist -x test


FROM eclipse-temurin:21-jre-noble AS runtime

RUN apt-get update \
    && apt-get install --no-install-recommends --yes curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system teslapark \
    && useradd --system --gid teslapark --home-dir /app --shell /usr/sbin/nologin teslapark

WORKDIR /app

COPY --from=build --chown=teslapark:teslapark /workspace/build/install/teslapark-api/lib /app/lib

USER teslapark

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Duser.timezone=UTC"

EXPOSE 3003

HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=5 \
    CMD curl --fail --silent --show-error http://127.0.0.1:3003/health/readiness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -cp '/app/lib/*' com.teslapark.ApplicationKt"]
