# syntax=docker/dockerfile:1

# --- Etapa de build ---
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Cachea la resolucion de dependencias
COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon help > /dev/null 2>&1 || true

# Compila y empaqueta (incluye la generacion de los stubs de gRPC/protobuf a partir de
# src/main/proto, tarea previa a compileJava en build.gradle)
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

# --- Etapa de runtime ---
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app app
COPY --from=build /workspace/build/libs/*.jar app.jar
USER app

# 8081: HTTP, hoy solo Actuator (sin REST propio, ver README.md). 9090: gRPC, unico
# protocolo del servicio (RegistroGrpcService/Registrar) — ver docs/contrato-grpc-registro.md.
EXPOSE 8081 9090
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
