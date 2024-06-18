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

# Set the DATACONNECT_EMULATOR_BINARY_PATH environment variable to point to this file.
# Then run: firebase emulators:start --only dataconnect

set -euo pipefail

readonly SELF_DIR="$(dirname "$0")"
readonly LOG_FILE="${SELF_DIR}/cli_wrapper.sh.log.txt"
readonly CLI_BINARY="${SELF_DIR}/cli"

readonly args=(
  "${CLI_BINARY}"
  "$@"
)

echo "$(date) pid=$$ ${args[*]}" >>"${LOG_FILE}"
exec "${args[@]}"
