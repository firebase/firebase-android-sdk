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

setopt errexit nounset pipefail extendedglob

source "${0:A:h}/util/say.zsh"

typeset -r project_root_dir="${0:A:h:h:h}"

if (( # != 1 )); then
  {
    say_error "Expected exactly 1 command-line argument, but got $#: $*"
    say "Usage: ${0:t} <test-class-name>"
    say "Example 1: ${0:t} RealtimeQuerySubscriptionImplUnitTest"
    say "Example 2: ${0:t} com.google.firebase.dataconnect.core.RealtimeQuerySubscriptionImplUnitTest"
  } >&2
  exit 2
fi

typeset -r test_input="$1"

# Data structure linking source roots to their submodule and test type.
# Format: "source_root  submodule  test_type"
typeset -ra test_configurations=(
  "src/test                          :firebase-dataconnect                   unit"
  "src/androidTest                   :firebase-dataconnect                   integration"
  "androidTestutil/src/test          :firebase-dataconnect:androidTestutil   unit"
  "androidTestutil/src/androidTest   :firebase-dataconnect:androidTestutil   integration"
  "connectors/src/test               :firebase-dataconnect:connectors        unit"
  "connectors/src/androidTest        :firebase-dataconnect:connectors        integration"
  "testutil/src/test                 :firebase-dataconnect:testutil          unit"
  "testutil/src/androidTest          :firebase-dataconnect:testutil          integration"
)

typeset -a matches
typeset -A match_submodules
typeset -A match_types

###############################################################################
# Function: search_for_test_class
#
# Searches for a test class name pattern across all configured test source
# directories.
#
# Arguments:
#   1: The search pattern. This should be a simple class name (e.g. "MyTest")
#      or a path-like suffix (e.g. "com/google/firebase/dataconnect/MyTest")
#      without the ".kt" extension.
#
# Outputs:
#   This function does not write to stdout.
#
# Results / Side Effects:
#   The results of the search are appended to the following global variables:
#     - matches: An array of absolute file paths to the matching test files.
#     - match_submodules: An associative array mapping each matched file path
#       to its corresponding Gradle submodule.
#     - match_types: An associative array mapping each matched file path
#       to its test type ("unit" or "integration").
#
# Returns:
#   0 if the search completed successfully (even if no matches were found).
#   2 if the arguments to the function were invalid.
###############################################################################
search_for_test_class() {
  if (( # != 1 )); then
    say_error "$0: expected exactly 1 argument, but got $#: $*" >&2
    return 2
  fi

  typeset -r search_pattern="$1"
  local config
  local -a parts
  local source_root
  local gradle_submodule
  local test_type
  local -a dir_matches
  local file

  for config in "${test_configurations[@]}"; do
    parts=( ${=config} )
    source_root="${parts[1]}"
    gradle_submodule="${parts[2]}"
    test_type="${parts[3]}"

    dir_matches=( ${project_root_dir}/firebase-dataconnect/${source_root}/**/${search_pattern}.kt(N) )

    for file in "${dir_matches[@]}"; do
      matches+=( "$file" )
      match_submodules[$file]="$gradle_submodule"
      match_types[$file]="$test_type"
    done
  done
}

###############################################################################
# Function: find_package_line
#
# Reads a file and finds the first line that declares a Kotlin package name.
#
# Arguments:
#   1: The absolute path of the file to read.
#
# Outputs:
#   Prints the matching package line to stdout if found.
#
# Returns:
#   0 if the package line was successfully found and printed.
#   1 if no package line was found in the file.
#   2 if the arguments to the function were invalid.
###############################################################################
find_package_line() {
  if (( # != 1 )); then
    say_error "$0: expected exactly 1 argument, but got $#: $*" >&2
    return 2
  fi

  local -r file_path="$1"
  local line
  local IFS

  # Explicitly clear IFS for 'read' to preserve exact line contents
  while IFS= read -r line; do
    # Relies on 'extendedglob' (already set at the top of the script)
    if [[ "$line" == [[:space:]]#package[[:space:]]##* ]]; then
      say "$line"
      return 0
    fi
  done < "$file_path"

  return 1
}

# Determine input format and search pattern
if [[ "$test_input" == *.* ]]; then
  typeset -r input_format="FullyQualifiedClassName"
  typeset -r search_pattern="${test_input//.//}" # Replace "." with "/"
else
  typeset -r input_format="SimpleClassName"
  typeset -r search_pattern="$test_input"
fi

search_for_test_class "$search_pattern"

# If simple class name not found and lacks "Test" suffix, try fallback with UnitTest and IntegrationTest
if [[ "$input_format" == "SimpleClassName" && ${#matches} == 0 && "$test_input" != *Test ]]; then
  search_for_test_class "${test_input}UnitTest"
  search_for_test_class "${test_input}IntegrationTest"
fi

# Handle matches
if [[ ${#matches} == 0 ]]; then
  say_error "Could not find test class matching: ${test_input}" >&2
  exit 1
elif [[ ${#matches} > 1 ]]; then
  {
    say_error "Multiple matching test classes found for: ${test_input}"
    typeset -i i=1
    for match in "${matches[@]}"; do
      say "  $i. ${match:t:r} (${match#${project_root_dir}/})"
      ((i++))
    done
  } >&2
  exit 1
fi

typeset -r matched_file="${matches[1]}"
typeset -r submodule="${match_submodules[$matched_file]}"
typeset -r test_type="${match_types[$matched_file]}"

# Extract or assign fully-qualified class name
if [[ "$input_format" == "FullyQualifiedClassName" ]]; then
  typeset -r fully_qualified_class="${test_input}"
elif [[ "$input_format" == "SimpleClassName" ]]; then
  typeset package_name=""
  typeset package_line
  package_line=$(find_package_line "$matched_file") || :

  if [[ -n "$package_line" ]]; then
    typeset -a parts
    parts=( ${(z)package_line} )
    package_name="${parts[2]}"
  fi

  if [[ -z "$package_name" ]]; then
    say_error "Could not determine package name from file: ${matched_file}" >&2
    exit 1
  fi
  typeset -r fully_qualified_class="${package_name}.${matched_file:t:r}"
else
  say_error "INTERNAL ERROR: unsupported value for input_format: $input_format" >&2
  exit 1
fi

# Construct gradle arguments
if [[ "$test_type" == "unit" ]]; then
  typeset -ra args=(
    "${project_root_dir}/gradlew"
    "-p"
    "${project_root_dir}"
    "--configure-on-demand"
    "${submodule}:testDebugUnitTest"
    "--tests"
    "${fully_qualified_class}"
  )
elif [[ "$test_type" == "integration" ]]; then
  typeset -ra args=(
    "${project_root_dir}/gradlew"
    "-p"
    "${project_root_dir}"
    "--configure-on-demand"
    "${submodule}:connectedDebugAndroidTest"
    "-Pandroid.testInstrumentationRunnerArguments.class=${fully_qualified_class}"
  )
else
  say_error "INTERNAL ERROR: unsupported value for test_type: $test_type" >&2
  exit 1
fi

say_args "${args[@]}"
exec "${args[@]}" # zshellcheck disable=ZC1909
