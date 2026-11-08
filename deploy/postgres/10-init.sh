#!/usr/bin/env bash
#
# Production Postgres bootstrap. Replaces infra/postgres/init.sql, which ships dev credentials.
#
# The official postgres image runs every file in /docker-entrypoint-initdb.d exactly once, on a
# brand-new (empty) data directory, with the container's environment available. Passwords are read
# from the environment so no credential is committed. One database and one login per service.
#
# NOTE: because this file is not committed executable, the entrypoint *sources* it rather than
# running it as a subprocess. So it must NOT use `set -e`/`set -u` (they would leak into the
# entrypoint and abort startup). Failures are therefore checked explicitly.
#
# Passwords are interpolated into SQL, so use URL-safe values (A-Za-z0-9 and -_.~). Generate them
# with:  openssl rand -base64 32 | tr -d '/+=' | cut -c1-32
#

require() {
    if [ -z "$(eval "echo \"\${$1:-}\"")" ]; then
        echo "[10-init] FATAL: $1 is required" >&2
        return 1
    fi
}

for var in AUTH_DB_PASSWORD GAME_DB_PASSWORD MATCHMAKING_DB_PASSWORD \
           NOTIFICATION_DB_PASSWORD ANALYSIS_DB_PASSWORD; do
    require "$var" || return 1 2>/dev/null || exit 1
done

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL || { echo "[10-init] FATAL: database/user creation failed" >&2; return 1 2>/dev/null || exit 1; }
    CREATE DATABASE auth_db;
    CREATE DATABASE game_db;
    CREATE DATABASE matchmaking_db;
    CREATE DATABASE notification_db;
    CREATE DATABASE analysis_db;

    CREATE USER auth_svc         WITH PASSWORD '${AUTH_DB_PASSWORD}';
    CREATE USER game_svc         WITH PASSWORD '${GAME_DB_PASSWORD}';
    CREATE USER matchmaking_svc  WITH PASSWORD '${MATCHMAKING_DB_PASSWORD}';
    CREATE USER notification_svc WITH PASSWORD '${NOTIFICATION_DB_PASSWORD}';
    CREATE USER analysis_svc     WITH PASSWORD '${ANALYSIS_DB_PASSWORD}';

    GRANT ALL PRIVILEGES ON DATABASE auth_db         TO auth_svc;
    GRANT ALL PRIVILEGES ON DATABASE game_db         TO game_svc;
    GRANT ALL PRIVILEGES ON DATABASE matchmaking_db  TO matchmaking_svc;
    GRANT ALL PRIVILEGES ON DATABASE notification_db TO notification_svc;
    GRANT ALL PRIVILEGES ON DATABASE analysis_db     TO analysis_svc;
EOSQL

# Postgres 15+ revokes CREATE on the public schema; each service needs it for Flyway.
for db in auth_db game_db matchmaking_db notification_db analysis_db; do
    svc="${db%_db}_svc"
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$db" \
        -c "GRANT ALL ON SCHEMA public TO ${svc};" \
        || { echo "[10-init] FATAL: grant on ${db} failed" >&2; return 1 2>/dev/null || exit 1; }
done

echo "[10-init] created 5 databases and 5 service logins"
