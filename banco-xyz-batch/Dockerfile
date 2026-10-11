# Imagen de banco-xyz-batch (multi-stage). Corre la migracion una vez y termina.

# ---------- Etapa 1: compilacion ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package

# ---------- Etapa 2: ejecucion ----------
FROM eclipse-temurin:21-jre
RUN groupadd --system spring && useradd --system --gid spring --no-create-home spring
WORKDIR /app
COPY --from=build --chown=spring:spring /build/target/*.jar app.jar
# Directorio de salida (errores.csv) escribible por el usuario sin privilegios
RUN mkdir -p /app/salida && chown -R spring:spring /app
USER spring
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
