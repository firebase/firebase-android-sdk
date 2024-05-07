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

# This script copies the minimal amount of code from a firebase-android-sdk
# repository to another clone of the firebase-android-sdk such that the
# firebase-dataconnect package will compile. To avoid leaking any mentions of
# "sql" or "graphql" the sources are scrubbed of all such mentions. When the
# firebase-dataconnect package is ultimately published these references will
# be restored; however, the initial publish to the public repos will be before
# the official announcements so we want to minimize the risk of leaks.
#
# Syntax: iorelease.sh <dest_dir>

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

echo "firebase-dataconnect" >>"${DEST_DIR}/subprojects.cfg"
cp "${SRC_DIR}/gradle/libs.versions.toml" "${DEST_DIR}/gradle/libs.versions.toml"
mkdir "${DEST_DIR}/firebase-dataconnect"
cp "${SRC_DIR}/firebase-dataconnect/firebase-dataconnect.gradle.kts" "${DEST_DIR}/firebase-dataconnect/firebase-dataconnect.gradle.kts"
echo "version=16.0.0-alpha01" >"${DEST_DIR}/firebase-dataconnect/gradle.properties"
mkdir "${DEST_DIR}/firebase-dataconnect/src"
cp -rv "${SRC_DIR}/firebase-dataconnect/src/main" "${DEST_DIR}/firebase-dataconnect/src/main"
rm -rf "${DEST_DIR}/firebase-dataconnect/src/main/kotlin/com/google/firebase/dataconnect/querymgr"

mv "${DEST_DIR}/firebase-dataconnect/src/main/proto/google/firebase/dataconnect/proto/graphql_error.proto" "${DEST_DIR}/firebase-dataconnect/src/main/proto/google/firebase/dataconnect/proto/error.proto"
for f in error.proto connector_service.proto ; do
  sed -i -e 's/graphql_error/error/' "${DEST_DIR}/firebase-dataconnect/src/main/proto/google/firebase/dataconnect/proto/$f"
  sed -i -e 's/Graphql/DataConnectOperation/' "${DEST_DIR}/firebase-dataconnect/src/main/proto/google/firebase/dataconnect/proto/$f"
done

sed -i -e 's/Graphql/DataConnectOperation/' "${DEST_DIR}/firebase-dataconnect/src/main/kotlin/com/google/firebase/dataconnect/core/DataConnectGrpcClient.kt"

for f in `find "${DEST_DIR}/firebase-dataconnect" -type f '!' -name '*.xml'` ; do
  sed -i -e '/^\s*\/\//d' "$f"
  sed -i -e 's/\s\+\/\/.*$//' "$f"
done

sed -i -e '/project/!p; /protolite-well-known-types/!d' "${DEST_DIR}/firebase-dataconnect/firebase-dataconnect.gradle.kts"
