# Local Trial Stack

## Purpose

Use this guide to run the complete Central Auth Service on one local machine. It is for learning, manual gRPC testing, and observing real authentication metrics.

## What Starts

```text
grpcui browser -> gRPC service -> PostgreSQL
                    |
                    -> Prometheus -> Grafana
```

`grpcui` is the Swagger-like browser for gRPC. It loads this project's protobuf contract, shows request fields, lets you send trial requests, and shows the gRPC response and status.

| Service | Local address | Why it is running |
| --- | --- | --- |
| grpcui | `http://127.0.0.1:8080` | Browser UI for sending gRPC requests. |
| Auth service | `127.0.0.1:50051` | The real Central Auth Service. |
| Prometheus | `http://127.0.0.1:9090` | Stores and queries service metrics. |
| Grafana | `http://127.0.0.1:3000` | Shows the prebuilt dashboard. |
| PostgreSQL | `127.0.0.1:55432` | Local trial database. It uses `55432` to avoid a normal local PostgreSQL service on `5432`. |

All browser addresses are bound to `127.0.0.1`. Other machines cannot open them.

## Before You Start

You need Docker, Docker Buildx, and Docker Compose. Check them with:

```bash
docker info
docker buildx version
docker compose version
```

If `docker info` fails, start Docker:

```bash
sudo systemctl start docker
```

## Start It

```bash
cd ~/Documents/projects/central-auth-service
./scripts/start-trial-stack.sh
```

The first run builds the Java image, downloads the Grafana, Prometheus, PostgreSQL, Flyway, and grpcui images, creates the database, and applies Flyway migrations. It can take a few minutes.

The script creates two local files:

| File | What it contains | Important rule |
| --- | --- | --- |
| `.trial.env` | Random database, JWT, Grafana, and runtime credentials. | Never commit or share it. |
| `.trial-admin-password` | The password for the CAS `admin` account. | Never commit or share it. |

Both files are ignored by Git. The script uses a separate Docker Compose project named `central-auth-service-trial`, so it does not mix with another Compose stack in this folder.

### Trial port selection

The trial script writes these host bindings to `.trial.env` on first start:

| Service | Default host binding | Override |
| --- | --- | --- |
| PostgreSQL | `127.0.0.1:55432` | `TRIAL_POSTGRES_HOST_PORT` |
| gRPC service | `0.0.0.0:50051` | `GRPC_HOST_PORT` |
| grpcui | `127.0.0.1:8080` | `GRPCUI_HOST_PORT` |
| Prometheus | `127.0.0.1:9090` | `PROMETHEUS_HOST_PORT` |
| Grafana | `127.0.0.1:3000` | `GRAFANA_HOST_PORT` |

The script chooses `55432` instead of the normal PostgreSQL host port
`5432`, but it does not scan for an available port. If `55432` is occupied,
choose a free port explicitly before starting. If `.trial.env` already exists,
edit its `POSTGRES_HOST_PORT` value because the script preserves existing trial
settings.

The trial stack enables plaintext gRPC and reflection only locally. grpcui reads `auth.proto` directly, so its browser UI does not depend on reflection discovery.

After the containers start, the script runs a real process-level smoke test by
default. It connects to the running PostgreSQL and gRPC containers, creates 100
temporary trial users, logs in representative users, updates one username,
deletes another, verifies the deleted login fails, and checks Prometheus for
gRPC metrics. The fixture users are removed when the smoke test finishes, while
the Prometheus time series remain available for inspection.

To start the containers without running the workload:

```bash
TRIAL_RUN_SMOKE_TEST=false ./scripts/start-trial-stack.sh
```

To run it again against an already-running trial stack:

```bash
./gradlew smokeTest
```

The smoke test deliberately uses only existing API operations. The service does
not currently expose public user create, update, or delete RPCs, so database
fixture setup is used for users and the real gRPC API is used for authentication
and security actions.

## Log In

### Grafana

Open `http://127.0.0.1:3000`.

Username:

```text
admin
```

Get the Grafana password:

```bash
grep '^GRAFANA_ADMIN_PASSWORD=' .trial.env
```

### CAS administrator

The CAS username is:

```text
admin
```

Get its password:

```bash
cat .trial-admin-password
```

## Try a Request

1. Open `http://127.0.0.1:8080`.
2. Choose `cas.v1.AuthService` and then `Login`.
3. Enter username `admin` and the password from `.trial-admin-password`.
4. Send the request.
5. The response will show a success, MFA requirement, or canonical gRPC error.
6. Open Prometheus or Grafana to see request metrics. Prometheus scrapes the app every 15 seconds, so wait briefly after sending a request.

Useful browser checks:

| What to check | Where |
| --- | --- |
| Service is being scraped | Prometheus: `Status` then `Targets`. The `central-auth-service` target should be `UP`. |
| Raw request metrics | Prometheus query page. Search for `grpc_server_call_count_total`. |
| Authentication dashboard | Grafana: `Dashboards`, then Central Auth overview. |
| gRPC request UI | grpcui: choose a method, fill fields, and invoke. |

Do not copy generated passwords, JWTs, TOTP values, backup codes, or recovery tokens into issue trackers or chat messages.

## Check the Stack

Use this command:

```bash
docker compose --env-file .trial.env -p central-auth-service-trial \
    --profile trial --profile observability ps
```

Expected state:

- `db`: running and healthy
- `flyway`: exited with code `0`
- `app`: running
- `grpcui`: running
- `prometheus`: running
- `grafana`: running

## Stop It

```bash
./scripts/stop-trial-stack.sh
```

This keeps the trial database and credentials, so you can restart later. To remove all trial data and generated credentials:

```bash
REMOVE_TRIAL_DATA=true ./scripts/stop-trial-stack.sh
```

This removes the PostgreSQL, Prometheus, and Grafana volumes as well as `.trial.env` and `.trial-admin-password`. The next start creates a completely new trial environment.

## Common Problems

### Port already allocated

If port `5432` is occupied, the trial script uses `55432` by default. If `55432` is also occupied, choose another port:

```bash
TRIAL_POSTGRES_HOST_PORT=55433 ./scripts/start-trial-stack.sh
```

### grpcui page does not open

Check the stack first:

```bash
docker compose --env-file .trial.env -p central-auth-service-trial \
    --profile trial --profile observability ps
```

If `app` or `grpcui` is restarting, reset the entire local trial state:

```bash
REMOVE_TRIAL_DATA=true ./scripts/stop-trial-stack.sh
./scripts/start-trial-stack.sh
```

### Grafana rejects the password

Grafana saves its first password inside its persistent volume. Reset the trial state, then read the new value from `.trial.env`:

```bash
REMOVE_TRIAL_DATA=true ./scripts/stop-trial-stack.sh
./scripts/start-trial-stack.sh
grep '^GRAFANA_ADMIN_PASSWORD=' .trial.env
```

### Docker command fails

Confirm the Docker daemon is running:

```bash
sudo systemctl start docker
docker info
```

## Important Security Boundary

The trial stack is for a local machine only. Its browser ports bind to `127.0.0.1`, plaintext gRPC is allowed, and reflection is enabled for gRPC tooling. Do not use this trial configuration, its generated secrets, or its database data in production.