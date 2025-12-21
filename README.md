# Liquibase Database Migrations

This directory contains all database migrations managed by Liquibase.

Structure:
- `changelog/` - Contains the master changelog and all changelogs
  - `master.xml` - The master changelog that includes all other changelogs
  - `changes/` - Directory for individual changelog files
    - Each file should follow the naming convention: `YYYYMMDD_HHMM_description.xml`
- `liquibase.properties` - Configuration file for Liquibase

## How to use Liquibase

### Running migrations
```bash
# Run all pending migrations
liquibase update

# Generate SQL without executing
liquibase updateSQL

# Rollback the last 1 change set
liquibase rollbackCount 1
```

### Creating new migrations
1. Create a new changelog file in `changelog/changes/` with the naming convention `YYYYMMDD_HHMM_description.xml`
2. Add your changes in the file
3. Include the new file in `changelog/master.xml`
4. Run `liquibase update` to apply the changes