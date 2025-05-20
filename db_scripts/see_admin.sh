#!/bin/bash
set -e

echo "👤 Seeding admin user..."

psql "host=db dbname=${POSTGRES_DB} user=${POSTGRES_USER} password=${POSTGRES_PASSWORD}" <<EOF
INSERT INTO users (username, password_hash, created_at, updated_at)
VALUES ('admin', '${ADMIN_PASSWORD_HASH}', NOW(), NOW())
ON CONFLICT (username) DO NOTHING;
EOF

echo "✅ Admin user seeded (or already exists)."
