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

import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.MaybeDocument;

/** The result of a write to the local store. */
public final class LocalWriteResult {
  private final int batchId;

  private final ImmutableSortedMap<DocumentKey, MaybeDocument> changes;

  LocalWriteResult(int batchId, ImmutableSortedMap<DocumentKey, MaybeDocument> changes) {
    this.batchId = batchId;
    this.changes = changes;
  }

  public int getBatchId() {
    return batchId;
  }

  public ImmutableSortedMap<DocumentKey, MaybeDocument> getChanges() {
    return changes;
  }
}
