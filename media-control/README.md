# Media Control

Audio/video system control service for the Home Control System.

## Features (Planned)

- Multi-room audio control
- TV/projector integration
- Streaming service management
- Volume and source selection
- Queue management

## Communication

- **Publishes:** `home/media/{room}/playing` — currently playing media
- **Publishes:** `home/media/{room}/volume` — volume level changes
- **Subscribes:** `home/media/{room}/command` — play/pause/skip/volume commands

## Status

🚧 **Planned** — Not yet implemented.
