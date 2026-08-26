# Self-Hosted Observability

This directory contains the version-controlled Prometheus and Grafana configuration for local or single-host deployments. Prometheus and Grafana run as separate containers from the authentication application.

## Start

Create the deployment `.env` from the existing application secret requirements and add a strong `GRAFANA_ADMIN_PASSWORD`. Then run:

```bash
docker compose --profile observability up -d
```

The services bind to loopback by default:

- Prometheus: `http://127.0.0.1:9090`
- Grafana: `http://127.0.0.1:3000`

Do not publish either port publicly without an authenticated TLS-protected reverse proxy or private network policy.

## Move to new hardware

1. Copy the repository and the deployment `.env` file through the approved secret-transfer process.
2. Back up the `postgres-data`, `prometheus-data`, and `grafana-data` volumes.
3. Restore those volumes on the new host, or start with empty volumes when historical data is not required.
4. Run `docker compose config` and review the rendered configuration.
5. Start the stack with `docker compose --profile observability up -d`.
6. Verify the application, Prometheus targets, Grafana datasource, and health endpoint.

The YAML, alert rules, dashboards, image versions, retention settings, and provisioning files are stored in this repository. Secrets and persistent data are intentionally not committed.

## Metrics

The application exposes OpenTelemetry Prometheus metrics at `app:9464/metrics`. Prometheus scrapes this endpoint over the internal Compose network. The metrics endpoint is not published directly to the host.

The application also emits bounded JSON security events to its runtime log stream. Configure the host or platform log collector to retain and restrict access to these logs, and do not parse request bodies into log fields. Event correlation IDs are safe, generated request identifiers; they are not user, session, or credential identifiers.

## Internal production gate

Use `ops/internal-security-gate.sh` only from the approved production runner. It requires an immutable `RELEASE_IMAGE_DIGEST` and a `DEPLOYMENT_APPROVAL_ID`, then runs the security gate, deployment checks, and digest verification. GitHub-hosted workflows are not the authoritative production enforcement path.

## Backup

Back up the named volumes using the host's approved backup process. Test restoration regularly. Prometheus data is operational history; PostgreSQL remains the authoritative authentication datastore.

Schedule `scripts/cleanup-rate-limits.sh` with the database host variables and a separate least-privileged maintenance credential. The application role is deliberately denied access to destructive audit cleanup. Keep the audit retention period and batch size in deployment configuration, not in Git secrets.