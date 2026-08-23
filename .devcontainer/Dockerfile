##############################################
# Development container configuration
##############################################
FROM eclipse-temurin:25-jdk

##############################################
# Install development tools & dependencies
##############################################
# Update package lists and install required development tools
# All apt-get commands merged into single RUN to reduce image layers
RUN apt-get update && \
    apt-get install -y --no-install-recommends git curl wget unzip && \
    rm -rf /var/lib/apt/lists/*

##############################################
# Install Flyway CLI for DB migrations
##############################################
ARG FLYWAY_VERSION=11.8.2
RUN curl -SL \
      "https://repo1.maven.org/maven2/org/flywaydb/flyway-commandline/${FLYWAY_VERSION}/flyway-commandline-${FLYWAY_VERSION}-linux-x64.tar.gz" \
    | tar -xz \
    && mv flyway-${FLYWAY_VERSION} /opt/flyway \
    && ln -s /opt/flyway/flyway /usr/local/bin/flyway

ENV PATH="/opt/flyway:${PATH}"

##############################################
# Set up Gradle for development
##############################################
# Set up workspace directory
WORKDIR /workspace

# Copy Gradle wrapper files and build configuration
# This provides the build system for the project
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle

# Make the Gradle wrapper executable and pre-download dependencies
# The --no-daemon flag prevents Gradle from starting a background process
RUN chmod +x gradlew && \
    ./gradlew --no-daemon dependencies && \
    mkdir -p src/main/java src/main/resources src/test/java src/test/resources

##############################################
# Expose ports for development
##############################################
# gRPC default port
EXPOSE 50051

# Note: No ENTRYPOINT or CMD is specified for a dev container
# This allows the container to be used flexibly for various development tasks
