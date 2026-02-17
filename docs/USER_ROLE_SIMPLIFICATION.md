# User-Role Relationship Simplification

**Date:** 2026-02-16  
**Status:** Completed

## Summary

Simplified the user-role relationship from many-to-many to one-to-one. Each user now has exactly one role, eliminating the complexity of the `user_roles` join table.

## Rationale

- **Simpler model**: One user = one role = clearer authorization logic
- **Sufficient flexibility**: Role-to-permission mapping remains many-to-many, providing all needed flexibility
- **Easier to maintain**: Fewer tables, simpler queries, reduced join complexity
- **Industry standard**: Most authentication systems use one role per user

## Changes Made

### Database Schema Changes

#### 1. **V1_0_2__create_tables.sql** - Tables
- ✅ Added `role_id UUID NOT NULL` column to `users` table
- ✅ Added foreign key: `FOREIGN KEY (role_id) REFERENCES private_schema.roles(role_id) ON DELETE RESTRICT`
- ✅ Removed `user_roles` join table entirely
- ✅ Removed audit actions: `USER_ROLE_ASSIGNED`, `USER_ROLE_REMOVED`
- ✅ Updated table count from 11 to 10 tables
- ✅ Added comment for `role_id` column

#### 2. **V1_0_6__create_api_functions.sql** - API Functions
- ✅ Updated `api_schema.get_user(uuid)` to use `users.role_id` instead of `user_roles` join
- ✅ Added `password_reset_required_at` and `mfa_required_at` to return type
- ✅ Updated GROUP BY clause to include new fields
- ✅ Simplified query: removed `user_roles` join, direct `users.role_id → roles.role_id`

#### 3. **V1_1_0__seed_auth_data.sql** - Seed Data
- ✅ Updated admin user creation to set `role_id` directly
- ✅ Changed from separate INSERT + role assignment to single SELECT with role lookup
- ✅ Removed separate "ASSIGN ADMIN ROLE TO ADMIN USER" section
- ✅ Updated header comments to reflect simplified model

#### 4. **V1_2_0__add_totp_test_data.sql** - Test Data
- ✅ Added `private_schema.` prefixes to all table references
- ✅ Fixed backup codes generation to use `generation_batch_id`
- ✅ Removed references to non-existent `users.totp_enabled` column
- ✅ Updated audit log query to use proper joins
- ✅ Fixed cleanup reference comments

#### 5. **V1_0_3__create_indexes.sql** - Indexes
- ✅ Removed `idx_user_roles_user_id` index
- ✅ Removed `idx_user_roles_role_id` index
- ✅ Added `idx_users_role_id` index for efficient role-based queries
- ✅ Updated index count from 19 to 17

#### 6. **V1_0_4__create_trigger_functions.sql** - Trigger Functions
- ✅ Removed `audit_user_roles()` function entirely
- ✅ Updated function count from 11 to 10

#### 7. **V1_0_5__create_triggers.sql** - Triggers
- ✅ Removed `trg_audit_user_roles` trigger
- ✅ Updated trigger count from 12 to 11

### Java Code Changes

#### 8. **AdminLoginDatabaseIntegrationTest.java** - Integration Tests
- ✅ Updated table existence check: `user_roles` → `role_permissions`
- ✅ Updated admin role verification query to use `users.role_id` join
- ✅ Added `private_schema.` prefixes to queries
- ✅ Updated test documentation

### No Changes Required

- ✅ **User.java** - Domain entity already correct (has permissions, not roles)
- ✅ **UserRepository.java** - Already calls updated `get_user()` function
- ✅ **V1_3_0__add_auth_flow_permissions.sql** - No user_roles references

## New Schema Structure

### Before (Many-to-Many)
```
users ←→ user_roles ←→ roles ←→ role_permissions ←→ permissions
```

### After (Simplified One-to-One)
```
users → roles ←→ role_permissions ←→ permissions
```

## SQL Query Comparison

### Before
```sql
SELECT u.user_id, u.username, array_agg(p.name) AS permissions
FROM users u
LEFT JOIN user_roles ur ON u.user_id = ur.user_id
LEFT JOIN roles r ON ur.role_id = r.role_id
LEFT JOIN role_permissions rp ON r.role_id = rp.role_id
LEFT JOIN permissions p ON rp.permission_id = p.permission_id
WHERE u.user_id = ?
GROUP BY u.user_id, u.username;
```

### After
```sql
SELECT u.user_id, u.username, array_agg(p.name) AS permissions
FROM users u
LEFT JOIN roles r ON u.role_id = r.role_id
LEFT JOIN role_permissions rp ON r.role_id = rp.role_id
LEFT JOIN permissions p ON rp.permission_id = p.permission_id
WHERE u.user_id = ?
GROUP BY u.user_id, u.username;
```

**Result:** One fewer join, simpler query plan, same functionality.

## Benefits

1. **Performance**: One fewer table join in all user queries
2. **Simplicity**: Easier to understand and maintain
3. **Data integrity**: `ON DELETE RESTRICT` prevents role deletion while users exist
4. **Flexibility preserved**: Role-to-permission mapping still supports complex authorization
5. **Audit trail**: Role changes tracked via `USER_UPDATED` instead of separate events

## Migration Notes

- **Breaking change**: Requires database migration for existing deployments
- **Idempotent**: All seed data uses `ON CONFLICT DO NOTHING`
- **Safe**: Foreign key constraint prevents orphaned users

## Testing

All tests updated and passing:
- ✅ Database schema verification tests
- ✅ Admin user role assignment tests
- ✅ Integration tests with actual database

## Related Files

- `.devcontainer/flyway/sql/V1_0_2__create_tables.sql`
- `.devcontainer/flyway/sql/V1_0_3__create_indexes.sql`
- `.devcontainer/flyway/sql/V1_0_4__create_trigger_functions.sql`
- `.devcontainer/flyway/sql/V1_0_5__create_triggers.sql`
- `.devcontainer/flyway/sql/V1_0_6__create_api_functions.sql`
- `.devcontainer/flyway/sql/V1_1_0__seed_auth_data.sql`
- `.devcontainer/flyway/sql/V1_2_0__add_totp_test_data.sql`
- `src/test/java/com/oodesigns/cas/integration/database/AdminLoginDatabaseIntegrationTest.java`




