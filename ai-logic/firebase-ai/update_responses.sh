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

# This script replaces mock response files for Vertex AI unit tests with a fresh
# clone of the shared repository of Vertex AI test data.

BRANCH_NAME=${1:-main}
REPO_NAME="vertexai-sdk-test-data"
REPO_LINK="https://github.com/FirebaseExtended/$REPO_NAME.git"

set -x

cd "$(dirname "$0")/src/test/resources" || exit
rm -rf "$REPO_NAME"
git clone "$REPO_LINK" --branch "$BRANCH_NAME" --quiet || exit
cd "$REPO_NAME" || exit
