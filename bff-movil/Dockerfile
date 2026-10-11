# Imagen de bff-movil (multi-stage)
# ---------- Etapa 1: compilacion ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package

# ---------- Etapa 2: ejecucion ----------
FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system spring \
    && useradd --system --gid spring --no-create-home spring
WORKDIR /app
COPY --from=build --chown=spring:spring /build/target/*.jar app.jar
USER spring
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8444 9444
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
