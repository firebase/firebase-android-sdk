#!/bin/bash

set -euo pipefail

if [[ $# -gt 0 ]] ; then
  echo "ERROR: no command-line arguments are supported, but got $*" >&2
  exit 2
fi

readonly PROJECT_ROOT_DIR="$(dirname "$0")/../.."

readonly TARGETS=(
  ":firebase-dataconnect:compileDebugKotlin"
  ":firebase-dataconnect:compileDebugUnitTestKotlin"
  ":firebase-dataconnect:compileDebugAndroidTestKotlin"
  ":firebase-dataconnect:androidTestutil:compileDebugKotlin"
  ":firebase-dataconnect:androidTestutil:compileDebugUnitTestKotlin"
  ":firebase-dataconnect:androidTestutil:compileDebugAndroidTestKotlin"
  ":firebase-dataconnect:connectors:compileDebugKotlin"
  ":firebase-dataconnect:connectors:compileDebugUnitTestKotlin"
  ":firebase-dataconnect:connectors:compileDebugAndroidTestKotlin"
  ":firebase-dataconnect:demo:compileDebugKotlin"
  ":firebase-dataconnect:demo:compileDebugUnitTestKotlin"
  ":firebase-dataconnect:demo:compileDebugAndroidTestKotlin"
  ":firebase-dataconnect:testutil:compileDebugKotlin"
  ":firebase-dataconnect:testutil:compileDebugUnitTestKotlin"
  ":firebase-dataconnect:testutil:compileDebugAndroidTestKotlin"
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
