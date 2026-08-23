FROM eclipse-temurin:25-jdk AS build

WORKDIR /workspace

COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle ./gradle
RUN chmod +x gradlew

COPY src ./src
RUN ./gradlew --no-daemon installDist

FROM eclipse-temurin:25-jre

RUN useradd --system --uid 10001 --user-group --create-home --home-dir /opt/cas cas
WORKDIR /opt/cas

COPY --from=build --chown=cas:cas /workspace/build/install/central-auth-service/ ./

USER cas
EXPOSE 50051

ENTRYPOINT ["/opt/cas/bin/central-auth-service"]
