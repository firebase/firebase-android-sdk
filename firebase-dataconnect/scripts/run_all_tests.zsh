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

typeset -r project_root_dir="${0:A:h:h:h}"

typeset -r targets=(
  ":firebase-dataconnect:androidTestutil:connectedDebugAndroidTest"
  ":firebase-dataconnect:androidTestutil:testDebugUnitTest"
  ":firebase-dataconnect:connectedDebugAndroidTest"
  ":firebase-dataconnect:connectors:connectedDebugAndroidTest"
  ":firebase-dataconnect:connectors:testDebugUnitTest"
  ":firebase-dataconnect:testDebugUnitTest"
  ":firebase-dataconnect:testutil:connectedDebugAndroidTest"
  ":firebase-dataconnect:testutil:testDebugUnitTest"
)

typeset -r args=(
  "${project_root_dir}/gradlew"
  "-p"
  "${project_root_dir}"
  "--configure-on-demand"
  "$@"
  "${targets[@]}"
)

print -r -- "${(q)args}"
exec "${args[@]}" # zshellcheck disable=ZC1909
