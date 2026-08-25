# syntax=docker/dockerfile:1
###############################################################################
# DeployForge AI backend
#
# Multi-stage: the runtime image carries a JRE and the application jar, not Maven
# and not a full JDK.
###############################################################################

FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

# Dependencies are resolved from the POM alone first, so editing source code does not
# invalidate the dependency layer on every rebuild.
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

# --------------------------------------------------------------------------- #

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# wget is used by the compose health check; git is not required because cloning uses JGit.
RUN apk add --no-cache wget tzdata \
    && addgroup -S deployforge \
    && adduser -S deployforge -G deployforge \
    && mkdir -p /var/lib/deployforge/builds \
    && chown -R deployforge:deployforge /var/lib/deployforge /app

COPY --from=builder /build/target/*.jar /app/deployforge.jar

USER deployforge

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70.0 -XX:+UseSerialGC -Djava.security.egd=file:/dev/./urandom"
ENV SERVER_PORT=8080
EXPOSE 8080

# exec form so the JVM is PID 1 and receives SIGTERM for graceful shutdown.
ENTRYPOINT ["java", "-jar", "/app/deployforge.jar"]
