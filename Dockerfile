FROM openjdk:17 AS builder
ENV GRADLE_OPTS=-Dorg.gradle.daemon=false
ENV GRADLE_USER_HOME=/root/.gradle
RUN mkdir /app
WORKDIR /app
ADD . .
RUN ./gradlew build -x generateJooq --build-cache


#FROM openjdk:17 AS builder
FROM openjdk:17

ENV JAVA_OPTS ""
ENV TZ "Europe/Moscow"

RUN useradd -ms /bin/bash offer

COPY --chown=offer:offer  ./docker-entrypoint.sh /
COPY --from=builder --chown=offer:offer  /app/build/libs/offers-transfer-all.jar /app.jar
USER offer:offer

ENTRYPOINT ["sh", "/docker-entrypoint.sh"]

#HEALTHCHECK --interval=60s --timeout=10s --retries=3  --start-period=30s CMD curl --silent --fail localhost:8080/metrics-micrometer || exit 1
