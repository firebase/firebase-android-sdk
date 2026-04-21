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

typeset -r dataconnect_root_dir="${0:A:h:h}"

typeset -r sh_files=(
  ${dataconnect_root_dir}/{emulator,scripts}/*.zsh(N)
)

typeset -r args=(
  "zshellcheck"
  "--severity"
  "style"
  "${sh_files[@]}"
)

print -r -- "${(q)args}"
exec "${args[@]}" # zshellcheck disable=ZC1909
