# Home Control System - Master Design Document

> **A whole-house HVAC automation system** built with Raspberry Pi 4, Modbus RTU relay/analog modules, ESP32-S3 wall controller, and AWS IoT for remote access.

---

## System Summary

| Component | Technology | Purpose |
|-----------|-----------|---------|
| Controller | Raspberry Pi 4 (Java 22) | Central brain, Modbus master, MQTT broker |
| I/O | Waveshare Modbus RTU modules | 8 AI + 8 AO + 30 relays over RS485 |
| UI | ESP32-S3 + 7" TFT (LVGL) | Wall-mounted touch interface |
| Comms | MQTT (local Mosquitto + AWS IoT) | Real-time messaging |
| Cloud | AWS (IoT Core, Cognito, Lambda, DynamoDB) | Remote access + auth |
| Auth | Local JWT (Ed25519) + AWS Cognito | Offline-capable security |

---

## Architecture Diagrams

All diagrams are in PlantUML format in the `diagrams/` directory. Render with any PlantUML tool or VS Code extension.

### 1. System Overview
**File:** [`diagrams/system-overview.puml`](diagrams/system-overview.puml)

Complete component view showing:
- Raspberry Pi 4 with all software components
- RS485 bus with 4 Modbus devices
- All 8 sensors with labels and signal types
- Wall controller (ESP32) with WiFi/MQTT
- AWS cloud services
- Local Mosquitto MQTT broker
- Connection types and protocols

---

### 2. Modbus RS485 Stack
**File:** [`diagrams/modbus-stack.puml`](diagrams/modbus-stack.puml)

RS485 daisy-chain topology showing:
- **Addr 0x01** — 16-channel relay (zone dampers + equipment)
- **Addr 0x02** — 8-channel relay (aux dampers + indicators)
- **Addr 0x03** — 8-channel relay (purge sequence + safety interlocks)
- **Addr 0x04** — 8AI/8AO analog module (sensors + actuators)
- Bus configuration (9600 baud, 8N1, 120Ω termination)

---

### 3. I/O Channel Assignment
**File:** [`diagrams/io-assignment.puml`](diagrams/io-assignment.puml)

Every channel mapped with full specifications:
- **8 Analog Inputs:** 4× temperature (NTC 10k), 2× humidity (4-20mA), CO2 (0-10V), filter ΔP (4-20mA)
- **8 Analog Outputs:** 4× fan speed (0-10V VFD), 2× valve position, 2× damper position
- **30 Relay Outputs:** 8 zone dampers, equipment contactors, purge sequence, safety interlocks
- Signal types, ranges, calibration notes, alarm thresholds, fail-safe positions

---

### 4. Authentication Flow
**File:** [`diagrams/auth-flow.puml`](diagrams/auth-flow.puml)

Dual authentication architecture:
- **Local LAN:** PIN → Pi JWT (Ed25519, 15min TTL) — works offline
- **Remote AWS:** Cognito SRP → API Gateway → Lambda → IoT Core → Pi
- **Token Bridge:** Maps Cognito groups to local zone permissions
- Security measures documented (TLS, rate limiting, MFA)

---

### 5. Purge Mode State Machine
**File:** [`diagrams/purge-mode.puml`](diagrams/purge-mode.puml)

Complete state machine with 6 stages:
1. **Pre-Purge Safety Check** — Validate interlocks (fire, smoke, temp, wind)
2. **Stage 1: Dampers Opening** — Open supply/exhaust dampers, 60s travel time
3. **Stage 2: Fan Ramp Up** — All fans to 100%, 5%/sec ramp rate
4. **Stage 3: Purge Active** — 3 air changes (~68 min), CO2 monitoring
5. **Stage 4: Fan Ramp Down** — Return to normal speed
6. **Stage 5: Dampers Closing** — Close purge dampers, verify position

Plus: abort sequences, safety priorities, manual reset requirements

---

### 6. Wall Controller Architecture
**File:** [`diagrams/wall-controller.puml`](diagrams/wall-controller.puml)

ESP32-S3 embedded UI system:
- **Hardware:** 7" IPS 800×480, capacitive touch, LED ring, PIR wake, ambient light
- **UI Layer:** LVGL v9 with 6 screens (Home, Zone, Climate, Purge, Settings, Login)
- **Comms:** WiFi, MQTT, mDNS discovery, OTA updates
- **State:** Zone cache, system state, NVS persistent storage
- **Power:** USB-C 5V/2A, deep sleep <10mA, active ~350mA

---

### 7. Delivery Roadmap
**File:** [`diagrams/roadmap.puml`](diagrams/roadmap.puml)

12-week phased delivery:
- **Phase 1 (Weeks 1-3):** Hardware assembly + basic Modbus I/O + local MQTT
- **Phase 2 (Weeks 4-6):** PID control + purge mode + economizer
- **Phase 3 (Weeks 7-9):** Wall controller UI + local auth + polish
- **Phase 4 (Weeks 10-12):** AWS cloud integration + mobile app + testing

---

## Key Specifications

### Modbus Bus
| Parameter | Value |
|-----------|-------|
| Protocol | Modbus RTU |
| Baud rate | 9600 |
| Data format | 8N1 |
| Bus topology | Daisy chain |
| Termination | 120Ω at each end |
| Max devices | 4 (of 247 max) |
| Poll cycle | 250ms (all devices) |
| Cable | Shielded twisted pair |

### Sensors
| ID | Type | Signal | Range | Purpose |
|----|------|--------|-------|---------|
| T1 | NTC 10k (B=3950) | Resistance | 0-50°C | Supply air temperature |
| T2 | NTC 10k (B=3950) | Resistance | 0-50°C | Return air temperature |
| T3 | NTC 10k (B=3950) | Resistance | -20-60°C | Outside air temperature |
| T4 | NTC 10k (B=3950) | Resistance | 0-50°C | Room temperature (average) |
| H1 | Capacitive | 4-20mA | 0-100% RH | Supply air humidity |
| H2 | Capacitive | 4-20mA | 0-100% RH | Room humidity |
| CO2 | NDIR | 0-10V | 0-2000 ppm | Room CO2 level |
| DP | Differential | 4-20mA | 0-500 Pa | Filter pressure drop |

### Control Outputs
| Output | Signal | Actuator | Fail-safe |
|--------|--------|----------|-----------|
| Supply fan | 0-10V (VFD) | 3-phase motor | OFF |
| Return fan | 0-10V (VFD) | 3-phase motor | OFF |
| Hot water valve | 0-10V | Spring return, 90s stroke | CLOSED |
| Chilled water valve | 0-10V | Spring return, 90s stroke | CLOSED |
| Zone dampers 1-12 | Relay (24V) | Motor actuator, 60s stroke | CLOSED |
| Heater stage 1-2 | Relay (contactor) | Electric/gas | OFF |
| Cooling compressor | Relay (contactor) | Scroll compressor | OFF |

### Safety Interlocks
| Interlock | Type | Action on Trigger |
|-----------|------|-------------------|
| Fire | N/C relay (R3.6) | Immediate shutdown, all equipment OFF |
| Smoke | N/C relay (R3.7) | Immediate shutdown, fans OFF, dampers CLOSE |
| High temp | AI sensor (T1 > 45°C) | Disable heating, alarm |
| Low temp | AI sensor (T3 < -5°C) | Block fresh air damper |
| Filter blocked | AI sensor (DP > 250Pa) | Reduce fan speed, alarm |

---

## Software Architecture (Pi)

```
home-control-service/
├── src/main/java/com/oodesigns/homecontrol/
│   ├── domain/
│   │   ├── model/           # Zone, Sensor, Actuator, PurgeState
│   │   ├── value/           # Temperature, Humidity, CO2Level, Pressure
│   │   └── service/         # Ports.java (Modbus, MQTT, Storage interfaces)
│   ├── application/
│   │   ├── control/         # PID controller, zone scheduler
│   │   ├── purge/           # Purge state machine
│   │   └── handler/         # Command handlers (MQTT → action)
│   └── infrastructure/
│       ├── modbus/          # jSerialComm Modbus RTU master
│       ├── mqtt/            # Eclipse Paho MQTT client
│       ├── storage/         # SQLite state persistence
│       └── auth/            # JWT issuer + validator (Ed25519)
├── src/test/                # Unit + integration tests
└── build.gradle             # Java 22, jSerialComm, Paho, SQLite
```

---

## How to Render Diagrams

### VS Code (Recommended)
1. Install "PlantUML" extension (jebbs.plantuml)
2. Open any `.puml` file
3. `Alt+D` to preview

### Command Line
```bash
# Install PlantUML
sudo apt install plantuml

# Render all diagrams to PNG
cd docs/home-control/diagrams
for f in *.puml; do plantuml -tpng "$f"; done
```

### Online
Paste content into [plantuml.com](https://www.plantuml.com/plantuml/uml)

---

## Related Documentation

- [`/docs/README.md`](../README.md) — Central Auth Service documentation index
- [`/.github/copilot-instructions.md`](../../.github/copilot-instructions.md) — Architecture patterns

---

**Created:** July 2026  
**Author:** OODesigns  
**Status:** Design phase — Phase 1 hardware assembly
