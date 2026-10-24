# syntax=docker/dockerfile:1
#
# One Dockerfile for every service. Each is a module in the same Maven reactor, so the build has
# to see the whole repo — but `-pl <module> -am` compiles only that service and its dependencies.
#
#   docker build -f infra/service.Dockerfile --build-arg MODULE=services/game-service .
#
# The Maven repository is a BuildKit cache mount, so building nine services downloads dependencies
# once rather than nine times.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml ./
COPY platform ./platform
COPY services ./services

ARG MODULE
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl ${MODULE} -am -DskipTests package && \
    cp ${MODULE}/target/*.jar /workspace/app.jar

# Alpine: it carries busybox wget, which the compose health checks need. The default
# eclipse-temurin JRE image has neither wget nor curl.
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
COPY --from=build /workspace/app.jar /app/app.jar
RUN chown -R app:app /app
USER app

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "/app/app.jar"]
