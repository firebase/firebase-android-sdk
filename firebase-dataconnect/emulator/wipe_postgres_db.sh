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

# This script starts a postgresql server and pgadmin web interface using
# a docker image and the "podman" command. It is safe to run this script if the
# server is already running (it will just be a no-op).

set -euo pipefail

function run_command {
  echo "$*"
  "$@"
}

# Shut down the pod, if it's running
run_command podman pod stop dataconnect_postgres

# Delete the postgresql database
run_command podman volume rm --force dataconnect_pgdata
