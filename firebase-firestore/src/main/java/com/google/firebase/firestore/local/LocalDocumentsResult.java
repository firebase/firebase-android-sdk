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

import static com.google.firebase.firestore.model.DocumentCollections.emptyDocumentMap;

import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import java.util.Map;

/**
 * Represents a set of document along with their mutation batch ID.
 *
 * <p>This class is used when applying mutations to the local store and to propagate document
 * updates to the indexing table.
 */
public final class LocalDocumentsResult {
  private final int batchId;
  private final ImmutableSortedMap<DocumentKey, Document> documents;

  LocalDocumentsResult(int batchId, ImmutableSortedMap<DocumentKey, Document> documents) {
    this.batchId = batchId;
    this.documents = documents;
  }

  public static LocalDocumentsResult fromOverlayedDocuments(
      int batchId, Map<DocumentKey, OverlayedDocument> overlays) {
    ImmutableSortedMap<DocumentKey, Document> documents = emptyDocumentMap();
    for (Map.Entry<DocumentKey, OverlayedDocument> entry : overlays.entrySet()) {
      documents = documents.insert(entry.getKey(), entry.getValue().getDocument());
    }

    return new LocalDocumentsResult(batchId, documents);
  }

  public int getBatchId() {
    return batchId;
  }

  public ImmutableSortedMap<DocumentKey, Document> getDocuments() {
    return documents;
  }
}
