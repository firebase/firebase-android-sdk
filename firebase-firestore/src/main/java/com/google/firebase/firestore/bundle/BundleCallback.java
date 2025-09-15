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

package com.google.firebase.firestore.bundle;

import com.google.firebase.database.collection.ImmutableHashMap;
import com.google.firebase.database.collection.ImmutableHashSet;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.MutableDocument;
import java.util.HashMap;

/** Interface implemented by components that can apply changes from a bundle to local storage. */
public interface BundleCallback {
  /**
   * Applies the documents from a bundle to the "ground-state" (remote) documents.
   *
   * <p>LocalDocuments are re-calculated if there are remaining mutations in the queue.
   *
   * @return a newly created {@link HashMap} with the applied documents.
   */
  HashMap<DocumentKey, Document> applyBundledDocuments(
      ImmutableHashMap<DocumentKey, MutableDocument> documents, String bundleId);

  /** Saves the given NamedQuery to local persistence. */
  void saveNamedQuery(NamedQuery namedQuery, ImmutableHashSet<DocumentKey> documentKeys);

  /** Saves the given BundleMetadata to local persistence. */
  void saveBundle(BundleMetadata bundleMetadata);
}
