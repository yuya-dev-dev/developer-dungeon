#!/bin/sh
set -eu

migrator_password="$(cat /run/secrets/db_migrator_password)"
app_password="$(cat /run/secrets/db_app_password)"

psql --set=ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  --set=migrator_password="$migrator_password" --set=app_password="$app_password" <<'SQL'
CREATE ROLE developer_dungeon_migrator LOGIN PASSWORD :'migrator_password';
CREATE ROLE developer_dungeon_app LOGIN PASSWORD :'app_password';
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
CREATE SCHEMA developer_dungeon AUTHORIZATION developer_dungeon_migrator;
GRANT CONNECT ON DATABASE developer_dungeon TO developer_dungeon_migrator, developer_dungeon_app;
ALTER ROLE developer_dungeon_app IN DATABASE developer_dungeon SET search_path = developer_dungeon;
SQL
