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

import androidx.annotation.NonNull;

/**
 * Represents which field to aggregate on for a {@link AggregateQuery}, and what type of
 * aggregations to perform.
 */
abstract class AggregateField {

  private AggregateField() {}

  /**
   * Returns a {@link CountAggregateField} which counts the number of documents matching the {@code
   * AggregateQuery}.
   */
  @NonNull
  public static CountAggregateField count() {
    return new CountAggregateField();
  }

  static final class CountAggregateField extends AggregateField {
    CountAggregateField() {}
  }
}
