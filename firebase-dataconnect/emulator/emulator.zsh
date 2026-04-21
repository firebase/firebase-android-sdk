#!/usr/bin/env zsh

# Copyright 2026 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

setopt errexit nounset pipefail

typeset -r self_executable="$0"
typeset -r log_prefix="[$0] "
typeset -r default_postgresql_string='postgresql://postgres:postgres@localhost:5432?sslmode=disable'

function main {
  cd "${0:A:h}"
  parse_args "$@"
  log "FIREBASE_DATACONNECT_POSTGRESQL_STRING=${FIREBASE_DATACONNECT_POSTGRESQL_STRING}"
  log "DATACONNECT_EMULATOR_BINARY_PATH=${DATACONNECT_EMULATOR_BINARY_PATH}"
  log "DATA_CONNECT_PREVIEW=${DATA_CONNECT_PREVIEW}"
  run_command firebase --debug emulators:start --only auth,dataconnect
}

function parse_args {
  zmodload zsh/zutil
  local -A opts
  zparseopts -D -E -A opts c: g p: v: w h || {
    echo "Run with -h for help" >&2
    exit 2
  }

  if (( ${+opts[-h]} )); then
    print_help
    exit 0
  fi

  if (( $# > 0 )); then
    echo "ERROR: unrecognized option or argument: $1" >&2
    echo "Run with -h for help" >&2
    exit 2
  fi

  local emulator_binary="${opts[-c]:-${opts[-g]+gradle}}"
  local postgresql_string="${opts[-p]:-$default_postgresql_string}"
  local preview_flags="${opts[-v]:-}"
  local wipe_and_restart_postgres_pod=${+opts[-w]}

  if [[ ${emulator_binary} != "gradle" ]] ; then
    export DATACONNECT_EMULATOR_BINARY_PATH="${emulator_binary}"
  else
    run_command "../../gradlew" -p ../.. --configure-on-demand :firebase-dataconnect:connectors:downloadDebugDataConnectExecutable
    local gradle_emulator_binaries=(../connectors/build/intermediates/dataconnect/debug/executable/*(N))
    if [[ ${#gradle_emulator_binaries} -ne 1 ]]; then
      log_error_and_exit "expected exactly 1 emulator binary from gradle, but got ${#gradle_emulator_binaries}: ${gradle_emulator_binaries[*]}"
    fi
    local gradle_emulator_binary="${gradle_emulator_binaries[1]}"
    if [[ ! -e ${gradle_emulator_binary} ]] ; then
      log_error_and_exit "emulator binary from gradle does not exist: ${gradle_emulator_binary}"
    fi
    export DATACONNECT_EMULATOR_BINARY_PATH="${gradle_emulator_binary}"
  fi

  export FIREBASE_DATACONNECT_POSTGRESQL_STRING="${postgresql_string}"
  export DATA_CONNECT_PREVIEW="${preview_flags}"

  if [[ ${wipe_and_restart_postgres_pod} == "1" ]] ; then
    run_command podman compose down -v
    run_command podman compose up -d

    echo "Waiting for Postgres service to appear to be healthy..."
    local postgres_health_check_number=0
    while : ; do
      (( postgres_health_check_number++ )) || true
      local postgres_service_status="$(print_postgres_status)"
      echo "Postgres service health check ${postgres_health_check_number}: ${postgres_service_status}"

      if [[ ${postgres_service_status} == *"(healthy)"* ]] ; then
        echo "Postgres service appears to be healthy after ${postgres_health_check_number} seconds"
        break
      elif [[ ${postgres_health_check_number} -eq 30 ]] ; then
        print_podman_compose_status
        echo "ERROR: Postgres service does not appear to be healthy after ${postgres_health_check_number} seconds" >&2
        exit 1
      fi

      sleep 1s
    done
  fi
}

function run_command {
  log "Running command: ${(q)@}"
  "$@"
}

function print_podman_compose_status {
  podman compose ps --format=json
}

function print_postgres_status {
  print_podman_compose_status | jq -re '.[] | select(.Labels["com.docker.compose.service"] == "postgres") | .Status'
}

function print_help {
  echo "Firebase Data Connect Emulator Launcher Helper"
  echo
  echo "This script provides a convenient way to launch the Firebase Data Connect"
  echo "and Firebase Authentication emulators in a way that is amenable for running"
  echo "the integration tests."
  echo
  echo "Syntax: ${self_executable} [options]"
  echo
  echo "Options:"
  echo "  -c <data_connect_emulator_binary_path>"
  echo "    Uses the Data Connect Emulator binary at the given path. A value of \"gradle\" "
  echo "    will use the same binary as the Gradle build. If not specified, or if specified "
  echo "    as the empty string, then the emulator binary is downloaded."
  echo
  echo "  -g"
  echo "    Shorthand for: -c gradle"
  echo
  echo "  -p <postgresql_connection_string>"
  echo "    Uses the given string to connect to the PostgreSQL server. If not specified "
  echo "    the the default value of \"${default_postgresql_string}\" is used."
  echo "    If specified as the empty string then an ephemeral PGLite server is used."
  echo
  echo "  -v <data_connect_preview_flags>"
  echo "    Uses the given Data Connect preview flags when launching the emulator."
  echo "    If not specified then an empty string is used, meaning that no preview flags"
  echo "    are in effect."
  echo
  echo "  -w"
  echo "    If specified, then a local PostgreSQL container is wiped and restarted"
  echo "    before launching the emulators. This is accomplished by running:"
  echo "    podman compose down -v && podman compose up -d"
  echo
  echo "  -h"
  echo "    Print this help screen and exit, as if successful."
}

function log {
  echo "${log_prefix}$*"
}

function log_error_and_exit {
  echo "${log_prefix}ERROR: $*" >&2
  exit 1
}

main "$@"
