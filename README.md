# Home Control System

A monorepo containing all projects related to home automation and control. Each directory is an independent, deployable project that together forms a complete smart home ecosystem.

## Projects

| Project | Description | Status |
|---------|-------------|--------|
| [auth-service](./auth-service/) | Central authentication & authorization service | Active |
| [lighting-control](./lighting-control/) | Lighting automation and scheduling | Planned |
| [hvac-control](./hvac-control/) | Heating, ventilation & air conditioning control | Planned |
| [security-system](./security-system/) | Cameras, alarms, and sensors | Planned |
| [media-control](./media-control/) | Audio/video system control | Planned |
| [dashboard](./dashboard/) | Web/mobile UI aggregating all systems | Planned |
| [gateway](./gateway/) | API gateway & message broker configuration | Planned |
| [shared-libs](./shared-libs/) | Shared libraries (common DTOs, protocols) | Planned |

## Architecture

See [docs/architecture.md](./docs/architecture.md) for the full system architecture and [docs/communication.md](./docs/communication.md) for inter-service communication patterns.

## Getting Started

### Prerequisites
- Docker & Docker Compose
- Java 22+ (for auth-service)

### Running the Full System

```bash
docker-compose up -d
```

This starts all services and their dependencies. See individual project READMEs for project-specific setup.

### Running a Single Service

Each project can be run independently. Navigate to the project directory and follow its README:

```bash
cd auth-service
./gradlew bootRun
```

## Design Principles

- **Independent Deployability** — Each project can be built, tested, and deployed on its own
- **Shared Communication Contracts** — Projects communicate via well-defined protocols (MQTT, REST)
- **Centralized Auth** — All services authenticate through the auth-service
- **Observable** — Each service exposes health checks and metrics
- **AI-Friendly** — All projects in one repo so AI tools can understand the full system context
