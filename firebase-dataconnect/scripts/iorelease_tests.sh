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

# This script copies the code required for running the unit and integration tests
# into the directory where iorelease.sh published the minimal source code. This code
# should _not_ be checked into source control; however, running the tests provides
# some assurances of the quality of the code being shipped.
#
# Syntax: iorelease_test.sh <dest_dir>

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
else
  readonly SRC_DIR="$(pwd)"
fi

set -xv

echo "firebase-dataconnect:testutil" >>"${DEST_DIR}/subprojects.cfg"
echo "firebase-dataconnect:androidTestutil" >>"${DEST_DIR}/subprojects.cfg"
echo "firebase-dataconnect:connectors" >>"${DEST_DIR}/subprojects.cfg"
cp "${SRC_DIR}/firebase-dataconnect/google-services.json" "${DEST_DIR}/firebase-dataconnect/google-services.json"
cp -rv "${SRC_DIR}/firebase-dataconnect/androidTestutil" "${DEST_DIR}/firebase-dataconnect/androidTestutil"
cp -rv "${SRC_DIR}/firebase-dataconnect/connectors" "${DEST_DIR}/firebase-dataconnect/connectors"
cp -rv "${SRC_DIR}/firebase-dataconnect/emulator" "${DEST_DIR}/firebase-dataconnect/emulator"
cp -rv "${SRC_DIR}/firebase-dataconnect/scripts" "${DEST_DIR}/firebase-dataconnect/scripts"
cp -rv "${SRC_DIR}/firebase-dataconnect/src/androidTest" "${DEST_DIR}/firebase-dataconnect/src/androidTest"
cp -rv "${SRC_DIR}/firebase-dataconnect/src/test" "${DEST_DIR}/firebase-dataconnect/src/test"
cp -rv "${SRC_DIR}/firebase-dataconnect/testutil" "${DEST_DIR}/firebase-dataconnect/testutil"

sed -i '/testImplementation.*mockito/a\  testImplementation(project(":firebase-dataconnect:testutil"))' "${DEST_DIR}/firebase-dataconnect/firebase-dataconnect.gradle.kts"
sed -i '/androidTestImplementation.*turbine/a\  androidTestImplementation(project(":firebase-dataconnect:androidTestutil"))' "${DEST_DIR}/firebase-dataconnect/firebase-dataconnect.gradle.kts"
sed -i '/androidTestImplementation.*turbine/a\  androidTestImplementation(project(":firebase-dataconnect:connectors"))' "${DEST_DIR}/firebase-dataconnect/firebase-dataconnect.gradle.kts"
sed -i '/androidTestImplementation.*turbine/a\  androidTestImplementation(project(":firebase-dataconnect:testutil"))' "${DEST_DIR}/firebase-dataconnect/firebase-dataconnect.gradle.kts"
