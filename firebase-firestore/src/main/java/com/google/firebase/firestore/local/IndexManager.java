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

import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldIndex;
import com.google.firebase.firestore.model.FieldPath;
import com.google.firebase.firestore.model.ResourcePath;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a set of indexes that are used to execute queries efficiently.
 *
 * <p>Currently the only index is a [collection id] => [parent path] index, used to execute
 * Collection Group queries.
 */
public interface IndexManager {
  class IndexComponent {
    public FieldPath getFieldPath() {
      return fieldPath;
    }

    public void setFieldPath(FieldPath fieldPath) {
      this.fieldPath = fieldPath;
    }

    public IndexType getType() {
      return type;
    }

    public void setType(IndexType type) {
      this.type = type;
    }

    public enum IndexType {
      ASC,
      DESC,
      ANY,
      ARRAY_CONTAINS
    }

    public IndexComponent(FieldPath fieldPath, IndexType type) {
      this.fieldPath = fieldPath;
      this.type = type;
    }

    FieldPath fieldPath;
    IndexType type;
  }

  class IndexDefinition extends ArrayList<IndexComponent> {
    public IndexDefinition popFirst() {
      IndexDefinition clone = new IndexDefinition();
      clone.addAll(subList(1, size()));
      return clone;
    }
  }

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

  void addDocument(Document document);

  void enableIndex(ResourcePath collectionPath, IndexDefinition index);

  Iterable<DocumentKey> getDocumentsMatchingQuery(Query query);

  /**
   * Adds a field path index.
   *
   * <p>Values for this index are persisted asynchronously. The index will only be used for query
   * execution once values are persisted.
   */
  void addFieldIndex(FieldIndex index);
}
