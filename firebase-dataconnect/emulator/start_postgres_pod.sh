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

# Determine the path of the directory containing this file.
SCRIPT_DIR="$(dirname "$0")"
readonly SCRIPT_DIR

# Create the podman "pod" if it is not already created.
# Bind the PostgreSQL server to port 5432 on the host, so that the host can connect to it.
# Bind the pgadmin server to port 8888 on the host, so that the host can connect to it.
if ! podman pod exists dataconnect_postgres ; then
  run_command podman pod create -p 5432:5432 -p 8888:80 dataconnect_postgres
fi

# Start the PostgreSQL server.
run_command podman \
  run \
  -dt \
  --rm \
  --pod dataconnect_postgres \
  -e POSTGRES_HOST_AUTH_METHOD=trust \
  --mount "type=volume,src=dataconnect_pgdata,dst=/var/lib/postgresql/data" \
  docker.io/library/postgres:15

# Start the pgadmin4 server.
readonly PGADMIN_EMAIL="admin@google.com"
readonly PGADMIN_PASSWORD="password"
run_command podman \
  run \
  -dt \
  --rm \
  --pod dataconnect_postgres \
  -e PGADMIN_DEFAULT_EMAIL="${PGADMIN_EMAIL}" \
  -e PGADMIN_DEFAULT_PASSWORD="${PGADMIN_PASSWORD}" \
  --mount "type=bind,ro,src=${SCRIPT_DIR}/servers.json,dst=/pgadmin4/servers.json" \
  --mount "type=volume,src=dataconnect_pgadmin_data,dst=/var/lib/pgadmin" \
  docker.io/dpage/pgadmin4

echo

cat <<EOF
PostegreSQL server running on port 5432
The pgAdmin web UI can be viewed at http://localhost:8888
The pgAdmin login credentails are: username "${PGADMIN_EMAIL}" and password "${PGADMIN_PASSWORD}"
If prompted later on for a Postgresql database password, any value should be accepted (e.g. "password").

To shut everything down, run
  podman pod stop dataconnect_postgres

To delete the postgresql database, run
  podman volume rm --force dataconnect_pgdata

To delete the containers, run
  podman pod rm --force dataconnect_postgres

When running the Firebase Data Connect emulator, use this postgresql connection string:
  postgresql://postgres:postgres@localhost:5432?sslmode=disable
EOF
