#!/bin/bash

# Copyright 2024 Google LLC
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

set -euo pipefail

readonly SCRIPT_DIR="$(dirname "$0")"
readonly SELF_EXECUTABLE="$0"
readonly LOG_PREFIX="[$0] "
readonly DEFAULT_POSTGRESQL_STRING='postgresql://postgres:postgres@localhost:5432?sslmode=disable'

function main {
  parse_args "$@"
  log "FIREBASE_DATACONNECT_POSTGRESQL_STRING=${FIREBASE_DATACONNECT_POSTGRESQL_STRING}"
  log "DATACONNECT_EMULATOR_BINARY_PATH=${DATACONNECT_EMULATOR_BINARY_PATH}"
  log "DATA_CONNECT_PREVIEW=${DATA_CONNECT_PREVIEW}"
  run_command firebase --debug emulators:start --only auth,dataconnect
}

function parse_args {
  local emulator_binary=''
  local postgresql_string="${DEFAULT_POSTGRESQL_STRING}"
  local preview_flags=''
  local wipe_and_restart_postgres_pod=0

  local OPTIND=1
  local OPTERR=0
  while getopts ":c:p:v:hw" arg ; do
    case "$arg" in
      c) emulator_binary="${OPTARG}" ;;
      p) postgresql_string="${OPTARG}" ;;
      v) preview_flags="${OPTARG}" ;;
      w) wipe_and_restart_postgres_pod=1 ;;
      h)
        print_help
        exit 0
        ;;
      :)
        echo "ERROR: missing value after option: -${OPTARG}" >&2
        echo "Run with -h for help" >&2
        exit 2
        ;;
      ?)
        echo "ERROR: unrecognized option: -${OPTARG}" >&2
        echo "Run with -h for help" >&2
        exit 2
        ;;
      *)
        echo "INTERNAL ERROR: unknown argument: $arg" >&2
        exit 1
        ;;
    esac
  done

  export DATACONNECT_EMULATOR_BINARY_PATH="${emulator_binary}"
  export FIREBASE_DATACONNECT_POSTGRESQL_STRING="${postgresql_string}"
  export DATA_CONNECT_PREVIEW="${preview_flags}"

  if [[ $wipe_and_restart_postgres_pod == "1" ]] ; then
    run_command "${SCRIPT_DIR}/wipe_postgres_db.sh"
    run_command "${SCRIPT_DIR}/start_postgres_pod.sh"
  fi
}

function run_command {
  log "Running command: $*"
  "$@"
}

function print_help {
  echo "Firebase Data Connect Emulator Launcher Helper"
  echo
  echo "This script provides a convenient way to launch the Firebase Data Connect"
  echo "and Firebase Authentication emulators in a way that is amenable for running"
  echo "the integration tests."
  echo
  echo "Syntax: ${SELF_EXECUTABLE} [options]"
  echo
  echo "Options:"
  echo "  -c <data_connect_emulator_binary_path>"
  echo "    Uses the Data Connect Emulator binary at the given path. If not specified, "
  echo "    or if specified as the empty string, then the emulator binary is downloaded."
  echo
  echo "  -p <postgresql_connection_string>"
  echo "    Uses the given string to connect to the PostgreSQL server. If not specified "
  echo "    the the default value of \"${DEFAULT_POSTGRESQL_STRING}\" is used."
  echo "    If specified as the empty string then an ephemeral PGLite server is used."
  echo
  echo "  -v <data_connect_preview_flags>"
  echo "    Uses the given Data Connect preview flags when launching the emulator."
  echo "    If not specified then an empty string is used, meaning that no preview flags"
  echo "    are in effect."
  echo
  echo "  -w"
  echo "    If specified, then a local PostgreSQL container is wiped and restarted"
  echo "    before launching the emulators. This is accomplished by running the scripts"
  echo "    ./wipe_postgres_db.sh followed by ./start_postgres_pod.sh."
  echo
  echo "  -h"
  echo "    Print this help screen and exit, as if successful."
}

function log {
  echo "${LOG_PREFIX}$*"
}

main "$@"
