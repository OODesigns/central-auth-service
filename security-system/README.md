# Security System

Camera, alarm, and sensor management service for the Home Control System.

## Features (Planned)

- Camera feed management and snapshot capture
- Motion detection with zone-based alerts
- Door/window sensor monitoring
- Alarm arm/disarm/trigger control
- Event history and audit log

## Communication

- **Publishes:** `home/security/motion/{zone}` — motion detected events
- **Publishes:** `home/security/door/{id}/state` — door open/closed
- **Publishes:** `home/security/alarm/state` — alarm state changes
- **Subscribes:** `home/security/alarm/command` — arm/disarm commands

## Status

🚧 **Planned** — Not yet implemented.
