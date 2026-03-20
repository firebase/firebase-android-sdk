#!/bin/bash

# Copyright 2026 Google LLC
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

set -euo pipefail

readonly DEFAULT_JAVA_VERSION="17"
readonly DEFAULT_ANDROID_EMULATOR_API_LEVEL="34"
readonly DEFAULT_NODEJS_VERSION="20"
readonly DEFAULT_FIREBASE_TOOLS_VERSION="15.8.0"
readonly DEFAULT_PYTHON_VERSION="3.13"

echo "Applying FDC defaults to GITHUB_ENV..."

set -x

echo "FDC_JAVA_VERSION=${FDC_JAVA_VERSION:-$DEFAULT_JAVA_VERSION}" >> "$GITHUB_ENV"
echo "FDC_ANDROID_EMULATOR_API_LEVEL=${FDC_ANDROID_EMULATOR_API_LEVEL:-$DEFAULT_ANDROID_EMULATOR_API_LEVEL}" >> "$GITHUB_ENV"
echo "FDC_NODEJS_VERSION=${FDC_NODEJS_VERSION:-$DEFAULT_NODEJS_VERSION}" >> "$GITHUB_ENV"
echo "FDC_FIREBASE_TOOLS_VERSION=${FDC_FIREBASE_TOOLS_VERSION:-$DEFAULT_FIREBASE_TOOLS_VERSION}" >> "$GITHUB_ENV"
echo "FDC_PYTHON_VERSION=${FDC_PYTHON_VERSION:-$DEFAULT_PYTHON_VERSION}" >> "$GITHUB_ENV"
