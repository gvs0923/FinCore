# syntax=docker/dockerfile:1.7
FROM gradle:9.7.1-jdk21 AS build
ARG MODULE
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/home/gradle/.gradle gradle :${MODULE}:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre AS layers
ARG MODULE
WORKDIR /layers
COPY --from=build /src/${MODULE}/build/libs/${MODULE}-0.0.1-SNAPSHOT.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --home-dir /app fincore \
    && mkdir -p /data/processor-reports \
    && chown 10001 /data/processor-reports
WORKDIR /app
COPY --from=layers /layers/extracted/dependencies/ ./
COPY --from=layers /layers/extracted/spring-boot-loader/ ./
COPY --from=layers /layers/extracted/snapshot-dependencies/ ./
COPY --from=layers /layers/extracted/application/ ./
USER 10001
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
