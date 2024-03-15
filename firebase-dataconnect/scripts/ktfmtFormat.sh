#!/bin/bash

set -euo pipefail

if [[ $# -gt 0 ]] ; then
  echo "ERROR: no command-line arguments are supported, but got $*" >&2
  exit 2
fi

readonly PROJECT_ROOT_DIR="$(dirname "$0")/../.."

readonly TARGETS=(
  ":firebase-dataconnect:ktfmtFormat"
  ":firebase-dataconnect:androidTestutil:ktfmtFormat"
  ":firebase-dataconnect:connectors:ktfmtFormat"
  ":firebase-dataconnect:demo:ktfmtFormat"
  ":firebase-dataconnect:testutil:ktfmtFormat"
)

readonly args=(
  "${PROJECT_ROOT_DIR}/gradlew"
  "-p"
  "${PROJECT_ROOT_DIR}"
  "--configure-on-demand"
  "${TARGETS[@]}"
)

echo "${args[*]}"
exec "${args[@]}"
