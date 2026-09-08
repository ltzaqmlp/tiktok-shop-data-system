#!/bin/sh
set -eu
psql -v ON_ERROR_STOP=1 --username postgres --dbname shop_operations -v app_password="$APP_PASSWORD" <<'SQL'
CREATE ROLE shop_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD :'app_password';
ALTER DATABASE shop_operations OWNER TO shop_app;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE, CREATE ON SCHEMA public TO shop_app;
SQL
