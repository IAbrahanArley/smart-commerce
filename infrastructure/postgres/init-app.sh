#!/bin/sh
(
set -eu

# Executado somente na inicializacao de um volume vazio. O processo Java nunca
# recebe a credencial administrativa. Flyway usa o proprietario local do banco,
# que nao possui privilegios de superusuario nem criacao de roles/bancos.
psql --username "$POSTGRES_USER" --dbname postgres \
  --set=ON_ERROR_STOP=1 \
  --set=app_user="$DB_USERNAME" \
  --set=app_password="$DB_PASSWORD" \
  --set=app_db="$DB_NAME" <<'SQL'
CREATE ROLE :"app_user" LOGIN PASSWORD :'app_password'
  NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
CREATE DATABASE :"app_db" OWNER :"app_user";
SQL
)
