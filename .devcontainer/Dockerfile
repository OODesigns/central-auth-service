# -------------------------
# 1) Build stage
# -------------------------
FROM openjdk:21-jdk-slim AS builder

# Install Git, bash (for any code-gen or entrypoint scripts)
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
      bash git openssh-client && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /workspace

# Copy just the Gradle wrapper & build files to cache dependencies
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle

RUN chmod +x gradlew && ./gradlew --no-daemon dependencies

# Copy source and build
COPY src ./src
RUN ./gradlew --no-daemon clean build -x test

# -------------------------
# 2) Runtime stage
# -------------------------
FROM openjdk:21-jdk-slim

WORKDIR /app

# Copy the fat-jar (or however your build produces it)
COPY --from=builder /workspace/build/libs/*.jar app.jar

# Expose your gRPC port
EXPOSE 6565

ENTRYPOINT ["java","-jar","/app/app.jar"]
