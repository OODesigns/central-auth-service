# Lighting Control

Smart lighting automation and scheduling service for the Home Control System.

## Features (Planned)

- Light on/off/dim control per room and zone
- Scene management (e.g., "Movie Mode", "Morning Routine")
- Scheduling with timers, sunrise/sunset triggers
- Integration with smart bulbs (Zigbee, Z-Wave, WiFi)
- Real-time state publishing via MQTT

## Communication

- **Publishes:** `home/lighting/{room}/state` — current light state changes
- **Subscribes:** `home/lighting/{room}/command` — incoming commands
- **Subscribes:** `home/security/motion/{zone}` — auto-illuminate on motion

## Status

🚧 **Planned** — Not yet implemented.
