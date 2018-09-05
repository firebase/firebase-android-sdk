// Copyright 2018 Google LLC
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

package com.google.firebase.firestore.local;

/** An enumeration of the different purposes we have for queries. */
public enum QueryPurpose {
  /** A regular, normal query. */
  LISTEN,

  /** The query was used to refill a query after an existence filter mismatch. */
  EXISTENCE_FILTER_MISMATCH,

  /** The query was used to resolve a limbo document. */
  LIMBO_RESOLUTION,
}
