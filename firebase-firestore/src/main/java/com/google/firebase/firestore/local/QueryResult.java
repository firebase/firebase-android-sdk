// Copyright 2019 Google LLC
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
import com.google.firebase.database.collection.ImmutableSortedSet;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;

/** The result of executing a query against the local store. */
public class QueryResult {
  private final ImmutableSortedMap<DocumentKey, Document> documents;
  private final ImmutableSortedSet<DocumentKey> remoteKeys;

  public QueryResult(
      ImmutableSortedMap<DocumentKey, Document> documents,
      ImmutableSortedSet<DocumentKey> remoteKeys) {
    this.documents = documents;
    this.remoteKeys = remoteKeys;
  }

  public ImmutableSortedMap<DocumentKey, Document> getDocuments() {
    return documents;
  }

  public ImmutableSortedSet<DocumentKey> getRemoteKeys() {
    return remoteKeys;
  }
}
