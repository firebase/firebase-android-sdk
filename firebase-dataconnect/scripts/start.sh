#!/bin/bash

set -euo pipefail
set -xv

# Determine the absolute path of the directory containing this file.
readonly SCRIPT_DIR="$(readlink -f $(dirname "$0"))"

# Create the podman "pod" if it is not already created.
# Bind the PostgreSQL server to port 5432 on the host, so that the host can connect to it.
# Bind the pgadmin server to port 8888 on the host, so that the host can connect to it.
if ! podman pod exists firemat_postgres_pod ; then
  podman pod create -p 5432:5432 -p 8888:80 firemat_postgres_pod
fi

# Start the PostgreSQL server.
podman \
  run \
  -dt \
  --rm \
  --pod firemat_postgres_pod \
  -e POSTGRES_HOST_AUTH_METHOD=trust \
  --mount "type=bind,ro,src=${SCRIPT_DIR}/postgres_dbinit.sh,dst=/docker-entrypoint-initdb.d/postgres_dbinit.sh" \
  --mount "type=volume,src=firemat_pgdata,dst=/var/lib/postgresql/data" \
  docker.io/library/postgres:15

# Start the pgadmin4 server.
podman \
  run \
  -dt \
  --rm \
  --pod firemat_postgres_pod \
  -e PGADMIN_DEFAULT_EMAIL=admin@google.com \
  -e PGADMIN_DEFAULT_PASSWORD=password \
  --mount "type=bind,ro,src=${SCRIPT_DIR}/servers.json,dst=/pgadmin4/servers.json" \
  --mount "type=volume,src=firemat_pgadmin_data,dst=/var/lib/pgadmin" \
  docker.io/dpage/pgadmin4

set +xv

echo
echo "PostegreSQL server running on port 5432"
echo "pgAdmin web server running on port 8888, which can be viewed by browsing to http://localhost:8888"
echo "To shut everything down, run: podman pod stop firemat_postgres_pod"
