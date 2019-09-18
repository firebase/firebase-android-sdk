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
import com.google.firebase.database.collection.ImmutableSortedSet;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.MaybeDocument;
import com.google.firebase.firestore.model.SnapshotVersion;

/**
 * Represents a query engine capable of performing queries over the local document cache. You must
 * call setLocalDocumentsView() before using.
 */
public interface QueryEngine {

  /** Sets the document view to query against. */
  void setLocalDocumentsView(LocalDocumentsView localDocuments);

  /** Returns all local documents matching the specified query. */
  ImmutableSortedMap<DocumentKey, Document> getDocumentsMatchingQuery(
      Query query,
      SnapshotVersion lastLimboFreeSnapshotVersion,
      ImmutableSortedSet<DocumentKey> remoteKeys);

  /**
   * Notifies the query engine of a document change in case it would like to update indexes and the
   * like.
   *
   * <p>TODO: We can change this to just accept the changed fields (w/ old and new values) if it's
   * convenient for the caller to compute.
   */
  void handleDocumentChange(MaybeDocument oldDocument, MaybeDocument newDocument);
}
