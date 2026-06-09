#!/usr/bin/env bash

set -euo pipefail

COLIMA_SOCKET="${HOME}/.colima/default/docker.sock"

is_colima_context() {
  command -v docker >/dev/null 2>&1 || return 1
  [[ "$(docker context show 2>/dev/null || true)" == "colima" ]]
}

is_colima_running() {
  command -v colima >/dev/null 2>&1 || return 1
  [[ "$(colima status 2>/dev/null || true)" == "Running" ]]
}

configure_colima_env() {
  export DOCKER_HOST="unix://${COLIMA_SOCKET}"
  export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="${TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE:-/var/run/docker.sock}"
  echo "Using Colima Docker socket: ${DOCKER_HOST}"
}

if [[ -z "${DOCKER_HOST:-}" ]] && [[ -S "${COLIMA_SOCKET}" ]]; then
  if is_colima_context || is_colima_running; then
    configure_colima_env
  fi
fi

exec mvn "$@"
