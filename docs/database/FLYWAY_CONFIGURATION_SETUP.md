# Flyway Configuration - Environment Variable Passing

**Date:** February 9, 2026  
**Status:** ACTION REQUIRED - Configure Flyway to pass FLYWAY_ENV

## Current Setup

You have:
- `.env` file with `FLYWAY_ENV=development` ✅
- V0_9_9 migration using Flyway placeholder `${flyway:env}` ✅
- **Missing:** Flyway Gradle plugin configuration ❌

## What Needs to be Done

Flyway needs to be configured in `build.gradle` to:
1. Read the `FLYWAY_ENV` environment variable
2. Pass it to SQL migrations as a placeholder `${flyway:env}`

## Solution: Add Flyway Plugin to build.gradle

Add to your `build.gradle`:

```gradle
plugins {
    id 'java'
    id 'jacoco'
    id 'org.flywaydb.flyway' version '10.10.0'  // Add this
}

// ... existing code ...

dependencies {
    // ... existing dependencies ...
    
    // Flyway
    implementation 'org.flywaydb:flyway-core:10.10.0'
    implementation 'org.flywaydb:flyway-database-postgresql:10.10.0'
    runtimeOnly 'org.postgresql:postgresql:42.7.8'  // Already have this
}

// Configure Flyway plugin
flyway {
    // Database connection
    url = System.getenv('JDBC_DATABASE_URL') ?: "jdbc:postgresql://${System.getenv('DB_HOST') ?: 'localhost'}:${System.getenv('DB_PORT') ?: 5432}/${System.getenv('APP_DB') ?: 'auth_db'}"
    user = System.getenv('APP_USER') ?: 'app_role'
    password = System.getenv('APP_PASSWORD') ?: 'changeme'
    
    // Locations
    locations = ['filesystem:src/main/resources/db/migration']
    
    // Placeholders - CRITICAL: Pass FLYWAY_ENV to migrations
    placeholders = [
        'flyway.env': System.getenv('FLYWAY_ENV') ?: 'production'  // Default to production (safest)
    ]
    
    // Validation
    validateOnMigrate = true
    baselineOnMigrate = true
    
    // Logging
    info = true
    debug = false
}
```

## Usage

### Development (fresh rebuild):
```bash
export FLYWAY_ENV=development
./gradlew flywayMigrate
# Flyway passes 'development' → Migration uses CASCADE
```

### Production (first time):
```bash
# FLYWAY_ENV not set (or = production)
./gradlew flywayMigrate
# Flyway passes 'production' (default) → Migration uses RESTRICT
```

### Production (explicit):
```bash
export FLYWAY_ENV=production
./gradlew flywayMigrate
# Flyway passes 'production' → Migration uses RESTRICT
```

## How the Placeholder Works

1. You set: `export FLYWAY_ENV=development`
2. Gradle reads: `System.getenv('FLYWAY_ENV')`
3. Flyway injects: `placeholders['flyway.env'] = 'development'`
4. In SQL: `${flyway:env}` becomes `'development'`
5. PL/pgSQL: `v_env := COALESCE('${flyway:env}', 'production');` → `v_env := COALESCE('development', 'production');`

## Security Guarantee

- **Default (no env var):** `'production'` (RESTRICT - safest)
- **Explicit dev:** `'development'` (CASCADE - intentional)
- **Explicit prod:** `'production'` (RESTRICT - confirmed)
- **Unknown value:** Treated as `'production'` (fail-safe)

## Database Connection String

If your database isn't `localhost:5432`, update the Flyway config:

**From `.env`:**
```
DB_HOST=192.168.100.3
DB_PORT=5432
APP_DB=auth_db
APP_USER=app_role
APP_PASSWORD=H1gwXfu5OBa!YL1
```

**In `build.gradle` (already configured above):**
```gradle
url = "jdbc:postgresql://${System.getenv('DB_HOST')}:${System.getenv('DB_PORT')}/${System.getenv('APP_DB')}"
```

## Alternative: Using flyway.conf

If you prefer a config file over Gradle:

Create `flyway.conf` in project root:
```properties
flyway.url=jdbc:postgresql://192.168.100.3:5432/auth_db
flyway.user=app_role
flyway.password=H1gwXfu5OBa!YL1
flyway.locations=filesystem:src/main/resources/db/migration
flyway.placeholders.flyway.env=development
flyway.validateOnMigrate=true
flyway.baselineOnMigrate=true
```

Then in `build.gradle`:
```gradle
flyway {
    // Reads from flyway.conf automatically
}
```

## Verify Configuration

After configuring Flyway:

```bash
# See what Flyway will do
./gradlew flywayInfo

# Run migration
./gradlew flywayMigrate

# Check history
psql -U app_role -d auth_db -c "SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"
```

## Expected Output

Migration runs and logs:
```
[INFO] Executing SQL migration: cleanup_db.sql
[INFO] Flyway cleanup mode: PRODUCTION (RESTRICT enforced - safest default)
[INFO] FLYWAY_ENV = production
```

Or in development:
```
[INFO] Executing SQL migration: cleanup_db.sql
[INFO] Flyway cleanup mode: DEVELOPMENT (CASCADE enabled)
[INFO] FLYWAY_ENV = development
```

## Summary

| Item | Required | Status |
|------|----------|--------|
| `.env` with `FLYWAY_ENV` | ✅ | Done |
| V0_9_9 with placeholder | ✅ | Done |
| `build.gradle` Flyway plugin | ❌ | **ACTION REQUIRED** |
| `flyway.conf` OR gradle config | ❌ | **ACTION REQUIRED** |

Next: Add Flyway plugin to `build.gradle` and run migrations!

