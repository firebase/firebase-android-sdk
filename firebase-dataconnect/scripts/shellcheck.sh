#!/bin/bash

# Copyright 2025 Google LLC
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

DATACONNECT_ROOT_DIR="$(dirname "$0")/.."
readonly DATACONNECT_ROOT_DIR

sh_files=(
  "${DATACONNECT_ROOT_DIR}"/emulator/*.sh
  "${DATACONNECT_ROOT_DIR}"/scripts/*.sh
)

readonly args=(
  shellcheck
  --norc
  --enable=all
  --shell=bash
  "${sh_files[@]}"
)

echo "${args[*]}"
exec "${args[@]}"
