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

import static com.google.firebase.firestore.model.DocumentCollections.emptyMaybeDocumentMap;
import static com.google.firebase.firestore.util.Assert.hardAssert;

import androidx.annotation.Nullable;
import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.database.collection.ImmutableSortedSet;
import com.google.firebase.firestore.LoadBundleTaskProgress;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.MaybeDocument;
import com.google.firebase.firestore.model.NoDocument;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A class to process the elements from a bundle, load them into local storage and provide progress
 * update while loading.
 */
public class BundleLoader {
  private final BundleListener bundleListener;
  private final BundleMetadata bundleMetadata;
  private final int totalDocuments;
  private final long totalBytes;
  private final List<NamedQuery> queries;
  private final Map<DocumentKey, BundledDocumentMetadata> documentsMetadata;

  private ImmutableSortedMap<DocumentKey, MaybeDocument> documents;
  private long bytesLoaded;
  private LoadBundleTaskProgress.TaskState taskState;
  @Nullable private DocumentKey currentDocument;

  public BundleLoader(
      BundleListener bundleListener,
      BundleMetadata bundleMetadata,
      int totalDocuments,
      long totalBytes) {
    this.bundleListener = bundleListener;
    this.bundleMetadata = bundleMetadata;
    this.totalDocuments = totalDocuments;
    this.totalBytes = totalBytes;
    this.queries = new ArrayList<>();
    this.documents = emptyMaybeDocumentMap();
    this.documentsMetadata = new HashMap<>();
    this.taskState = LoadBundleTaskProgress.TaskState.RUNNING;
  }

  /**
   * Adds an element from the bundle to the loader.
   *
   * <p>Returns a new progress if adding the element leads to a new progress, otherwise returns
   * null.
   */
  public @Nullable LoadBundleTaskProgress addElement(BundleElement bundleElement, long byteSize) {
    if (bundleElement instanceof BundleMetadata) {
      return fail("Unexpected bundle metadata  element.");
    }

    boolean updateProgress = false;

    if (bundleElement instanceof NamedQuery) {
      queries.add((NamedQuery) bundleElement);
    } else if (bundleElement instanceof BundledDocumentMetadata) {
      BundledDocumentMetadata bundledDocumentMetadata = (BundledDocumentMetadata) bundleElement;
      documentsMetadata.put(bundledDocumentMetadata.getKey(), bundledDocumentMetadata);
      currentDocument = bundledDocumentMetadata.getKey();
      if (!((BundledDocumentMetadata) bundleElement).exists()) {
        documents =
            documents.insert(
                bundledDocumentMetadata.getKey(),
                new NoDocument(
                    bundledDocumentMetadata.getKey(),
                    bundledDocumentMetadata.getReadTime(),
                    /* hasCommittedMutations= */ false));
        updateProgress = true;
        currentDocument = null;
      }
    } else if (bundleElement instanceof BundleDocument) {
      BundleDocument bundleDocument = (BundleDocument) bundleElement;
      if (!bundleDocument.getKey().equals(currentDocument)) {
        return fail("The document being added does not match the stored metadata.");
      }
      documents = documents.insert(bundleDocument.getKey(), bundleDocument.getDocument());
      updateProgress = true;
      currentDocument = null;
    }

    bytesLoaded += byteSize;

    if (bytesLoaded == totalBytes) {
      if (documents.size() != totalDocuments) {
        return fail(
            String.format(
                "Expected %s documents, but loaded %s.", totalDocuments, documents.size()));
      }
      if (currentDocument != null) {
        return fail(
            "Bundled documents end with a document metadata element instead of a document.");
      }
      if (bundleMetadata.getBundleId() == null) {
        return fail("Bundle ID must be set");
      }

      taskState = LoadBundleTaskProgress.TaskState.SUCCESS;
      updateProgress = true;
    }

    return updateProgress
        ? new LoadBundleTaskProgress(
            documents.size(), totalDocuments, bytesLoaded, totalBytes, null, taskState)
        : null;
  }

  private LoadBundleTaskProgress fail(String error) {
    taskState = LoadBundleTaskProgress.TaskState.ERROR;
    return new LoadBundleTaskProgress(
        documents.size(),
        totalDocuments,
        bytesLoaded,
        totalBytes,
        new IllegalArgumentException(error),
        LoadBundleTaskProgress.TaskState.ERROR);
  }

  /** Applies the loaded documents and queries to local store. Returns the document view changes. */
  public ImmutableSortedMap<DocumentKey, MaybeDocument> applyChanges() {
    hardAssert(
        taskState.equals(LoadBundleTaskProgress.TaskState.SUCCESS),
        "Expected successful task, but was " + taskState);
    ImmutableSortedMap<DocumentKey, MaybeDocument> changes =
        bundleListener.applyBundledDocuments(documents, bundleMetadata.getBundleId());

    Map<String, ImmutableSortedSet<DocumentKey>> queryDocumentMap = getQueryDocumentMapping();
    for (NamedQuery namedQuery : queries) {
      bundleListener.saveNamedQuery(namedQuery, queryDocumentMap.get(namedQuery.getName()));
    }

    bundleListener.saveBundle(bundleMetadata);

    return changes;
  }

  private Map<String, ImmutableSortedSet<DocumentKey>> getQueryDocumentMapping() {
    Map<String, ImmutableSortedSet<DocumentKey>> queryDocumentMap = new HashMap<>();
    for (BundledDocumentMetadata metadata : documentsMetadata.values()) {
      for (String query : metadata.getQueries()) {
        ImmutableSortedSet<DocumentKey> matchingKeys = queryDocumentMap.get(query);
        if (matchingKeys == null) {
          matchingKeys = DocumentKey.emptyKeySet();
        }
        queryDocumentMap.put(query, matchingKeys.insert(metadata.getKey()));
      }
    }

    return queryDocumentMap;
  }
}
