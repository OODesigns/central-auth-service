# System Architecture

## Overview

The Home Control System is a distributed microservices architecture where each service manages a specific domain of home automation. Services communicate asynchronously via MQTT for real-time events and synchronously via REST for request/response interactions.

## High-Level Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                         Dashboard (UI)                           │
│                    Web / Mobile Application                      │
└──────────────────────────────┬──────────────────────────────────┘
                               │ HTTPS
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                        API Gateway                               │
│              Rate Limiting · Routing · TLS Termination           │
└───┬──────────┬──────────┬──────────┬──────────┬────────────────┘
    │          │          │          │          │
    ▼          ▼          ▼          ▼          ▼
┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐
│  Auth  │ │Lighting│ │  HVAC  │ │Security│ │ Media  │
│Service │ │Control │ │Control │ │ System │ │Control │
└────┬───┘ └───┬────┘ └───┬────┘ └───┬────┘ └───┬────┘
     │         │          │          │          │
     └─────────┴──────────┴──────────┴──────────┘
                           │
                    MQTT Message Broker
                    (Event Bus)
```

## Service Responsibilities

### Auth Service
- User authentication (login, token refresh)
- JWT token issuance and validation
- Role-based access control
- User management

### Lighting Control
- Light on/off/dim control
- Scene management (e.g., "Movie Mode", "Morning")
- Scheduling (timers, sunrise/sunset triggers)
- Integration with smart bulbs (Zigbee, Z-Wave, WiFi)

### HVAC Control
- Temperature monitoring and control
- Thermostat scheduling
- Zone management
- Energy usage tracking

### Security System
- Camera feed management
- Motion detection alerts
- Door/window sensor monitoring
- Alarm arm/disarm

### Media Control
- Multi-room audio control
- TV/projector integration
- Streaming service management
- Volume and source selection

### Dashboard
- Unified web/mobile interface
- Real-time status of all systems
- Control panels for each service
- Notification center

### Gateway
- API routing and load balancing
- Rate limiting per client
- TLS termination
- Request/response logging

### Shared Libraries
- Common DTOs for inter-service communication
- Protocol definitions (MQTT topics, REST contracts)
- Utility functions shared across services

## Technology Stack

| Component | Technology |
|-----------|-----------|
| Auth Service | Java 22, Spring Boot, PostgreSQL |
| Lighting Control | TBD |
| HVAC Control | TBD |
| Security System | TBD |
| Media Control | TBD |
| Dashboard | TBD |
| Gateway | TBD |
| Message Broker | MQTT (Mosquitto) |
| Containerization | Docker, Docker Compose |

## Security Model

All inter-service communication is authenticated:
1. External requests are authenticated at the Gateway via JWT tokens issued by Auth Service
2. Internal service-to-service calls use mutual TLS (mTLS)
3. MQTT messages are published/subscribed on authenticated, ACL-protected topics
