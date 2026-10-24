-- One database per service. Nothing is shared: a service may only reach its own schema.
CREATE DATABASE auth_db;
CREATE DATABASE game_db;
CREATE DATABASE matchmaking_db;
CREATE DATABASE notification_db;
CREATE DATABASE analysis_db;

-- One login per service, each with rights only over its own database.
CREATE USER auth_svc WITH PASSWORD 'auth_svc';
CREATE USER game_svc WITH PASSWORD 'game_svc';
CREATE USER matchmaking_svc WITH PASSWORD 'matchmaking_svc';
CREATE USER notification_svc WITH PASSWORD 'notification_svc';
CREATE USER analysis_svc WITH PASSWORD 'analysis_svc';

GRANT ALL PRIVILEGES ON DATABASE auth_db TO auth_svc;
GRANT ALL PRIVILEGES ON DATABASE game_db TO game_svc;
GRANT ALL PRIVILEGES ON DATABASE matchmaking_db TO matchmaking_svc;
GRANT ALL PRIVILEGES ON DATABASE notification_db TO notification_svc;
GRANT ALL PRIVILEGES ON DATABASE analysis_db TO analysis_svc;

-- Postgres 15+ revokes CREATE on the public schema by default; each service needs it for Flyway.
\connect auth_db
GRANT ALL ON SCHEMA public TO auth_svc;

\connect game_db
GRANT ALL ON SCHEMA public TO game_svc;

\connect matchmaking_db
GRANT ALL ON SCHEMA public TO matchmaking_svc;

\connect notification_db
GRANT ALL ON SCHEMA public TO notification_svc;

\connect analysis_db
GRANT ALL ON SCHEMA public TO analysis_svc;
