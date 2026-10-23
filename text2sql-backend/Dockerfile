# text2sql-backend — multi-stage build.
#
# Build (JDK 25 + Maven):  the same toolchain the project targets (java.version=25).
# Run   (JRE 25, Alpine):  a small runtime image, running as a non-root user.
#
# Build context is this directory (text2sql-backend/), see deploy/docker-compose.prod.yml.
#
# The base images are build args so a deployment can point them at a registry mirror without
# editing this file: region-locked networks (e.g. mainland China) cannot reach Docker Hub, and
# deploy/.env supplies Huawei Cloud SWR equivalents. The defaults are plain Docker Hub references.

ARG MAVEN_IMAGE=maven:3.9.16-eclipse-temurin-25-noble
ARG JRE_IMAGE=eclipse-temurin:25-jre-alpine

# --- Build stage --------------------------------------------------------------------------------
FROM ${MAVEN_IMAGE} AS build
WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
# Tests need no external services (they run on H2), but they are skipped here so an image build
# never depends on the test suite; run them locally with ./mvnw test.
RUN mvn -B -DskipTests package

# --- Runtime stage ------------------------------------------------------------------------------
FROM ${JRE_IMAGE}

RUN addgroup -S app && adduser -S -G app app

WORKDIR /app
COPY --from=build /workspace/target/*.jar /app/app.jar

# AgentScope resolves text2sql.agent.workspace (default "./agentscope/workspace") against the
# working directory and reads AGENTS.md + knowledge/SCHEMA.md from it, so it must ship in the image.
COPY agentscope ./agentscope

RUN chown -R app:app /app
USER app

EXPOSE 8080
# MaxRAMPercentage keeps the heap inside the container limit instead of the host's memory.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
