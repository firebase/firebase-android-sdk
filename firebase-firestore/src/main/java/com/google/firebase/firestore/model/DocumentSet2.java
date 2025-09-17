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

package com.google.firebase.firestore.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * An immutable set of documents (unique by key) ordered by the given comparator or ordered by key
 * by default if no document is present.
 */
public final class DocumentSet2 implements Iterable<Document> {

  private final HashMap<DocumentKey, Document> documentByKey;

  private DocumentSet2(HashMap<DocumentKey, Document> documentByKey) {
    this.documentByKey = documentByKey;
  }

  private static final HashMap<DocumentKey, Document> emptyHashMap = new HashMap<>();

  @NonNull
  public static DocumentSet2 emptySet() {
    return new DocumentSet2(emptyHashMap);
  }

  public int size() {
    return documentByKey.size();
  }

  public boolean isEmpty() {
    return documentByKey.isEmpty();
  }

  /** Returns true iff this set contains a document with the given key. */
  public boolean contains(DocumentKey key) {
    return documentByKey.containsKey(key);
  }

  /** Returns the document from this set with the given key if it exists or null if it doesn't. */
  @Nullable
  public Document getDocument(DocumentKey key) {
    return documentByKey.get(key);
  }

  /**
   * Returns the documents in this set as array.
   * @return a newly-created list containing the documents in this set as array in an undefined
   * order. The caller assumes ownership of this list and is free to do whatever it wants with it.
   */
  @NonNull
  public ArrayList<Document> toArrayList() {
    return new ArrayList<>(documentByKey.values());
  }

  @Override
  @NonNull
  public Iterator<Document> iterator() {
    return Collections.unmodifiableCollection(documentByKey.values()).iterator();
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof DocumentSet2)) {
      return false;
    }
    return documentByKey.equals(((DocumentSet2) other).documentByKey);
  }

  @Override
  public int hashCode() {
    return documentByKey.hashCode();
  }

  @Override
  @NonNull
  public String toString() {
    return documentByKey.toString();
  }

  @NonNull
  public Builder toBuilder() {
    return new Builder(documentByKey);
  }

  @NonNull
  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private HashMap<DocumentKey, Document> documentByKey;

    private Builder() {
      documentByKey = new HashMap<>();
    }

    private Builder(int initialCapacity) {
      documentByKey = new HashMap<>(initialCapacity);
    }

    private Builder(int initialCapacity, float loadFactor) {
      documentByKey = new HashMap<>(initialCapacity, loadFactor);
    }

    private Builder(Map<? extends DocumentKey, ? extends Document> initialData) {
      documentByKey = new HashMap<>(initialData);
    }

    @NonNull
    public DocumentSet2 build() {
      HashMap<DocumentKey, Document> capturedDocumentByKey = documentByKey;
      documentByKey = null;
      return new DocumentSet2(capturedDocumentByKey);
    }

    @NonNull
    public Builder add(Document value) {
      documentByKey.put(value.getKey(), value);
      return this;
    }

    @NonNull
    public Builder remove(DocumentKey key) {
      documentByKey.remove(key);
      return this;
    }
  }
}
