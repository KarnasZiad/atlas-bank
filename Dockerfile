# syntax=docker/dockerfile:1
# Runtime image of one Atlas Bank service, built from the jar produced by `./mvnw package`
# (build once in CI, promote the same artifact everywhere). The build context is the module itself,
# and Dockerfile.dockerignore keeps only its jar: nothing else is sent to the builder.
#
#   docker build -f Dockerfile -t atlas/account-service services/account-service
#
# The base image is pinned by digest (reproducible builds); Dependabot proposes the updates.

FROM eclipse-temurin:25-jre-alpine@sha256:3c0a9084927a221ccd1d007fcaf614465672c0af37aaa834c5184483afe56d61 AS layers
WORKDIR /layers
COPY target/*.jar app.jar
# Spring Boot layers: dependencies change rarely, application classes often -> better image caching.
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:25-jre-alpine@sha256:3c0a9084927a221ccd1d007fcaf614465672c0af37aaa834c5184483afe56d61
RUN addgroup -S atlas && adduser -S -G atlas -H -s /sbin/nologin atlas
WORKDIR /app
COPY --from=layers /layers/extracted/dependencies/ ./
COPY --from=layers /layers/extracted/spring-boot-loader/ ./
COPY --from=layers /layers/extracted/snapshot-dependencies/ ./
COPY --from=layers /layers/extracted/application/ ./
USER atlas
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
