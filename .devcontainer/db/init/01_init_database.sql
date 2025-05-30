-- This script is automatically executed by the Postgres container
CREATE DATABASE auth_db
WITH
  OWNER = postgres
  ENCODING = 'UTF8'
  LC_COLLATE = 'C.utf8'
  LC_CTYPE   = 'C.utf8'
  TABLESPACE = pg_default
  CONNECTION LIMIT = -1;