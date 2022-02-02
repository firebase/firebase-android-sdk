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

package com.google.firebase.firestore.model.mutation;

import com.google.auto.value.AutoValue;
import com.google.firebase.firestore.model.DocumentKey;

/**
 * Representation of an overlay computed by Firestore.
 *
 * <p>Holds information about a mutation and the largest batch id in Firestore when the mutation was
 * created.
 */
@AutoValue
public abstract class Overlay {
  public static Overlay create(int largestBatchId, Mutation mutation) {
    return new AutoValue_Overlay(largestBatchId, mutation);
  }

  public abstract int getLargestBatchId();

  public abstract Mutation getMutation();

  public DocumentKey getKey() {
    return getMutation().getKey();
  }
}
