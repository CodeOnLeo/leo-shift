#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

if [[ -f .env.local ]]; then
  set -a
  # shellcheck disable=SC1091
  source .env.local
  set +a
fi

if docker compose version >/dev/null 2>&1; then
  COMPOSE=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  COMPOSE=(docker-compose)
else
  echo "Docker Compose is required. Install Docker Desktop or docker-compose." >&2
  exit 1
fi

"${COMPOSE[@]}" up -d postgres

export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$PWD/.gradle-home}"
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
