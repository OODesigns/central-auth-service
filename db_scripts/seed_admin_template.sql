-- Template for seeding an initial admin user.
-- IMPORTANT:
-- 1. Generate a strong, cryptographically secure hash for the password (e.g., using bcrypt or Argon2).
--    Do NOT store a plain-text password here.
-- 2. Replace 'your_generated_bcrypt_hash_here' with the actual hash.
-- 3. Run this script MANUALLY after the main schema has been created.

-- Example admin user details:
-- Username: admin

-- Before running, generate a password hash.
-- For example, using Python with bcrypt:
--   import bcrypt
--   password = b"YourSecurePassword123!" # Choose a strong password
--   hashed_password = bcrypt.hashpw(password, bcrypt.gensalt())
--   print(f"Hashed password for SQL: {hashed_password.decode()}")
-- Copy the output starting with $2b$ (or similar for your chosen algorithm)

INSERT INTO users (username, password_hash, created_at, updated_at)
VALUES (
    'admin',                                -- The desired username for the admin
    'your_generated_bcrypt_hash_here',      -- << REPLACE THIS with the actual generated hash
    NOW(),                                  -- Sets current timestamp for created_at
    NOW()                                   -- Sets current timestamp for updated_at
)
ON CONFLICT (username) DO NOTHING -- Optional: Prevents error if admin already exists, does nothing.

-- to confirm insertion.
SELECT * FROM users WHERE username = 'admin';

COMMIT;