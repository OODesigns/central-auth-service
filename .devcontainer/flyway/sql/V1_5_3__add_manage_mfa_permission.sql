INSERT INTO private_schema.permissions (name)
VALUES ('manage_mfa')
ON CONFLICT (name) DO NOTHING;

INSERT INTO private_schema.role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM private_schema.roles r
CROSS JOIN private_schema.permissions p
WHERE r.name = 'admin' AND p.name = 'manage_mfa'
ON CONFLICT (role_id, permission_id) DO NOTHING;

ALTER TABLE private_schema.permissions OWNER TO owner_role;
REVOKE ALL ON TABLE private_schema.permissions FROM PUBLIC;
REVOKE ALL ON TABLE private_schema.role_permissions FROM PUBLIC;