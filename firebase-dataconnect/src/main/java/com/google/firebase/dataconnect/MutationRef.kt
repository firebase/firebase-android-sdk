// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.firebase.dataconnect

import java.util.Objects

open class MutationRef
constructor(val revision: String, val operationSet: String, val operationName: String) {

  override fun equals(other: Any?) =
    other is MutationRef &&
      revision == other.revision &&
      operationSet == other.operationSet &&
      operationName == other.operationName

  override fun hashCode() = Objects.hash(revision, operationSet, operationName)

  override fun toString() =
    "MutationRef{revision=$revision, operationSet=$operationSet operationName=$operationName}"
}
