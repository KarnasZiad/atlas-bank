# syntax=docker/dockerfile:1
# Runtime image of one Atlas Bank service, built from the jar produced by `./mvnw package`
# (build once in CI, promote the same artifact everywhere).
#
#   docker build --build-arg MODULE=services/account-service -t atlas/account-service .

FROM eclipse-temurin:21-jre-alpine AS layers
ARG MODULE
WORKDIR /layers
COPY ${MODULE}/target/*.jar app.jar
# Spring Boot layers: dependencies change rarely, application classes often -> better image caching.
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S atlas && adduser -S -G atlas -H -s /sbin/nologin atlas
WORKDIR /app
COPY --from=layers /layers/extracted/dependencies/ ./
COPY --from=layers /layers/extracted/spring-boot-loader/ ./
COPY --from=layers /layers/extracted/snapshot-dependencies/ ./
COPY --from=layers /layers/extracted/application/ ./
USER atlas
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
