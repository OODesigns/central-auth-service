FROM eclipse-temurin:25-jdk AS build

WORKDIR /workspace

COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle ./gradle
RUN chmod +x gradlew

COPY src ./src
RUN ./gradlew --no-daemon installDist

FROM eclipse-temurin:25-jre-alpine

RUN addgroup -S -g 10001 cas \
	&& adduser -S -D -u 10001 -G cas -h /opt/cas cas
WORKDIR /opt/cas

COPY --from=build --chown=cas:cas /workspace/build/install/central-auth-service/ ./

USER cas
EXPOSE 50051

ENTRYPOINT ["/opt/cas/bin/central-auth-service"]
