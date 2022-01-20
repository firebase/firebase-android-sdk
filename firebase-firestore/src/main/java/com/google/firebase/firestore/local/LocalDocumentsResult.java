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
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;

/** The result of a applying local mutations. */
public final class LocalDocumentsResult {
  private final int largestBatchId;
  private final ImmutableSortedMap<DocumentKey, Document> documents;

  LocalDocumentsResult(int largestBatchId, ImmutableSortedMap<DocumentKey, Document> documents) {
    this.largestBatchId = largestBatchId;
    this.documents = documents;
  }

  public int getLargestBatchId() {
    return largestBatchId;
  }

  public ImmutableSortedMap<DocumentKey, Document> getDocuments() {
    return documents;
  }
}
