#!/bin/bash

set -euo pipefail

readonly FIREBASE_ARGS=(
  firebase emulators:exec
  --only auth
  --project dataconnect-demo
  ./emulator_noauth.sh
)

echo "[$0] Running command: ${FIREBASE_ARGS[*]}"
exec "${FIREBASE_ARGS[@]}"
