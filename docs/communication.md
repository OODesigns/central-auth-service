# Inter-Service Communication

## Communication Patterns

The Home Control System uses two primary communication patterns:

### 1. Synchronous — REST/HTTPS

Used for request/response interactions where a client needs an immediate answer.

**Examples:**
- Dashboard → Auth Service: Login, token refresh
- Dashboard → Gateway → Any Service: User-initiated commands
- Service → Auth Service: Token validation

**Conventions:**
- All REST APIs follow OpenAPI 3.0 specification
- JSON request/response bodies
- JWT ****** in `Authorization` header
- Standard HTTP status codes

### 2. Asynchronous — MQTT

Used for event-driven communication where services need to react to state changes.

**Examples:**
- Lighting Control publishes: `home/lighting/{room}/state` → Dashboard subscribes for real-time updates
- Security System publishes: `home/security/motion/{zone}` → Lighting subscribes to auto-illuminate
- HVAC publishes: `home/hvac/temperature/{zone}` → Dashboard subscribes for live readings

## MQTT Topic Structure

```
home/{service}/{entity_type}/{entity_id}/{action}
```

### Topic Hierarchy

```
home/
├── lighting/
│   ├── {room}/state          # Current light state (on/off/brightness)
│   ├── {room}/command        # Command to change light state
│   └── scenes/activated      # Scene activation events
├── hvac/
│   ├── {zone}/temperature    # Current temperature reading
│   ├── {zone}/setpoint       # Desired temperature
│   └── {zone}/mode           # Heating/cooling/auto/off
├── security/
│   ├── motion/{zone}         # Motion detected events
│   ├── door/{id}/state       # Door open/closed
│   ├── alarm/state           # Alarm armed/disarmed/triggered
│   └── camera/{id}/snapshot  # Camera snapshot available
├── media/
│   ├── {room}/playing        # Currently playing media
│   ├── {room}/volume         # Volume level
│   └── {room}/command        # Play/pause/skip commands
└── system/
    ├── {service}/health      # Service health status
    └── {service}/config      # Configuration change events
```

### QoS Levels

| Message Type | QoS | Rationale |
|-------------|-----|-----------|
| Sensor readings | 0 | Frequent updates, missing one is acceptable |
| State changes | 1 | At-least-once delivery, UI should reflect changes |
| Commands | 2 | Exactly-once, prevent duplicate actions |
| Alarm events | 2 | Critical, must not be lost or duplicated |

## Service Discovery

Services register with the gateway on startup. The gateway maintains a routing table:

| Route | Service | Port |
|-------|---------|------|
| `/api/auth/**` | auth-service | 8443 |
| `/api/lighting/**` | lighting-control | 8081 |
| `/api/hvac/**` | hvac-control | 8082 |
| `/api/security/**` | security-system | 8083 |
| `/api/media/**` | media-control | 8084 |

## Error Handling

### REST Errors
All services return errors in a standard format:
```json
{
  "error": "INVALID_CREDENTIALS",
  "message": "Username or password is incorrect",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

### MQTT Error Events
Services publish errors to their error topic:
```
home/{service}/error
```

Payload includes error code, message, and original event that caused the failure.

## Authentication Flow

```
Client → Gateway → Auth Service: POST /api/auth/login
                                   ← 200 { access_token, refresh_token }

Client → Gateway → Any Service:  GET /api/lighting/rooms
         Header: Authorization: ******
         Gateway validates token, forwards request
                                   ← 200 { rooms: [...] }
```
