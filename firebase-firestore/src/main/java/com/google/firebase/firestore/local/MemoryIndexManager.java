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

import static com.google.firebase.firestore.util.Assert.hardAssert;

import androidx.annotation.Nullable;
import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.firestore.core.Target;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldIndex;
import com.google.firebase.firestore.model.ResourcePath;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** An in-memory implementation of IndexManager. */
class MemoryIndexManager implements IndexManager {
  private final MemoryCollectionParentIndex collectionParentsIndex =
      new MemoryCollectionParentIndex();

  public MemoryIndexManager() {}

  @Override
  public void start() {}

  @Override
  public void addToCollectionParentIndex(ResourcePath collectionPath) {
    collectionParentsIndex.add(collectionPath);
  }

  @Override
  public List<ResourcePath> getCollectionParents(String collectionId) {
    return collectionParentsIndex.getEntries(collectionId);
  }

  @Override
  public void addFieldIndex(FieldIndex index) {
    // Field indices are not supported with memory persistence.
  }

  @Override
  public void deleteFieldIndex(FieldIndex index) {
    // Field indices are not supported with memory persistence.
  }

  @Nullable
  @Override
  public FieldIndex getFieldIndex(Target target) {
    // Field indices are not supported with memory persistence.
    return null;
  }

  @Override
  @Nullable
  public Set<DocumentKey> getDocumentsMatchingTarget(FieldIndex fieldIndex, Target target) {
    // Field indices are not supported with memory persistence.
    return Collections.emptySet();
  }

  @Nullable
  @Override
  public String getNextCollectionGroupToUpdate() {
    // Field indices are not supported with memory persistence.
    return null;
  }

  @Override
  public void updateCollectionGroup(String collectionGroup, FieldIndex.IndexOffset offset) {
    // Field indices are not supported with memory persistence.
  }

  @Override
  public Collection<FieldIndex> getFieldIndexes(String collectionGroup) {
    // Field indices are not supported with memory persistence.
    return Collections.emptyList();
  }

  @Override
  public Collection<FieldIndex> getFieldIndexes() {
    // Field indices are not supported with memory persistence.
    return Collections.emptyList();
  }

  @Override
  public void updateIndexEntries(ImmutableSortedMap<DocumentKey, Document> documents) {
    // Field indices are not supported with memory persistence.
  }

  /**
   * Internal implementation of the collection-parent index. Also used for in-memory caching by
   * SQLiteIndexManager and initial index population in SQLiteSchema.
   */
  static class MemoryCollectionParentIndex {
    private final HashMap<String, HashSet<ResourcePath>> index = new HashMap<>();

    // Returns false if the entry already existed.
    boolean add(ResourcePath collectionPath) {
      hardAssert(collectionPath.length() % 2 == 1, "Expected a collection path.");

      String collectionId = collectionPath.getLastSegment();
      ResourcePath parentPath = collectionPath.popLast();
      HashSet<ResourcePath> existingParents = index.get(collectionId);
      if (existingParents == null) {
        existingParents = new HashSet<>();
        index.put(collectionId, existingParents);
      }
      return existingParents.add(parentPath);
    }

    List<ResourcePath> getEntries(String collectionId) {
      HashSet<ResourcePath> existingParents = index.get(collectionId);
      return existingParents != null ? new ArrayList<>(existingParents) : Collections.emptyList();
    }
  }
}
