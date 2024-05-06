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
