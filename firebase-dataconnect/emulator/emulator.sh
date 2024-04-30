#!/bin/bash

set -euo pipefail

readonly CLI_ARGS=(
  ./cli
  -alsologtostderr=1
  -stderrthreshold=0
  -log_dir=logs
  dev
  --disable_sdk_generation=true
  "-local_connection_string=\"postgresql://postgres:postgres@localhost:5432/emulator?sslmode=disable\""
)

readonly FIREBASE_ARGS=(
  firebase emulators:exec
  --only auth
  --project dataconnect-demo
  "${CLI_ARGS[*]}"
)

set -xv

exec "${FIREBASE_ARGS[@]}"
