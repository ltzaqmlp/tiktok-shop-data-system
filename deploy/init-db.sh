#!/bin/sh
set -eu
psql -v ON_ERROR_STOP=1 --username postgres --dbname shop_operations -v app_password="$APP_PASSWORD" <<'SQL'
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'shop_app') THEN
    CREATE ROLE shop_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE;
  END IF;
END
$$;
ALTER ROLE shop_app LOGIN PASSWORD :'app_password';
ALTER DATABASE shop_operations OWNER TO shop_app;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE, CREATE ON SCHEMA public TO shop_app;
SQL
