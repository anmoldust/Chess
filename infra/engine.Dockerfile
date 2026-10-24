# syntax=docker/dockerfile:1
#
# engine-service, with a real Stockfish baked in.
#
# This one cannot use the shared service Dockerfile: the official Linux binary is linked against
# glibc, and the shared runtime image is Alpine (musl) — the binary would not execute. So this
# image is Ubuntu-based and installs wget for the compose health check.
#
# The release ships a "universal" binary that detects CPU features at start-up, so there is no
# AVX2/SSE4.1 build to choose.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml ./
COPY platform ./platform
COPY services ./services

ARG MODULE=services/engine-service
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl ${MODULE} -am -DskipTests package && \
    cp ${MODULE}/target/*.jar /workspace/app.jar

FROM eclipse-temurin:21-jre

ARG STOCKFISH_RELEASE=sf_19
RUN apt-get update \
    && apt-get install -y --no-install-recommends wget ca-certificates \
    && wget -q -O /tmp/stockfish.tar.gz \
       "https://github.com/official-stockfish/Stockfish/releases/download/${STOCKFISH_RELEASE}/stockfish-linux-x86-64-universal.tar.gz" \
    && mkdir -p /opt/stockfish \
    && tar -xzf /tmp/stockfish.tar.gz -C /opt/stockfish \
    && SF_BIN="$(find /opt/stockfish -type f -name 'stockfish*' | head -n 1)" \
    && chmod +x "$SF_BIN" \
    && ln -sf "$SF_BIN" /usr/local/bin/stockfish \
    && rm -f /tmp/stockfish.tar.gz \
    && rm -rf /var/lib/apt/lists/*

# The service reads this and drives the engine over UCI.
ENV CCMMD_ENGINE_BINARY=stockfish

RUN groupadd -r app && useradd -r -g app app
WORKDIR /app
COPY --from=build /workspace/app.jar /app/app.jar
RUN chown -R app:app /app
USER app

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "/app/app.jar"]
