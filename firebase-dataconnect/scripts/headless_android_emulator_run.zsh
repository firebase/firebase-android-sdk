#!/bin/zsh

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

set -e -u -o pipefail

function show_help {
  echo "Runs an Android emulator in headless mode."
  echo "This can be useful for running instrumentation tests"
  echo "without the overhead of rendering the Android UI."
  echo
  echo "Syntax: $0 [avd]"
  echo
  echo "The AVD to use may be specified as the first argument,"
  echo "and _must_ be specified if there is more than one AVD"
  echo "configured in the Android emulator."
  echo
  echo "The ANDROID_HOME environment variable must be set and is used"
  echo "to find the Android emulator binary."
}

# Parse the command-line arguments.
zparseopts -D -E h=opt_help
if (( ${#opt_help} )); then
  show_help
  exit 0
fi
if [[ $# -eq 1 ]]; then
  readonly emulator_avd="$1"
elif [[ $# -gt 1 ]]; then
  echo "ERROR: unexpected command-line argument: $2" >&2
  echo "Run with -h for help." >&2
  exit 2
fi

# Validate ANDROID_HOME.
if [[ -z "${ANDROID_HOME}" ]] ; then
  echo "ERROR: ANDROID_HOME environment variable is not set." >&2
  exit 1
elif [[ ! -d "${ANDROID_HOME}" ]] ; then
  echo "ERROR: ANDROID_HOME environment specifies a non-existent directory: ${ANDROID_HOME}" >&2
  exit 1
fi

# Find the emulator command in ANDROID_HOME.
readonly emulator_cmd="${ANDROID_HOME}/emulator/emulator"
if [[ ! -e "${emulator_cmd}" ]] ; then
  echo "ERROR: file not found: ${emulator_cmd} (ANDROID_HOME=${ANDROID_HOME})" >&2
  exit 1
fi

# Determine the AVD to use, if one was not specified on the command line.
if (( ! ${+emulator_avd} )) ; then
  echo "Retrieving list of AVDs configured in the emulator"
  avds=(${(f)"$("${emulator_cmd}" -list-avds)"})
  readonly avds
  if [[ ${#avds[@]} -eq 0 ]] ; then
    echo "ERROR: no AVDs are configured in the Android emulator." >&2
    exit 1
  elif [[ ${#avds[@]} -gt 1 ]] ; then
    echo "ERROR: ${#avds[@]} AVDs are configured in the Android emulator." >&2
    echo "Specify the AVD to use as a command-line argument." >&2
    echo "Available avds: ${avds[*]}" >&2
    exit 1
  fi
  readonly emulator_avd="${avds[1]}"
fi

# Launch the emulator, with flags to minimize system resource usage
# and maximize performance.
readonly args=(
  "${emulator_cmd}"
  -avd "${emulator_avd}"
  -no-window
  -no-audio
  -no-boot-anim
  -accel on
  -gpu off
  -no-snapshot-load
  -no-snapshot-save
  -screen touch
  -netfast
  -no-sim
  -timezone UTC
  -skip-adb-auth
  -no-metrics
)

echo "${(q)args[@]}"
exec "${args[@]}"
