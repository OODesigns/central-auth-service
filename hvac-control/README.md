# HVAC Control

Heating, ventilation, and air conditioning control service for the Home Control System.

## Features (Planned)

- Temperature monitoring per zone
- Thermostat setpoint control
- Scheduling (daily/weekly programs)
- Mode management (heating, cooling, auto, off)
- Energy usage tracking and optimization

## Communication

- **Publishes:** `home/hvac/{zone}/temperature` — current temperature readings
- **Publishes:** `home/hvac/{zone}/mode` — current mode changes
- **Subscribes:** `home/hvac/{zone}/setpoint` — desired temperature commands

## Status

🚧 **Planned** — Not yet implemented.
