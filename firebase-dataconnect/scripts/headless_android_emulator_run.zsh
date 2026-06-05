#!/usr/bin/env zsh

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

setopt errexit nounset pipefail

source "${0:A:h}/util/say.zsh"

show_help() {
  say "Runs an Android emulator in headless mode."
  say "This can be useful for running instrumentation tests"
  say "without the overhead of rendering the Android UI."
  say
  say "Syntax: $0 [avd] [-- emulator_flags...]"
  say
  say "The AVD to use may be specified as the first argument,"
  say "and _must_ be specified if there is more than one AVD"
  say "configured in the Android emulator."
  say
  say "Any arguments after '--' are passed directly to the emulator command."
  say
  say "The ANDROID_HOME environment variable must be set and is used"
  say "to find the Android emulator binary."
}

# Parse the command-line arguments.
zparseopts -D -E h=opt_help
if (( ${#opt_help} )); then
  show_help
  exit 0
fi

typeset -i double_dash_index=${@[(i)--]}
typeset -a script_args
typeset -a emulator_extra_args
if (( double_dash_index <= $# )); then
  script_args=( "${@[1,double_dash_index-1]}" )
  emulator_extra_args=( "${@[double_dash_index+1,$#]}" )
else
  script_args=( "$@" )
  emulator_extra_args=( )
fi

if (( ${#script_args} == 1 )); then
  typeset -r emulator_avd="${script_args[1]}"
elif (( ${#script_args} > 1 )); then
  say_error "unexpected command-line argument: ${script_args[2]}" >&2
  sayp "Run with %F{cyan}-h%f for help." >&2
  exit 2
fi

# Validate ANDROID_HOME.
if [[ -z "${ANDROID_HOME}" ]] ; then
  say_error "ANDROID_HOME environment variable is not set." >&2
  exit 1
elif [[ ! -d "${ANDROID_HOME}" ]] ; then
  say_error "ANDROID_HOME environment specifies a non-existent directory: ${ANDROID_HOME}" >&2
  exit 1
fi

# Find the emulator command in ANDROID_HOME.
typeset -r emulator_cmd="${ANDROID_HOME}/emulator/emulator"
if [[ ! -e "${emulator_cmd}" ]] ; then
  say_error "file not found: ${emulator_cmd} (ANDROID_HOME=${ANDROID_HOME})" >&2
  exit 1
fi

# Determine the AVD to use, if one was not specified on the command line.
if (( ! ${+emulator_avd} )) ; then
  say "Retrieving list of AVDs configured in the emulator"
  avds=(${(f)"$("${emulator_cmd}" -list-avds)"})
  typeset -r avds
  if (( ${#avds[@]} == 0 )) ; then
    say_error "no AVDs are configured in the Android emulator." >&2
    exit 1
  elif (( ${#avds[@]} > 1 )) ; then
    say_error "${#avds[@]} AVDs are configured in the Android emulator." >&2
    say "Specify the AVD to use as a command-line argument." >&2
    say "Available avds: ${avds[*]}" >&2
    exit 1
  fi
  typeset -r emulator_avd="${avds[1]}"
fi

# Launch the emulator, with flags to minimize system resource usage
# and maximize performance.
typeset -r args=(
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
  "${emulator_extra_args[@]}"
)

say_args "${args[@]}"
exec "${args[@]}" # zshellcheck disable=ZC1909
