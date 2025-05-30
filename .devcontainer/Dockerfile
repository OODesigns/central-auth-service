##############################################
# Development container configuration
##############################################
FROM openjdk:21-jdk-slim

##############################################
# Install development tools & dependencies
##############################################
# Update package lists and install required development tools
# Breaking this into multiple RUN commands to improve build caching and resilience
# Each step will be cached separately, making rebuilds faster if one step fails

# Update package lists
RUN apt-get update

# Install Git for version control
RUN apt-get install -y --no-install-recommends git

# Install network utilities
RUN apt-get install -y --no-install-recommends curl wget

# Install additional tools
RUN apt-get install -y --no-install-recommends unzip

# Clean up to reduce image size
RUN rm -rf /var/lib/apt/lists/*

##############################################
# Install Flyway CLI for DB migrations
##############################################
ARG FLYWAY_VERSION=11.8.2
RUN curl -SL "https://repo1.maven.org/maven2/org/flywaydb/flyway-commandline/${FLYWAY_VERSION}/flyway-commandline-${FLYWAY_VERSION}-linux-x64.tar.gz" \
    | tar -xz && \
    mv flyway-${FLYWAY_VERSION} /opt/flyway

# Make the `flyway` command available on PATH
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

# Make the Gradle wrapper executable
RUN chmod +x gradlew

# Pre-download Gradle dependencies to speed up subsequent builds
# The --no-daemon flag prevents Gradle from starting a background process
RUN ./gradlew --no-daemon dependencies

# For a dev container, we'll create an empty src directory structure
# This ensures the directory exists without requiring source files during build
RUN mkdir -p src/main/java src/main/resources src/test/java src/test/resources

##############################################
# Expose ports for development
##############################################
# gRPC default port
EXPOSE 6565
# Common web application ports
EXPOSE 8080 8081

# Note: No ENTRYPOINT or CMD is specified for a dev container
# This allows the container to be used flexibly for various development tasks
