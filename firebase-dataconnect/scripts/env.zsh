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

# This file is designed to be "sourced" into your zsh environment.
# The functions defined below are the real value provided by this script,
# such as "ck" anywhere in the git tree to run compile_kotlin.zsh

###############################################################################
# Function: run_dataconnect_script
#
# A convenience function to run scripts located in the firebase-dataconnect
# scripts directory from anywhere within the git repository.
#
# Arguments:
#   1: The name of the script to run (e.g. compile_kotlin.zsh)
#   Remaining: Arguments to pass to the script.
###############################################################################

run_dataconnect_script() {
  # Use localoptions to ensure that any setopt calls do not affect the
  # user's interactive shell environment.
  setopt localoptions nounset pipefail

  if (( $# < 1 )); then
    builtin print -rPn -- "%F{red}ERROR:%f " >&2
    builtin print -r -- "$0: no script specified" >&2
    builtin print -r -- "For example, $0 compile_kotlin.zsh" >&2
    return 2
  fi

  local script_name="$1"
  shift
  local remaining_args=("$@")

  local current_dir="$PWD"
  local git_dir=""

  # Traverse up the directory tree to find the git root.
  while true; do
    if [[ -e "$current_dir/.git" ]]; then
      git_dir="$current_dir"
      break
    fi

    # Stop if we've reached the root directory.
    if [[ "$current_dir" == "/" ]]; then
      break
    fi

    # Move to the parent directory.
    current_dir="${current_dir:h}"
  done

  if [[ -z "$git_dir" ]]; then
    builtin print -rPn -- "%F{red}ERROR:%f " >&2
    builtin print -r -- "$0: No parent directory containing .git was found starting from ${PWD}" >&2
    return 1
  fi

  # Construct the full path to the target script.
  local target_file="${git_dir}/firebase-dataconnect/scripts/${script_name}"

  # Verify the existence and executability of the target script.
  if [[ ! -e "$target_file" ]]; then
    builtin print -rPn -- "%F{red}ERROR:%f " >&2
    builtin print -r -- "$0: file not found: ${target_file}" >&2
    return 1
  fi

  # Execute the script with the remaining arguments.
  typeset -r args=("${target_file}" "${remaining_args[@]}")
  builtin print -r -- "${(q)args}"
  "${args[@]}"
}

ck() {
  run_dataconnect_script compile_kotlin.zsh "$@"
}

rat() {
  run_dataconnect_script run_all_tests.zsh "$@"
}

rit() {
  run_dataconnect_script run_integration_tests.zsh "$@"
}

rut() {
  run_dataconnect_script run_unit_tests.zsh "$@"
}

sp() {
  run_dataconnect_script spotlessApply.zsh "$@"
}
