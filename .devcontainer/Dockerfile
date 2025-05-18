FROM node:16

# Install basic development tools
RUN apt-get update && apt-get install -y \
    git \
    procps \
    nano \
    wget \
    curl \
    postgresql-client \
    jq \
    vim \
    openjdk-11-jre-headless \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Install useful global npm packages
RUN npm install -g nodemon typescript ts-node jest \
    @nestjs/cli \
    prisma \
    eslint \
    prettier

# Set up non-root user
ARG USERNAME=node
ARG USER_UID=1000
ARG USER_GID=$USER_UID

# Create the workspace directory
RUN mkdir -p /workspace
WORKDIR /workspace

# Change ownership to non-root user
RUN chown -R $USERNAME:$USERNAME /workspace

# Set environment variables
ENV NODE_ENV=development

# Expose common ports
EXPOSE 3000 9229
