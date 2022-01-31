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

import static com.google.firebase.firestore.model.DocumentCollections.emptyMutableDocumentMap;

import androidx.annotation.Nullable;
import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.database.collection.ImmutableSortedSet;
import com.google.firebase.firestore.LoadBundleTaskProgress;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.MutableDocument;
import com.google.firebase.firestore.util.Preconditions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A class to process the elements from a bundle, load them into local storage and provide progress
 * update while loading.
 */
public class BundleLoader {
  private final BundleCallback bundleCallback;
  private final BundleMetadata bundleMetadata;
  private final List<NamedQuery> queries;
  private final Map<DocumentKey, BundledDocumentMetadata> documentsMetadata;

  private ImmutableSortedMap<DocumentKey, MutableDocument> documents;
  private long bytesLoaded;
  @Nullable private BundledDocumentMetadata currentMetadata;

  public BundleLoader(BundleCallback bundleCallback, BundleMetadata bundleMetadata) {
    this.bundleCallback = bundleCallback;
    this.bundleMetadata = bundleMetadata;
    this.queries = new ArrayList<>();
    this.documents = emptyMutableDocumentMap();
    this.documentsMetadata = new HashMap<>();
  }

  /**
   * Adds an element from the bundle to the loader.
   *
   * <p>Returns a new progress if adding the element leads to a new progress, otherwise returns
   * null.
   */
  public @Nullable LoadBundleTaskProgress addElement(BundleElement bundleElement, long byteSize) {
    Preconditions.checkArgument(
        !(bundleElement instanceof BundleMetadata), "Unexpected bundle metadata element.");

    int beforeDocumentCount = documents.size();

    if (bundleElement instanceof NamedQuery) {
      queries.add((NamedQuery) bundleElement);
    } else if (bundleElement instanceof BundledDocumentMetadata) {
      BundledDocumentMetadata bundledDocumentMetadata = (BundledDocumentMetadata) bundleElement;
      documentsMetadata.put(bundledDocumentMetadata.getKey(), bundledDocumentMetadata);
      currentMetadata = bundledDocumentMetadata;
      if (!((BundledDocumentMetadata) bundleElement).exists()) {
        documents =
            documents.insert(
                bundledDocumentMetadata.getKey(),
                MutableDocument.newNoDocument(
                        bundledDocumentMetadata.getKey(), bundledDocumentMetadata.getReadTime())
                    .setReadTime(bundledDocumentMetadata.getReadTime()));
        currentMetadata = null;
      }
    } else if (bundleElement instanceof BundleDocument) {
      BundleDocument bundleDocument = (BundleDocument) bundleElement;
      if (currentMetadata == null || !bundleDocument.getKey().equals(currentMetadata.getKey())) {
        throw new IllegalArgumentException(
            "The document being added does not match the stored metadata.");
      }
      documents =
          documents.insert(
              bundleDocument.getKey(),
              bundleDocument.getDocument().setReadTime(currentMetadata.getReadTime()));
      currentMetadata = null;
    }

    bytesLoaded += byteSize;

    return beforeDocumentCount != documents.size()
        ? new LoadBundleTaskProgress(
            documents.size(),
            bundleMetadata.getTotalDocuments(),
            bytesLoaded,
            bundleMetadata.getTotalBytes(),
            null,
            LoadBundleTaskProgress.TaskState.RUNNING)
        : null;
  }

  /** Applies the loaded documents and queries to local store. Returns the document view changes. */
  public ImmutableSortedMap<DocumentKey, Document> applyChanges() {
    Preconditions.checkArgument(
        currentMetadata == null,
        "Bundled documents end with a document metadata element instead of a document.");
    Preconditions.checkArgument(bundleMetadata.getBundleId() != null, "Bundle ID must be set");
    Preconditions.checkArgument(
        documents.size() == bundleMetadata.getTotalDocuments(),
        "Expected %s documents, but loaded %s.",
        bundleMetadata.getTotalDocuments(),
        documents.size());

    ImmutableSortedMap<DocumentKey, Document> changes =
        bundleCallback.applyBundledDocuments(documents, bundleMetadata.getBundleId());

    Map<String, ImmutableSortedSet<DocumentKey>> queryDocumentMap = getQueryDocumentMapping();
    for (NamedQuery namedQuery : queries) {
      bundleCallback.saveNamedQuery(namedQuery, queryDocumentMap.get(namedQuery.getName()));
    }

    bundleCallback.saveBundle(bundleMetadata);

    return changes;
  }

  private Map<String, ImmutableSortedSet<DocumentKey>> getQueryDocumentMapping() {
    Map<String, ImmutableSortedSet<DocumentKey>> queryDocumentMap = new HashMap<>();
    for (NamedQuery namedQuery : queries) {
      queryDocumentMap.put(namedQuery.getName(), DocumentKey.emptyKeySet());
    }
    for (BundledDocumentMetadata metadata : documentsMetadata.values()) {
      for (String query : metadata.getQueries()) {
        ImmutableSortedSet<DocumentKey> matchingKeys = queryDocumentMap.get(query);
        queryDocumentMap.put(query, matchingKeys.insert(metadata.getKey()));
      }
    }

    return queryDocumentMap;
  }
}
