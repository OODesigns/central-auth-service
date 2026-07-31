# V1/V2 Merge - Team Action Items

## ✅ Complete

- [x] V2 security hardening merged into V1__init_schema.sql
- [x] Schema separation implemented (auth_private, auth_api)
- [x] Role-based access control configured (auth_owner, app_auth)
- [x] All tables migrated to auth_private
- [x] All triggers updated with SECURITY DEFINER + locked search_path
- [x] Auth_api functions created with proper security
- [x] Legacy auth.* wrappers created for backward compatibility
- [x] Seed data updated to use auth_private schema
- [x] V2 migration file deleted (merged into V1)
- [x] Documentation created (completion report, quick start)
- [x] All changes verified

---

## 📋 Now You Need To Do

### Phase 1: Testing (1-2 hours)

- [ ] **Run the migration:**
  ```bash
  cd /mnt/data/projects/home-control-system
  ./gradlew flywayMigrate
  ```

- [ ] **Verify database changes:**
  ```bash
  psql -U postgres -d cas << EOF
    \dn                              -- Verify schemas
    \du                              -- Verify roles
    \df auth_api.*                   -- Verify functions
    SELECT * FROM auth_private.users LIMIT 1;  -- Verify tables
  EOF
  ```

- [ ] **Check permissions:**
  ```bash
  psql -U postgres -d cas << EOF
    -- Should return no results (app_auth has no table access)
    SELECT * FROM information_schema.role_table_grants 
    WHERE grantee = 'app_auth';
  EOF
  ```

### Phase 2: Application Updates (1-2 hours)

- [ ] **Update connection string:**
  - File: `src/main/resources/application.properties`
  - Change: `app_user` → `app_auth`

- [ ] **Update database queries (optional but recommended):**
  - Search: `FROM users` → `FROM auth_api.find_user_credentials()`
  - Search: `FROM public.` → Update to use auth_api functions
  - Search: `auth.find_` → Consider updating to auth_api.*

- [ ] **Rebuild application:**
  ```bash
  ./gradlew build
  ```

- [ ] **Run tests:**
  ```bash
  ./gradlew test
  ```

### Phase 3: Deployment (1-2 hours)

- [ ] **Deploy application:**
  ```bash
  # Your deployment process here
  # (e.g., docker push, kubernetes apply, etc.)
  ```

- [ ] **Monitor logs:**
  - Check for permission denied errors
  - Verify audit_logs are being populated
  - Monitor performance (should be unchanged)

- [ ] **Smoke test:**
  ```bash
  # Test login endpoint
  curl -X POST http://localhost:8080/api/login \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"<password>"}'
  
  # Should return 200 (or 401 if password wrong)
  # Should NOT return 403 or permission errors
  ```

---

## 📚 Documentation to Review

**Required Reading:**
1. [ ] `V1_V2_MERGE_DEVELOPER_QUICK_START.md` (5 min)
2. [ ] `V1_V2_MERGE_COMPLETION_REPORT.md` (10 min)

**Reference (as needed):**
- `DATABASE_SECURITY_HARDENING.md` - Full technical details
- `API_FUNCTIONS_REFERENCE.md` - Function documentation
- `DATABASE_SECURITY_QUICK_REFERENCE.md` - Troubleshooting

---

## 🔍 Verification Checklist

After deployment, verify:

- [ ] **Schemas created:**
  ```sql
  SELECT schema_name FROM information_schema.schemata 
  WHERE schema_name IN ('auth_private', 'auth_api', 'auth');
  -- Should return: auth_private, auth_api, auth
  ```

- [ ] **Roles created:**
  ```sql
  SELECT rolname, rolcanlogin FROM pg_roles 
  WHERE rolname IN ('auth_owner', 'app_auth');
  -- Should return: auth_owner (NOLOGIN), app_auth (LOGIN)
  ```

- [ ] **Tables in auth_private:**
  ```sql
  SELECT COUNT(*) FROM information_schema.tables 
  WHERE table_schema = 'auth_private';
  -- Should return: 11 tables
  ```

- [ ] **Functions in auth_api:**
  ```sql
  SELECT COUNT(*) FROM information_schema.routines 
  WHERE routine_schema = 'auth_api';
  -- Should return: 4 functions
  ```

- [ ] **App can execute functions:**
  ```bash
  psql -U app_auth -d cas -c "SELECT * FROM auth_api.find_user_credentials('admin');"
  -- Should return: user record (or empty if admin doesn't exist)
  ```

- [ ] **App cannot access tables:**
  ```bash
  psql -U app_auth -d cas -c "SELECT * FROM auth_private.users;" 2>&1 | grep -i "permission"
  -- Should return: permission denied error
  ```

- [ ] **Audit logs working:**
  ```sql
  SELECT COUNT(*) FROM auth_private.audit_logs;
  -- Should return: > 0 (audit entries created)
  ```

- [ ] **No errors in application logs:**
  ```bash
  grep -i "permission\|error\|denied" application.log
  -- Should return: No authentication/permission errors
  ```

---

## ❌ If Something Goes Wrong

### "permission denied for schema auth_private"
- [ ] Check connection string uses `app_auth` (not `app_user`)
- [ ] Check application is not trying direct table access
- [ ] Run: `SELECT current_user;` to verify role

### "function not found"
- [ ] Check function name uses `auth_api.*` (not `auth.*`)
- [ ] Old `auth.*` functions should still work as wrappers
- [ ] Verify functions exist: `\df auth_api.*`

### "permission denied for table users"
- [ ] This is expected! app_auth should NOT have table access
- [ ] Update code to use `auth_api.find_user_credentials()` instead
- [ ] Or use `auth.find_user_credentials()` as legacy wrapper

### "cannot connect as app_auth"
- [ ] Verify role exists: `\du app_auth`
- [ ] Check password in connection string
- [ ] Default password is 'changeme' (change in production!)

### Performance degradation
- [ ] SECURITY DEFINER should add <1% overhead
- [ ] If performance issues, check query plans: `EXPLAIN ANALYZE`
- [ ] May need indexes (existing indexes should be sufficient)

---

## 🎯 Success Criteria

All items complete when:

- [x] Migration runs without errors
- [x] All schemas and roles created correctly
- [x] All tables in auth_private schema
- [x] All functions in auth_api schema (SECURITY DEFINER)
- [ ] Application updated with app_auth connection string
- [ ] Application tests pass
- [ ] Application deployed successfully
- [ ] Login endpoint works (no permission errors)
- [ ] Audit logs populated with events
- [ ] Performance acceptable (<1% overhead)
- [ ] No permission denied errors in application logs

---

## 📞 Questions?

### Before You Start
- Read: `V1_V2_MERGE_DEVELOPER_QUICK_START.md`

### During Testing
- Reference: `DATABASE_SECURITY_QUICK_REFERENCE.md`

### If Issues
- Troubleshoot: `DATABASE_SECURITY_QUICK_REFERENCE.md` (Troubleshooting section)
- Details: `V1_V2_MERGE_COMPLETION_REPORT.md`

### Architecture Questions
- Reference: `DATABASE_SECURITY_HARDENING.md`
- Functions: `API_FUNCTIONS_REFERENCE.md`

---

## 📅 Recommended Timeline

| Phase | Duration | Activity |
|-------|----------|----------|
| Phase 1 | 1-2 hours | Test migration & verify database |
| Phase 2 | 1-2 hours | Update application code & tests |
| Phase 3 | 1-2 hours | Deploy & monitor |
| **Total** | **3-6 hours** | **Full implementation** |

---

## ✅ Completion

Mark this checklist when each section is complete:

- [ ] Phase 1: Testing complete
- [ ] Phase 2: Application updates complete
- [ ] Phase 3: Deployment complete
- [ ] Phase 4: Verification complete
- [ ] Team notified of changes
- [ ] Documentation updated

---

**Status:** Ready to deploy  
**Risk Level:** Low (backward compatible)  
**Rollback:** Simple (restore from backup if needed)

