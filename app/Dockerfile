# Build context là thư mục gốc của dự án (xem docker-compose.yml)
FROM maven:3.9-eclipse-temurin-21 AS otel
RUN mvn -q org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy -Dartifact=io.opentelemetry.javaagent:opentelemetry-javaagent:2.15.0 -DoutputDirectory=/agent -Dmdep.stripVersion=true
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY telemetry/pom.xml telemetry/pom.xml
COPY telemetry/src telemetry/src
COPY app/pom.xml app/pom.xml
COPY device-sim/pom.xml device-sim/pom.xml
COPY payment-sim/pom.xml payment-sim/pom.xml
COPY app/src app/src
RUN mvn -q -pl app -am -DskipTests package

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=otel /agent/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar
ENV JAVA_TOOL_OPTIONS="-javaagent:/app/opentelemetry-javaagent.jar"
COPY --from=build /build/app/target/app-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
