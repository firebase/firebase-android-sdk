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
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.MutableDocument;
import com.google.firebase.firestore.model.SnapshotVersion;
import java.util.Map;

/**
 * Represents cached documents received from the remote backend.
 *
 * <p>The cache is keyed by DocumentKey and entries in the cache are MaybeDocument instances,
 * meaning we can cache both Document instances (an actual document with data) as well as NoDocument
 * instances (indicating that the document is known to not exist).
 */
interface RemoteDocumentCache {

  /** Sets the index manager to use for managing the collectionGroup index. */
  void setIndexManager(IndexManager indexManager);

  /**
   * Adds or replaces an entry in the cache.
   *
   * <p>The cache key is extracted from {@code maybeDocument.key}. If there is already a cache entry
   * for the key, it will be replaced.
   *
   * @param document A document to put in the cache.
   * @param readTime The time at which the document was read or committed.
   */
  void add(MutableDocument document, SnapshotVersion readTime);

  /** Removes the cached entry for the given key (no-op if no entry exists). */
  void remove(DocumentKey documentKey);

  /**
   * Looks up an entry in the cache.
   *
   * @param documentKey The key of the entry to look up.
   * @return The cached document entry, or an invalid document if nothing is cached.
   */
  MutableDocument get(DocumentKey documentKey);

  /**
   * Looks up a set of entries in the cache.
   *
   * @param documentKeys The keys of the entries to look up.
   * @return The cached document entries indexed by key. If an entry is not cached, the
   *     corresponding key will be mapped to an invalid document
   */
  Map<DocumentKey, MutableDocument> getAll(Iterable<DocumentKey> documentKeys);

  /**
   * Executes a query against the cached Document entries
   *
   * <p>Implementations may return extra documents if convenient. The results should be re-filtered
   * by the consumer before presenting them to the user.
   *
   * <p>Cached entries for non-existing documents have no bearing on query results.
   *
   * @param query The query to match documents against.
   * @param sinceReadTime If not set to SnapshotVersion.MIN, return only documents that have been
   *     read since this snapshot version (exclusive).
   * @return The set of matching documents.
   */
  ImmutableSortedMap<DocumentKey, MutableDocument> getAllDocumentsMatchingQuery(
      Query query, SnapshotVersion sinceReadTime);

  /** Returns the latest read time of any document in the cache. */
  SnapshotVersion getLatestReadTime();
}
