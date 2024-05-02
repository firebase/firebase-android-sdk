#!/bin/bash

# This script compiles the maven artifacts for firebase-dataconnect and
# copies them to the given destination directory.

set -euo pipefail

if [[ $# -eq 0 ]] ; then
  echo "ERROR: a destination directory must be specified" >&2
  exit 2
elif [[ $# -gt 1 ]] ; then
  shift
  echo "ERROR: unexpected arguments: $*" >&2
  exit 2
else
  readonly DEST_DIR="$1"
fi

if [[ ! -d "firebase-dataconnect" ]] ; then
  echo "ERROR: this script must be run from the top directory of the firebase-android-sdk" >&2
  exit 1
fi

function run_command {
  echo "[$0] Running command: $*"
  "$@"
}

run_command rm -rfv $HOME/.m2/repository/com/google/firebase/firebase-dataconnect
run_command ./gradlew -PprojectsToPublish=firebase-dataconnect publishReleasingLibrariesToMavenLocal

run_command rsync \
  -avzc \
  --include=com/ \
  --include=com/google/ \
  --include=com/google/firebase/ \
  --include=com/google/firebase/firebase-dataconnect/'***' \
  --exclude='*' \
  /$HOME/.m2/repository/ \
  "${DEST_DIR}"
