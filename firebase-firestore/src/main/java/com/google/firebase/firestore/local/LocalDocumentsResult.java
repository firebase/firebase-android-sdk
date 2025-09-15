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

import com.google.firebase.database.collection.ImmutableHashMap;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a set of document along with their mutation batch ID.
 *
 * <p>This class is used when applying mutations to the local store and to propagate document
 * updates to the indexing table.
 */
public final class LocalDocumentsResult {
  private final int batchId;
  private final ImmutableHashMap<DocumentKey, Document> documents;

  LocalDocumentsResult(int batchId, ImmutableHashMap<DocumentKey, Document> documents) {
    this.batchId = batchId;
    this.documents = documents;
  }

  public static LocalDocumentsResult fromOverlayedDocuments(
      int batchId, Map<DocumentKey, OverlayedDocument> overlays) {
    HashMap<DocumentKey, Document> documents = new HashMap<>();
    for (Map.Entry<DocumentKey, OverlayedDocument> entry : overlays.entrySet()) {
      documents.put(entry.getKey(), entry.getValue().getDocument());
    }

    return new LocalDocumentsResult(batchId, ImmutableHashMap.withDelegateMap(documents));
  }

  public int getBatchId() {
    return batchId;
  }

  public ImmutableHashMap<DocumentKey, Document> getDocuments() {
    return documents;
  }
}
