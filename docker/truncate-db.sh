#!/usr/bin/env bash
set -euo pipefail

# Zera os dados de negocio preservando o schema e o historico do Flyway, para que
# cada subida da stack comece com o estacionamento vazio. As tabelas sao lidas do
# information_schema, entao migracoes novas entram sem precisar editar este script.
#
# Exporte SKIP_DB_TRUNCATE=1 para preservar os dados de uma execucao anterior.

CONTAINER="${MYSQL_CONTAINER:-teslapark-mysql}"

if [[ "${SKIP_DB_TRUNCATE:-0}" == "1" ]]; then
  printf 'SKIP_DB_TRUNCATE=1: dados preservados\n'
  exit 0
fi

if [[ "$(docker inspect --format '{{.State.Running}}' "$CONTAINER" 2>/dev/null)" != "true" ]]; then
  printf '%s nao esta rodando; nada a truncar\n' "$CONTAINER"
  exit 0
fi

# A senha vem do ambiente do proprio container, nunca da linha de comando do host.
run_sql() {
  docker exec --interactive "$CONTAINER" sh -c \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --user=root --database="$MYSQL_DATABASE" --batch --skip-column-names'
}

tables=$(
  run_sql <<'SQL'
SELECT table_name
  FROM information_schema.tables
 WHERE table_schema = DATABASE()
   AND table_type = 'BASE TABLE'
   AND table_name <> 'flyway_schema_history'
 ORDER BY table_name
SQL
)

if [[ -z "${tables//[[:space:]]/}" ]]; then
  printf 'schema ainda nao criado; nada a truncar\n'
  exit 0
fi

{
  printf 'SET FOREIGN_KEY_CHECKS = 0;\n'
  while read -r table; do
    [[ -n "$table" ]] && printf 'TRUNCATE TABLE `%s`;\n' "$table"
  done <<<"$tables"
  printf 'SET FOREIGN_KEY_CHECKS = 1;\n'
} | run_sql

printf 'base truncada:%s\n' "$(sed 's/^/ /' <<<"$tables" | tr -d '\n')"
