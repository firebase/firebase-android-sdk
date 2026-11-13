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

# Runs CloudSqlEndToEndTest against a Firebase SQL service in production.
#
# The script applies src/androidTest/sql/e2e_setup.sql to the database of the service
# through the Cloud SQL Auth Proxy, as your Google account (gcloud auth login), and runs the tests,
# which call the service like an app would. Your account needs the owner role of the database once:
#   firebase dataconnect:sql:grant -R owner -E <your email> --service <service> --location <location>
# (run it from a directory with a firebase.json and dataconnect.yaml for the service). The service
# must not enforce App Check, because the tests send no App Check token.
#
# Configuration:
# - The project comes from a google-services.json file. Set FIREBASE_GOOGLE_SERVICES_PATH to the
#   file of the project, or put it at the root of the repository; the build copies it into
#   firebase-sql/ with the package name of the module. When FIREBASE_GOOGLE_SERVICES_PATH is
#   set, the script removes the copy of an earlier run first.
# - The service comes from the environment variables FIREBASE_SQL_SERVICE_ID and
#   FIREBASE_SQL_LOCATION of this script, which passes them to the build as the Gradle properties
#   targetServiceId and targetLocation:
#     FIREBASE_SQL_SERVICE_ID=my-service FIREBASE_SQL_LOCATION=us-east4 ./run_cloud_tests.sh
# - The Cloud SQL instance and the database of the service are looked up with the Firebase CLI.
#
# With RUN_TESTS=false, the script only creates the schema, so that the tests can run from Android
# Studio.
#
# Usage: FIREBASE_SQL_SERVICE_ID=<id> FIREBASE_SQL_LOCATION=<location> [RUN_TESTS=false]
#   ./run_cloud_tests.sh [extra Gradle arguments]
# The log of the proxy is written to build/e2e-logs/.
set -euo pipefail

MODULE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(dirname "$MODULE_DIR")"

SERVICE_ID=${FIREBASE_SQL_SERVICE_ID:-""}
SERVICE_LOCATION=${FIREBASE_SQL_LOCATION:-""}
RUN_TESTS=${RUN_TESTS:-"true"}
PROXY_PORT=${PROXY_PORT:-5433}
# A test class, or a single test as `<class>#<method>`.
TEST_FILTER=${TEST_FILTER:-"com.google.firebase.sql.CloudSqlEndToEndTest"}
STARTUP_TIMEOUT_SECONDS=30
# How long to wait for the device to finish booting. A cold boot of an emulator can take minutes.
BOOT_TIMEOUT_SECONDS=180

SQL_DIR="$MODULE_DIR/src/androidTest/sql"
LOG_DIR="$MODULE_DIR/build/e2e-logs"

PROXY_PID=""

# Registered before anything starts, so that an early failure still stops what was started.
cleanup() {
  [[ -n "$PROXY_PID" ]] && kill "$PROXY_PID" 2>/dev/null || true
}
trap cleanup EXIT
# Ctrl-C and kill end the script through its EXIT trap, so that the proxy stops too.
trap "exit 130" INT
trap "exit 143" TERM

fail() {
  echo "❌ Error: $1" >&2
  exit 1
}

# Waits until `$2` succeeds, or fails if the process `$3` exits or the timeout expires.
wait_for() {
  local name=$1 check=$2 pid=$3 log=$4
  for ((i = 0; i < STARTUP_TIMEOUT_SECONDS; i++)); do
    if eval "$check" >/dev/null 2>&1; then
      return 0
    fi
    if ! kill -0 "$pid" 2>/dev/null; then
      tail -n 20 "$log" >&2 || true
      fail "$name exited. See $log."
    fi
    sleep 1
  done
  tail -n 20 "$log" >&2 || true
  fail "$name was not ready after ${STARTUP_TIMEOUT_SECONDS}s. See $log."
}

[[ -n "$SERVICE_ID" && -n "$SERVICE_LOCATION" ]] ||
  fail "Set the environment variables FIREBASE_SQL_SERVICE_ID and FIREBASE_SQL_LOCATION."

# The google-services.json file that the build uses, as the copy-google-services plugin picks it.
MODULE_GOOGLE_SERVICES="$MODULE_DIR/google-services.json"
if [[ -n "${FIREBASE_GOOGLE_SERVICES_PATH:-}" ]]; then
  [[ -f "$FIREBASE_GOOGLE_SERVICES_PATH" ]] ||
    fail "FIREBASE_GOOGLE_SERVICES_PATH names a file that does not exist: $FIREBASE_GOOGLE_SERVICES_PATH"
  # The plugin copies the file only when the module has none, so a copy of an earlier run would win.
  rm -f "$MODULE_GOOGLE_SERVICES"
  GOOGLE_SERVICES_JSON="$FIREBASE_GOOGLE_SERVICES_PATH"
elif [[ -f "$MODULE_GOOGLE_SERVICES" ]]; then
  GOOGLE_SERVICES_JSON="$MODULE_GOOGLE_SERVICES"
elif [[ -f "$REPO_ROOT/google-services.json" ]]; then
  GOOGLE_SERVICES_JSON="$REPO_ROOT/google-services.json"
else
  fail "No google-services.json. Set FIREBASE_GOOGLE_SERVICES_PATH to the file of the project of $SERVICE_ID."
fi
PROJECT_ID="$(sed -n 's/.*"project_id": *"\([^"]*\)".*/\1/p' "$GOOGLE_SERVICES_JSON" | head -n 1)"
[[ -n "$PROJECT_ID" ]] || fail "$GOOGLE_SERVICES_JSON has no project_id."
echo "Service $SERVICE_ID in $SERVICE_LOCATION of project $PROJECT_ID (from $GOOGLE_SERVICES_JSON)"

echo "=== Checking E2E Test Dependencies ==="
command -v cloud-sql-proxy >/dev/null ||
  fail "cloud-sql-proxy could not be found. Please install it: brew install cloud-sql-proxy"
command -v gcloud >/dev/null || fail "gcloud could not be found. Please install the Google Cloud CLI."
command -v firebase >/dev/null || fail "firebase could not be found. Please install the Firebase CLI."
command -v python3 >/dev/null || fail "python3 could not be found."
if [[ "$RUN_TESTS" == "true" ]]; then
  command -v adb >/dev/null || fail "adb could not be found. Add the Android SDK platform-tools to PATH."
fi

# psql is optional: without it, the schema must already exist. Homebrew installs it with libpq,
# which is not linked into PATH.
PSQL=$(command -v psql || true)
if [[ -z "$PSQL" ]] && command -v brew >/dev/null; then
  BREW_PSQL="$(brew --prefix libpq 2>/dev/null)/bin/psql"
  [[ -x "$BREW_PSQL" ]] && PSQL="$BREW_PSQL"
fi
echo "✅ Dependencies verified."

if [[ -n "$PSQL" ]]; then
  mkdir -p "$LOG_DIR"

  echo "=== Looking up the database of $SERVICE_ID ==="
  # The CLI describes the data source as "CloudSQL Instance: <instance>\nDatabase: <database>".
  DATASOURCE="$(firebase dataconnect:services:list --project "$PROJECT_ID" --json 2>/dev/null |
    python3 -c '
import json, sys
service_id, location = sys.argv[1], sys.argv[2]
for service in json.load(sys.stdin)["result"]["services"]:
    if service["serviceId"] == service_id and service["location"] == location:
        fields = dict(line.split(": ", 1) for line in service["datasource"].splitlines() if ": " in line)
        print(fields.get("CloudSQL Instance", "").strip(), fields.get("Database", "").strip())
' "$SERVICE_ID" "$SERVICE_LOCATION")" ||
    fail "Could not list the Firebase SQL services of $PROJECT_ID. Run firebase login."
  read -r SERVICE_INSTANCE_ID SERVICE_DB_NAME <<<"$DATASOURCE" || true
  [[ -n "${SERVICE_INSTANCE_ID:-}" && -n "${SERVICE_DB_NAME:-}" ]] ||
    fail "No Cloud SQL database for the service $SERVICE_ID in $SERVICE_LOCATION of $PROJECT_ID."
  SERVICE_INSTANCE="$(gcloud sql instances describe "$SERVICE_INSTANCE_ID" --project "$PROJECT_ID" \
    --format='value(connectionName)')" ||
    fail "Could not describe the Cloud SQL instance $SERVICE_INSTANCE_ID. Run gcloud auth login."

  # Succeeds when a server accepts connections on the local port `$1`.
  port_is_open() { (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null; }

  # A server that already listens on the port would pass the readiness check below even when the
  # proxy that this script starts fails.
  if port_is_open "$PROXY_PORT"; then
    fail "Port $PROXY_PORT is already in use (see lsof -nP -iTCP:$PROXY_PORT -sTCP:LISTEN). Stop that process, or set PROXY_PORT to a free port."
  fi

  echo "=== Starting Cloud SQL Auth Proxy for $SERVICE_INSTANCE ==="
  # The proxy logs in to the database as the Google account of gcloud.
  cloud-sql-proxy "$SERVICE_INSTANCE" --auto-iam-authn --port "$PROXY_PORT" \
    >"$LOG_DIR/cloud-sql-proxy.log" 2>&1 &
  PROXY_PID=$!
  wait_for "The Cloud SQL Auth Proxy" "port_is_open $PROXY_PORT" "$PROXY_PID" \
    "$LOG_DIR/cloud-sql-proxy.log"

  echo "=== Creating the E2E schema in $SERVICE_DB_NAME ==="
  IAM_USER="$(gcloud config get-value account 2>/dev/null)"
  [[ -n "$IAM_USER" ]] || fail "No gcloud account. Run gcloud auth login."
  "$PSQL" "host=127.0.0.1 port=$PROXY_PORT user=$IAM_USER dbname=$SERVICE_DB_NAME sslmode=disable" \
    -v ON_ERROR_STOP=1 -q \
    -v owner_role="firebaseowner_${SERVICE_DB_NAME}_public" \
    -f "$SQL_DIR/e2e_setup.sql" ||
    fail "Could not apply e2e_setup.sql as $IAM_USER. Does the account have the owner role of the database (see the top of this script)?"

  # The proxy is only needed for the schema.
  kill "$PROXY_PID" 2>/dev/null || true
  PROXY_PID=""
else
  echo "⚠️ psql could not be found (brew install libpq), so the schema is assumed to exist."
fi

if [[ "$RUN_TESTS" != "true" ]]; then
  echo "=== The schema is ready. Run CloudSqlEndToEndTest from Android Studio. ==="
  exit 0
fi

echo "=== Waiting for the device to finish booting ==="
# connectedCheck fails with "No online devices found" when it starts while an emulator is still
# booting. `adb shell` also fails when several devices are connected; set ANDROID_SERIAL to pick
# one.
device_is_booted() { [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; }
for ((i = 0; i < BOOT_TIMEOUT_SECONDS; i++)); do
  device_is_booted && break
  sleep 1
done
device_is_booted ||
  fail "No device finished booting within ${BOOT_TIMEOUT_SECONDS}s. Start an emulator, or set ANDROID_SERIAL when several devices are connected."
echo "✅ Device ready."

echo "=== Running E2E Tests ==="
cd "$REPO_ROOT"
./gradlew :firebase-sql:connectedCheck \
  "-Pandroid.testInstrumentationRunnerArguments.class=$TEST_FILTER" \
  "-PtargetServiceId=$SERVICE_ID" \
  "-PtargetLocation=$SERVICE_LOCATION" \
  "$@"

echo "=== Tests Finished Successfully! ==="
