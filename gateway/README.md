# API Gateway

Central API gateway and message broker configuration for the Home Control System.

## Features (Planned)

- Request routing to backend services
- Rate limiting per client/IP
- TLS termination
- JWT token validation (delegated to auth-service)
- Request/response logging
- Health check aggregation

## Routes

| Route | Service | Port |
|-------|---------|------|
| `/api/auth/**` | auth-service | 8443 |
| `/api/lighting/**` | lighting-control | 8081 |
| `/api/hvac/**` | hvac-control | 8082 |
| `/api/security/**` | security-system | 8083 |
| `/api/media/**` | media-control | 8084 |

## Status

🚧 **Planned** — Not yet implemented.
