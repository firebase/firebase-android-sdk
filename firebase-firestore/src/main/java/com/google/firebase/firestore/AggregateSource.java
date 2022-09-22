// Copyright 2022 Google LLC
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

package com.google.firebase.firestore;

/** Configures the behavior of {@link AggregateQuery#get}. */
enum AggregateSource {
  /**
   * Reach to the Firestore backend and surface the result verbatim, that is no local documents or
   * mutations in the SDK cache will be included in the surfaced result.
   *
   * <p>Requires client to be online.
   */
  SERVER,
}
