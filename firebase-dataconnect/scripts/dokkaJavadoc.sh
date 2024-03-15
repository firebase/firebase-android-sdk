#!/bin/bash

set -euo pipefail

if [[ $# -gt 0 ]] ; then
  echo "ERROR: no command-line arguments are supported, but got $*" >&2
  exit 2
fi

readonly PROJECT_ROOT_DIR="$(dirname "$0")/../.."

readonly args=(
  "${PROJECT_ROOT_DIR}/gradlew"
  "-p"
  "${PROJECT_ROOT_DIR}"
  "--configure-on-demand"
  ":firebase-dataconnect:dokkaJavadoc"
)

echo "${args[*]}"
"${args[@]}"

echo "Starting HTTP server to serve the generated documentation..."
(cd "${PROJECT_ROOT_DIR}/firebase-dataconnect/build/dokka/javadoc" && python -m http.server)
