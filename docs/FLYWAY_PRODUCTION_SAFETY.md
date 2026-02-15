# Flyway Production Safety - V0_9_9 Cleanup Migration

**Date:** February 9, 2026  
**Status:** ✅ PRODUCTION SAFE

## Overview

The `cleanup_db.sql` migration uses **RESTRICT by default** to prevent accidental data loss in production.

---

## Safety Mechanism

### Default Behavior (RESTRICT - Safe)

```sql
DROP SCHEMA IF EXISTS private_schema RESTRICT;
DROP SCHEMA IF EXISTS api_schema RESTRICT;
```

**What RESTRICT does:**
- ✅ Fails if schema contains ANY objects
- ✅ Forces intentional cleanup first
- ✅ Prevents "surprise" deletions
- ✅ Requires human review

**When this fails (expected in production):**
```
ERROR: cannot drop schema private_schema because other objects depend on it
```

This is **WORKING AS INTENDED**. The schema is not empty and needs intentional cleanup.

---

## Environment Modes

### Development (Default)

**No setup required.** Development assumes:
- Fresh rebuilds are expected
- Schemas and tables can be recreated
- CASCADE behavior (if manually changed) is acceptable

**To use CASCADE in development (if you really need to):**
1. Only do this locally or on development databases
2. Change RESTRICT → CASCADE in the file
3. **Never commit CASCADE to main branch**
4. Change it back to RESTRICT immediately after

### Production (Strict Mode)

**Required setup:**

Set environment variable in your CI/CD or deployment script:

```bash
# Before running Flyway migration
export FLYWAY_ENV=production
./gradlew flywayMigrate
```

Or in `gradle.properties`:

```properties
# gradle.properties (committed to repo)
flywayEnv=production
```

Or in `.env` file (for Docker):

```env
FLYWAY_ENV=production
```

**What this does:**
- Enables audit logging of environment
- Safety check at migration start
- Uses RESTRICT (fail-closed, not fail-open)
- DBA approval required before deployment

---

## Production Deployment Checklist

### Before Running Migration

- [ ] **Set FLYWAY_ENV=production**
  ```bash
  echo "FLYWAY_ENV=production" >> /etc/environment
  ```

- [ ] **Verify environment is set**
  ```bash
  psql -c "SELECT current_setting('app.environment', true) AS env;"
  # Should show: 'production' (or empty if not set - defaults to development)
  ```

- [ ] **Test migration on staging first**
  ```bash
  FLYWAY_ENV=production ./gradlew flywayMigrate -Dflyway.baselineOnMigrate=false
  ```

- [ ] **Review Flyway history**
  ```bash
  psql -c "SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"
  ```

- [ ] **Backup database before production migration**
  ```bash
  pg_dump auth_db > backup_$(date +%Y%m%d_%H%M%S).sql
  ```

### Expected Behavior

**First time (fresh install):**
- V0_9_9 runs successfully (schemas don't exist, nothing to drop)
- All subsequent migrations run
- No errors

**Re-running (database already exists):**
- V0_9_9 attempts to drop schemas with RESTRICT
- If schemas contain objects: **ERROR (expected!)**
  - Do NOT use CASCADE
  - Contact DBA to review what's in the schema
  - Clean up intentionally or restore from backup
- If schemas are empty: Success

---

## Handling RESTRICT Failures

### If you see: "cannot drop schema because other objects depend on it"

**This is CORRECT behavior.** The migration is protecting your database.

**Next steps:**

1. **Do NOT modify the migration to use CASCADE**
2. **Review what's in the schema:**
   ```sql
   SELECT tablename FROM pg_tables WHERE schemaname = 'private_schema';
   SELECT routinename FROM pg_proc WHERE routines.pronamespace = (SELECT oid FROM pg_namespace WHERE nspname = 'private_schema');
   ```

3. **Choose path A or B:**

   **Path A: This is expected (first production deployment)**
   - Drop objects intentionally:
     ```sql
     DROP TABLE IF EXISTS private_schema.users CASCADE;
     DROP TABLE IF EXISTS private_schema.roles CASCADE;
     -- ... etc for all tables
     DROP SCHEMA private_schema RESTRICT;
     ```
   - Then re-run migration

   **Path B: This is unexpected (corrupted schema)**
   - Restore from backup and investigate
   - Do NOT force CASCADE
   - Contact DBA

---

## CI/CD Integration

### GitHub Actions Example

```yaml
# .github/workflows/flyway-prod-migration.yml
name: Production Flyway Migration

on:
  workflow_dispatch:  # Manual trigger only

env:
  FLYWAY_ENV: production  # Enforced in production

jobs:
  migrate:
    runs-on: ubuntu-latest
    environment: production  # Requires approval
    
    steps:
      - uses: actions/checkout@v3
      
      - name: Verify FLYWAY_ENV is set
        run: |
          if [ -z "$FLYWAY_ENV" ]; then
            echo "ERROR: FLYWAY_ENV not set. Aborting migration."
            exit 1
          fi
          echo "FLYWAY_ENV is set to: $FLYWAY_ENV"
      
      - name: Run Flyway Migration
        run: ./gradlew flywayMigrate
        env:
          FLYWAY_ENV: production
```

### GitLab CI Example

```yaml
# .gitlab-ci.yml
production_migrate:
  stage: deploy
  only:
    - main
  environment:
    name: production
    action: prepare
  before_script:
    - export FLYWAY_ENV=production
    - echo "FLYWAY_ENV is set to: $FLYWAY_ENV"
  script:
    - ./gradlew flywayMigrate
  when: manual  # Requires manual approval
```

---

## Troubleshooting

### Issue: "FLYWAY_ENV not recognized"

**Solution:** Ensure environment variable is actually set
```bash
# Check if set
echo $FLYWAY_ENV

# If empty, set it
export FLYWAY_ENV=production
echo $FLYWAY_ENV  # Should print "production"
```

### Issue: "Schemas still have objects after cleanup"

**Solution:** Only run cleanup-dependent migrations after manual schema drop
```bash
# Option 1: Drop manually before migration
psql -c "DROP SCHEMA private_schema CASCADE;"
psql -c "DROP SCHEMA api_schema CASCADE;"
./gradlew flywayMigrate

# Option 2: If using backup, restore and try again
pg_restore backup_20260209.sql
```

### Issue: "I accidentally used CASCADE in production"

**Alert:** This is a data loss event

**Immediate actions:**
1. Stop all applications connecting to database
2. Restore from backup
3. Investigate how CASCADE was allowed
4. Review deployment process to prevent repeat

---

## Security Best Practices

✅ **DO:**
- Use RESTRICT in production (default)
- Set FLYWAY_ENV=production in CI/CD
- Require manual approval before prod migrations
- Backup before every production migration
- Test on staging first
- Review all schema changes with DBA

❌ **DON'T:**
- Use CASCADE in production
- Deploy without FLYWAY_ENV check
- Skip backups
- Test only on production
- Assume schemas are clean (verify first)
- Override RESTRICT without DBA approval

---

## Verification Commands

### Check if migration will succeed

```sql
-- Will RESTRICT work?
-- Try to drop with RESTRICT (simulated)
BEGIN;
  DROP SCHEMA IF EXISTS private_schema RESTRICT;
  -- If this completes without error, migration will succeed
ROLLBACK;  -- Don't actually drop, just test
```

### Monitor active migration

```bash
# Watch Flyway history
psql -c "SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 10;" 

# Check for recent errors
psql -c "SELECT version, description, success, installed_on FROM flyway_schema_history WHERE success = false ORDER BY installed_rank DESC;"
```

### Audit environment

```bash
# Verify environment is set
psql -c "SHOW app.environment;"

# Check migration was logged with environment
psql -c "SELECT version, description, installed_on FROM flyway_schema_history WHERE version = 'v0_9_9';"
```

---

## Summary

| Aspect | Development | Production |
|--------|-------------|-----------|
| **Default behavior** | RESTRICT (safe) | RESTRICT (safe) |
| **Failure mode** | Errors if schema has objects | Errors if schema has objects |
| **Override to CASCADE** | Manual only, local only | NEVER (no permission) |
| **Backup required** | Optional | REQUIRED |
| **Approval required** | None | DBA + DevOps |
| **Testing location** | Local/dev database | Staging only |

---

**Status: ✅ PRODUCTION SAFE**

The migration uses RESTRICT by default, preventing accidental data loss. No CASCADE execution is possible without explicit manual code change (which would be caught in code review).

