#!/bin/bash

set -euo pipefail

readonly CLI_ARGS=(
  ./cli
  -alsologtostderr=1
  -stderrthreshold=0
  -log_dir=logs
  dev
  -disable_sdk_generation=true
  -config_dir=dataconnect
  -local_connection_string='postgresql://postgres:postgres@localhost:5432?sslmode=disable'
)

echo "[$0] Running command: ${CLI_ARGS[*]}"
exec "${CLI_ARGS[@]}"
