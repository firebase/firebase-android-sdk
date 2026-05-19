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
  exit 1
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

# Determine input format
typeset -r input_format=$([[ "$test_input" == *.* ]] && sayn "FullyQualifiedClassName" || sayn "SimpleClassName")
typeset -r relative_path_suffix=$([[ "$input_format" == "FullyQualifiedClassName" ]] && sayn "${test_input//.//}" || sayn "")

for config in "${test_configurations[@]}"; do
  typeset -a parts
  parts=( ${=config} )
  typeset source_root="${parts[1]}"
  typeset config_submodule="${parts[2]}"
  typeset config_type="${parts[3]}"

  typeset -a dir_matches
  if [[ "$input_format" == "FullyQualifiedClassName" ]]; then
    dir_matches=( ${project_root_dir}/firebase-dataconnect/${source_root}/**/${relative_path_suffix}.kt(N) )
  else
    dir_matches=( ${project_root_dir}/firebase-dataconnect/${source_root}/**/${test_input}.kt(N) )
  fi

  for file in "${dir_matches[@]}"; do
    matches+=( "$file" )
    match_submodules[$file]="$config_submodule"
    match_types[$file]="$config_type"
  done
done

# Handle matches
if [[ ${#matches} == 0 ]]; then
  say_error "Could not find test class matching: ${test_input}" >&2
  exit 1
elif [[ ${#matches} > 1 ]]; then
  {
    say_error "Multiple matching test classes found for: ${test_input}"
    typeset -i i=1
    for match in "${matches[@]}"; do
      say "  $i. ${match#${project_root_dir}/}"
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
else
  typeset package_name=""
  typeset package_line
  package_line=$(grep -m 1 "^[[:space:]]*package[[:space:]]" "$matched_file") || :

  if [[ -n "$package_line" ]]; then
    typeset -a parts
    parts=( ${(z)package_line} )
    package_name="${parts[2]}"
  fi

  if [[ -z "$package_name" ]]; then
    say_error "Could not determine package name from file: ${matched_file}" >&2
    exit 1
  fi
  typeset -r fully_qualified_class="${package_name}.${test_input}"
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
else
  typeset -ra args=(
    "${project_root_dir}/gradlew"
    "-p"
    "${project_root_dir}"
    "--configure-on-demand"
    "${submodule}:connectedDebugAndroidTest"
    "-Pandroid.testInstrumentationRunnerArguments.class=${fully_qualified_class}"
  )
fi

say_args "${args[@]}"
exec "${args[@]}" # zshellcheck disable=ZC1909
