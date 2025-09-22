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

PROJECT_ROOT_DIR="$(dirname "$0")/../.."
readonly PROJECT_ROOT_DIR

(
  set -xv
  cd "${PROJECT_ROOT_DIR}"/firebase-dataconnect/emulator
  ./wipe_postgres_db.sh
  ./start_postgres_pod.sh
)

readonly args=(
  "${PROJECT_ROOT_DIR}/gradlew"
  "-p"
  "${PROJECT_ROOT_DIR}"
  "--configure-on-demand"
  "$@"
  ":firebase-dataconnect:connectors:runDebugDataConnectEmulator"
)

echo "${args[*]}"
exec "${args[@]}"
