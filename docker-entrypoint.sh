#!/bin/sh
set -e

log() {
  echo "[entrypoint] $1"
}

urldecode() {
  local data="${1//+/ }"
  printf '%b' "${data//%/\\x}"
}

configure_postgres_from_database_url() {
  local raw_url="$1"
  if [ -z "$raw_url" ]; then
    return 0
  fi

  case "$raw_url" in
    postgres://*|postgresql://*) ;;
    *)
      return 0
      ;;
  esac

  local remainder="${raw_url#*://}"
  local host_and_path
  local credentials_section
  if printf '%s' "$remainder" | grep -q '@'; then
    credentials_section="${remainder%%@*}"
    host_and_path="${remainder#*@}"
  else
    credentials_section=""
    host_and_path="$remainder"
  fi

  local username=""
  local password=""
  if [ -n "$credentials_section" ]; then
    username="${credentials_section%%:*}"
    if [ "$username" = "$credentials_section" ]; then
      password=""
    else
      password="${credentials_section#*:}"
    fi
    username="$(urldecode "$username")"
    password="$(urldecode "$password")"
  fi

  local hostport="${host_and_path%%/*}"
  local path_with_params=""
  if [ "$hostport" = "$host_and_path" ]; then
    path_with_params=""
  else
    path_with_params="${host_and_path#*/}"
  fi

  local host="${hostport%%:*}"
  local port=""
  if [ "$host" = "$hostport" ]; then
    port=""
  else
    port="${hostport#*:}"
  fi

  local dbname=""
  local query=""
  if [ -n "$path_with_params" ]; then
    dbname="${path_with_params%%\?*}"
    if [ "$dbname" = "$path_with_params" ]; then
      query=""
    else
      query="${path_with_params#*\?}"
    fi
  fi

  local jdbc_url="jdbc:postgresql://$host"
  if [ -n "$port" ]; then
    jdbc_url="$jdbc_url:$port"
  fi
  if [ -n "$dbname" ]; then
    jdbc_url="$jdbc_url/$dbname"
  fi
  if [ -n "$query" ]; then
    jdbc_url="$jdbc_url?$query"
  fi

  if [ -z "$SPRING_DATASOURCE_URL" ]; then
    export SPRING_DATASOURCE_URL="$jdbc_url"
    log "DATABASE_URL 감지: $jdbc_url 사용"
  fi
  if [ -z "$SPRING_DATASOURCE_USERNAME" ] && [ -n "$username" ]; then
    export SPRING_DATASOURCE_USERNAME="$username"
  fi
  if [ -z "$SPRING_DATASOURCE_PASSWORD" ] && [ -n "$password" ]; then
    export SPRING_DATASOURCE_PASSWORD="$password"
  fi
}

configure_postgres_from_database_url "$DATABASE_URL"

JAVA_OPTS="${JAVA_OPTS:--Xmx512m -Xms256m}"
APP_PORT="${PORT:-8080}"

if [ "$#" -eq 0 ]; then
  set -- java $JAVA_OPTS -Dserver.port=$APP_PORT -jar /app/app.jar
fi

exec "$@"
