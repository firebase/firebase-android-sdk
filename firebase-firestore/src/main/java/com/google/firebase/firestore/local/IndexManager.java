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

import androidx.annotation.Nullable;
import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.firestore.core.Target;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldIndex;
import com.google.firebase.firestore.model.ResourcePath;
import java.util.Collection;
import java.util.List;
import java.util.Set;

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
   * Returns an index that can be used to serve the provided target. Returns {@code null} if no
   * index is configured.
   */
  @Nullable
  FieldIndex getFieldIndex(Target target);

  /** Returns the documents that match the given target based on the provided index. */
  Set<DocumentKey> getDocumentsMatchingTarget(FieldIndex fieldIndex, Target target);

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
