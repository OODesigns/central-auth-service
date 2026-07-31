# Home Control System — Master Plan

## Overview

A whole-home environmental control system built around the MODBUS protocol, integrating:

- **MVHR** (Mechanical Ventilation with Heat Recovery) via Blauberg hardware
- **Evaporative Cooler** (Blauberg EVAP-COOLER, standalone)
- **Night Purge** ventilation using a dedicated high-power fan and duct actuators
- **Central Auth Service** for local and remote access control
- **AWS Cloud Bridge** for secure remote access without opening inbound router ports
- **Mobile / Web apps** (Android, iOS, browser)
- **Wall Controller** for local in-home control

---

## Core Pillars

| # | Pillar | Status |
|---|--------|--------|
| 1 | MVHR Control (Blauberg via MODBUS) | Planned |
| 2 | Evaporative Cooler Control (analog I/O) | Planned |
| 3 | Night Purge System (fan + actuators) | Planned |
| 4 | Central Auth Service | **Active** |
| 5 | Cloud Bridge (AWS IoT / API Gateway) | Planned |
| 6 | Mobile / Web Apps | Planned |
| 7 | Wall Controller (local UI) | Planned |

---

## Architecture Diagrams

All diagrams are authored in PlantUML and live alongside this document in `diagrams/`.

| Diagram | File | Description |
|---------|------|-------------|
| System Overview | [system-overview.puml](diagrams/system-overview.puml) | Top-level component view of every service, device, and app |
| MODBUS Stack | [modbus-stack.puml](diagrams/modbus-stack.puml) | How MODBUS TCP/RTU connects the control service to hardware modules |
| Auth & Remote Access Flow | [auth-flow.puml](diagrams/auth-flow.puml) | JWT-based auth for both local LAN and remote AWS paths |
| Night Purge State Machine | [purge-mode.puml](diagrams/purge-mode.puml) | State transitions between normal MVHR mode and night purge mode |
| Phased Delivery Roadmap | [roadmap.puml](diagrams/roadmap.puml) | Timeline showing which components go live in which phase |

---

## System Overview

![System Overview](diagrams/system-overview.puml)

The home network contains three main software components (Wall Controller, Home Control Service, Central Auth Service) talking to hardware over a MODBUS bus. Remotely, a persistent outbound connection to AWS IoT removes the need for any inbound firewall rule.

---

## MODBUS Hardware Layer

### Devices on the RS485 Bus

| MODBUS Address | Device | Purpose |
|----------------|--------|---------|
| 1 | 8-ch Analog Output Module (12-bit DA) | Drive 0–10 V signals to EVAP cooler speed input and MVHR setpoint |
| 2 | 8-ch Analog Input Module (12-bit AD) | Read sensor feedback (temperatures, humidity, pressure) |
| 3 | 30-ch Ethernet Relay Module | Switch purge fans on/off; open/close duct actuators |
| 4 | Blauberg MVHR | Proprietary MODBUS registers for speed, mode, alarms |

### Connectivity

- **RS485 ↔ Ethernet Serial Server** (TCP port 502) — bridges the RS485 bus to the home LAN so the Java service uses standard MODBUS TCP.
- **USB ↔ RS485 Adapter** — fallback direct connection from the control machine for development and diagnostics.

### MODBUS Library

Write a bespoke MODBUS library in **Java** first (consistent with the control service language). Wrap it behind a clean interface (port/adapter pattern matching central-auth-service style) so a C++ or Python implementation can be swapped in later without touching business logic.

---

## Evaporative Cooler Control

The Blauberg EVAP-COOLER PC board is damaged at the MVHR integration input and runs standalone only. Control is via:

1. **Analog Output Module channel** → 0–10 V signal → cooler speed/power input.
2. **Analog Input Module channel** → read cooler status feedback if available.
3. **Relay channel** → on/off power control.

No serial communication with the cooler's PC board is required.

---

## Night Purge System

During summer nights when outdoor temperature drops below indoor temperature, the system switches from MVHR to a direct air-flush mode:

1. Duct actuators (relay-driven) redirect airflow away from the MVHR heat exchanger.
2. A dedicated high-power supply fan forces hot indoor air out.
3. A second fan (planned) draws cool outdoor air in through a separate duct.

Trigger conditions (configurable):
- Outdoor temp < Indoor temp by a threshold (e.g., 2 °C)
- Scheduled time window (e.g., 23:00–06:00)
- Manual override via wall controller or app

---

## Auth & Remote Access

The Central Auth Service (this repository) issues JWTs used by every path:

### Local (LAN)
```
App → POST /auth → CAS → JWT → App → HCS (JWT) → CAS (verify) → execute
```

### Remote (AWS)
```
App → AWS Bridge → CAS (outbound tunnel) → JWT → App
App → AWS Bridge → HCS (via tunnel) → CAS (verify) → execute
```

The Home Control Service maintains a **persistent outbound connection** (MQTT over TLS or WebSocket) to AWS IoT Core. No inbound port is opened on the home router.

---

## Key Technology Decisions

| Topic | Decision | Rationale |
|-------|----------|-----------|
| MODBUS library language | Java | Matches team skill, runs on Linux, matches HCS language |
| Control service language | Java 22 | Consistent with central-auth-service; hexagonal architecture reuse |
| EVAP cooler interface | Analog Output Module 0–10 V | Cooler serial input is damaged; analog is simpler and reliable |
| MVHR interface | MODBUS RTU / TCP | Already proven in Python experiments |
| Cloud connectivity | Outbound MQTT to AWS IoT Core | No inbound firewall rule; secure TLS tunnel |
| Auth tokens | JWT from central-auth-service | Single auth mechanism for local and remote |
| Duct switching | Motorised damper actuators via relay module | 24 V AC or 230 V actuators; relay module already on MODBUS bus |

---

## Phased Delivery Roadmap

### Phase 0 — Foundation (Now)
- [x] Central Auth Service (JWT, login, 2FA)

### Phase 1 — MODBUS Infrastructure
- [ ] Java MODBUS library (RTU + TCP)
- [ ] RS485 bus wiring and device addressing
- [ ] Integration tests with analog output/input modules and relay module

### Phase 2 — HVAC Control
- [ ] Home Control Service (Java, hexagonal architecture)
- [ ] EVAP cooler control via analog output
- [ ] MVHR control via MODBUS registers
- [ ] Sensor ingestion via analog input module

### Phase 3 — Advanced Features
- [ ] Night purge state machine and duct actuator control
- [ ] AWS IoT outbound bridge
- [ ] Mobile apps (Android, iOS)
- [ ] Web browser UI
- [ ] Wall controller (embedded local touch panel)

---

## Repository Structure (Planned)

| Repository | Language | Purpose |
|------------|----------|---------|
| `central-auth-service` | Java 22 | JWT auth for local and remote access |
| `home-control-service` | Java 22 | Orchestrates all HVAC hardware via MODBUS |
| `modbus-lib` | Java (then C++ optional) | Bespoke MODBUS RTU/TCP library |
| `docs` | Markdown + PlantUML | Architecture diagrams and decisions |
| `modbus` (existing) | Python | Prototype experiments (reference only) |

---

## Notes

- This document was initially saved in `central-auth-service/docs/home-control/` because that was the only repository accessible at the time. It should be migrated to `OODesigns/docs` once that repository is accessible.
- PlantUML diagrams can be rendered to PNG and embedded in the docs site using the PlantUML CLI or a CI pipeline step (e.g., `plantuml -tpng diagrams/*.puml`).
