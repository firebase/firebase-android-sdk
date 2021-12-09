// Copyright 2021 Google LLC
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

/**
 * Representation of an overlay computed by Firestore.
 *
 * <p>Holds information about a mutation and the largest batch id in Firestore when the mutation was
 * created.
 */
public class Overlay {
  private final int largestBatchId;
  private final Mutation mutation;

  public Overlay(int largestBatchId, Mutation mutation) {
    this.largestBatchId = largestBatchId;
    this.mutation = mutation;
  }

  public int getLargestBatchId() {
    return largestBatchId;
  }

  public Mutation getMutation() {
    return mutation;
  }
}
