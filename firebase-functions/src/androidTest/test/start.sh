#!/bin/bash
#
# Copyright 2018 Google LLC
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


# Sets up a project with the functions CLI and starts a backend to run
# integration tests against.

set -e

function LOG_FATAL {
    echo "$1" >&2
    exit 1
}

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
TEMP_DIR=$(mktemp -d)
echo "Creating functions in ${TEMP_DIR}"

# Set up the functions directory.
cp "${SCRIPT_DIR}/index.js" "${TEMP_DIR}/" \
    || LOG_FATAL "Unable to copy index.js from ${SCRIPT_DIR}"
cp "${SCRIPT_DIR}/package.json" "${TEMP_DIR}/" \
    || LOG_FATAL "Unable to copy package.json from ${SCRIPT_DIR}"
cd "${TEMP_DIR}" || LOG_FATAL "Unable to cd to ${TEMP_DIR}"
npm install

# Start the server.
FUNCTIONS="./node_modules/.bin/functions"

echo "Setting up Functions emulator config..."
${FUNCTIONS} config set projectId functions-integration-test
${FUNCTIONS} config set supervisorPort 5005
${FUNCTIONS} config set region us-central1

echo "Restarting Functions emulator..."
# The output needs to be redirected to a file. Otherwise, the emulator process
# that gets spawned off will keep a reference to the output pipes from this
# shell process, and gradle will block until the functions emulator is stopped
# instead of running the tests (in macOS only).
${FUNCTIONS} restart >./functions.stdout 2>functions.stderr

echo "Deploying test functions..."
${FUNCTIONS} deploy dataTest --trigger-http
${FUNCTIONS} deploy scalarTest --trigger-http
${FUNCTIONS} deploy tokenTest --trigger-http
${FUNCTIONS} deploy instanceIdTest --trigger-http
${FUNCTIONS} deploy nullTest --trigger-http
${FUNCTIONS} deploy missingResultTest --trigger-http
${FUNCTIONS} deploy unknownErrorTest --trigger-http
${FUNCTIONS} deploy unhandledErrorTest --trigger-http
${FUNCTIONS} deploy explicitErrorTest --trigger-http
${FUNCTIONS} deploy httpErrorTest --trigger-http
${FUNCTIONS} deploy timeoutTest --trigger-http

echo "Finished setting up Cloud Functions emulator."
