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

import android.util.Pair;
import androidx.annotation.Nullable;
import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.firestore.core.Target;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldIndex;
import com.google.firebase.firestore.model.FieldIndex.IndexOffset;
import com.google.firebase.firestore.model.ResourcePath;
import java.util.Collection;
import java.util.List;

/**
 * Represents a set of indexes that are used to execute queries efficiently.
 *
 * <p>Currently the only index is a [collection id] => [parent path] index, used to execute
 * Collection Group queries.
 */
public interface IndexManager {

  /** Initializes the IndexManager. */
  void start();

  /**
   * Creates an index entry mapping the collectionId (last segment of the path) to the parent path
   * (either the containing document location or the empty path for root-level collections). Index
   * entries can be retrieved via getCollectionParents().
   *
   * <p>NOTE: Currently we don't remove index entries. If this ends up being an issue we can devise
   * some sort of GC strategy.
   */
  void addToCollectionParentIndex(ResourcePath collectionPath);

  /**
   * Retrieves all parent locations containing the given collectionId, as a set of paths (each path
   * being either a document location or the empty path for a root-level collection).
   */
  List<ResourcePath> getCollectionParents(String collectionId);

  /**
   * Adds a field path index.
   *
   * <p>Values for this index are persisted asynchronously. The index will only be used for query
   * execution once values are persisted.
   */
  void addFieldIndex(FieldIndex index);

  /** Removes the given field index and deletes all index values. */
  void deleteFieldIndex(FieldIndex index);

  /**
   * Returns a list of field indexes that correspond to the specified collection group.
   *
   * @param collectionGroup The collection group to get matching field indexes for.
   * @return A collection of field indexes for the specified collection group.
   */
  Collection<FieldIndex> getFieldIndexes(String collectionGroup);

  /** Returns all configured field indexes. */
  Collection<FieldIndex> getFieldIndexes();

  /**
   * Iterates over all field indexes that are used to serve the given target, and returns the
   * minimum offset of them all. Asserts that the target can be served from index.
   */
  IndexOffset getMinOffset(Target target);

  /** Returns the minimum offset for the given collection group. */
  IndexOffset getMinOffset(String collectionGroup);

  /**
   * Returns an index that can be used to serve the provided target, as well as the number of index
   * segments that matched the target filters and orderBys. Returns {@code null} if no index is
   * configured.
   */
  @Nullable
  Pair<FieldIndex, Integer> getFieldIndexAndSegmentCount(Target target);

  /**
   * Returns a list of documents that match the given target and whether or not a "full index" was
   * used to generate this result. Returns {@code null} if the query cannot be served from an index.
   *
   * <p>Note: if a "full index" does not exist for this target, but a "partial index" can be used to
   * find a superset of the results, it will be used.
   *
   * <p>For example: for querying `a==1 && b==1 LIMIT 10`, if `A ASC, B ASC` index exists (full
   * index), the return value will contain documents that satisfy both `a==1` and `b==1` limited to
   * 10 docs. If, however, we only have `A ASC` index (partial index), the return value will contain
   * all the documents that satisfy `a==1`, but may or may not satisfy `b==1`. In such cases,
   * `LIMIT`s are not applied. It is the caller's responsibility to perform a post-filter in such
   * cases to ensure `b==1` is also satisfied and to limit the results. This mechanism (using a
   * partial index and performing a post-filter) is still a performance improvement compared to full
   * collection scans.
   */
  Pair<List<DocumentKey>, Boolean> getDocumentsMatchingTarget(Target target);

  /** Returns the next collection group to update. Returns {@code null} if no group exists. */
  @Nullable
  String getNextCollectionGroupToUpdate();

  /**
   * Sets the collection group's latest read time.
   *
   * <p>This method updates the index offset for all field indices for the collection group and
   * increments their sequence number. Subsequent calls to {@link #getNextCollectionGroupToUpdate()}
   * will return a different collection group (unless only one collection group is configured).
   */
  void updateCollectionGroup(String collectionGroup, FieldIndex.IndexOffset offset);

  /** Updates the index entries for the provided documents. */
  void updateIndexEntries(ImmutableSortedMap<DocumentKey, Document> documents);
}
