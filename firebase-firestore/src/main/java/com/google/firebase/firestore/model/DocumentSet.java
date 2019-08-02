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

import static com.google.firebase.firestore.model.DocumentCollections.emptyDocumentMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.database.collection.ImmutableSortedSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * An immutable set of documents (unique by key) ordered by the given comparator or ordered by key
 * by default if no document is present.
 */
public final class DocumentSet implements Iterable<Document> {

  /** Returns an empty DocumentSet sorted by the given comparator, then by keys. */
  public static DocumentSet emptySet(final Comparator<Document> comparator) {
    // We have to add the document key comparator to the passed in comparator, as it's the only
    // guaranteed unique property of a document.
    Comparator<Document> adjustedComparator =
        (left, right) -> {
          int comparison = comparator.compare(left, right);
          if (comparison == 0) {
            return Document.keyComparator().compare(left, right);
          } else {
            return comparison;
          }
        };

    return new DocumentSet(
        emptyDocumentMap(), new ImmutableSortedSet<>(Collections.emptyList(), adjustedComparator));
  }

  /**
   * An index of the documents in the DocumentSet, indexed by document key. The index exists to
   * guarantee the uniqueness of document keys in the set and to allow lookup and removal of
   * documents by key.
   */
  private final ImmutableSortedMap<DocumentKey, Document> keyIndex;

  /**
   * The main collection of documents in the DocumentSet. The documents are ordered by the provided
   * comparator. The collection exists in addition to the index to allow ordered traversal of the
   * DocumentSet.
   */
  private final ImmutableSortedSet<Document> sortedSet;

  private DocumentSet(
      ImmutableSortedMap<DocumentKey, Document> keyIndex, ImmutableSortedSet<Document> sortedSet) {
    this.keyIndex = keyIndex;
    this.sortedSet = sortedSet;
  }

  public int size() {
    return keyIndex.size();
  }

  public boolean isEmpty() {
    return keyIndex.isEmpty();
  }

  /** Returns true iff this set contains a document with the given key. */
  public boolean contains(DocumentKey key) {
    return keyIndex.containsKey(key);
  }

  /** Returns the document from this set with the given key if it exists or null if it doesn't. */
  @Nullable
  public Document getDocument(DocumentKey key) {
    return keyIndex.get(key);
  }

  /**
   * Returns the first document in the set according to the set's ordering, or null if the set is
   * empty.
   */
  @Nullable
  public Document getFirstDocument() {
    return sortedSet.getMinEntry();
  }

  /**
   * Returns the last document in the set according to the set's ordering, or null if the set is
   * empty.
   */
  @Nullable
  public Document getLastDocument() {
    return sortedSet.getMaxEntry();
  }

  /**
   * Returns the document previous to the document associated with the given key in the set
   * according to the set's ordering. Returns null if the document associated with the given key is
   * the first document.
   *
   * @param key A key that must be present in the set
   * @throws IllegalArgumentException if the set does not contain the key
   */
  @Nullable
  public Document getPredecessor(DocumentKey key) {
    Document document = keyIndex.get(key);
    if (document == null) {
      throw new IllegalArgumentException("Key not contained in DocumentSet: " + key);
    }
    return sortedSet.getPredecessorEntry(document);
  }

  /**
   * Returns the index of the provided key in the document set, or -1 if the document key is not
   * present in the set;
   */
  public int indexOf(DocumentKey key) {
    Document document = keyIndex.get(key);
    if (document == null) {
      return -1;
    }
    return sortedSet.indexOf(document);
  }

  /**
   * Returns a new DocumentSet that contains the given document, replacing any old document with the
   * same key.
   */
  public DocumentSet add(Document document) {
    // Remove any prior mapping of the document's key before adding, preventing sortedSet from
    // accumulating values that aren't in the index.
    DocumentSet removed = remove(document.getKey());

    ImmutableSortedMap<DocumentKey, Document> newKeyIndex =
        removed.keyIndex.insert(document.getKey(), document);
    ImmutableSortedSet<Document> newSortedSet = removed.sortedSet.insert(document);
    return new DocumentSet(newKeyIndex, newSortedSet);
  }

  /** Returns a new DocumentSet with the document for the provided key removed. */
  public DocumentSet remove(DocumentKey key) {
    Document document = keyIndex.get(key);
    if (document == null) {
      return this;
    }

    ImmutableSortedMap<DocumentKey, Document> newKeyIndex = keyIndex.remove(key);
    ImmutableSortedSet<Document> newSortedSet = sortedSet.remove(document);
    return new DocumentSet(newKeyIndex, newSortedSet);
  }

  /**
   * Returns a copy of the documents in this set as array. This is O(n) in the size of the set TODO:
   * Consider making this backed by the set instead to achieve O(1)?
   */
  public List<Document> toList() {
    List<Document> documents = new ArrayList<>(size());
    for (Document document : this) {
      documents.add(document);
    }
    return documents;
  }

  @Override
  @NonNull
  public Iterator<Document> iterator() {
    return sortedSet.iterator();
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }

    DocumentSet documentSet = (DocumentSet) other;

    if (size() != documentSet.size()) {
      return false;
    }

    Iterator<Document> thisIterator = iterator();
    Iterator<Document> otherIterator = documentSet.iterator();
    while (thisIterator.hasNext()) {
      Document thisDoc = thisIterator.next();
      Document otherDoc = otherIterator.next();
      if (!thisDoc.equals(otherDoc)) {
        return false;
      }
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result = 0;
    for (Document document : this) {
      result = 31 * result + document.hashCode();
    }
    return result;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder("[");
    boolean first = true;
    for (Document doc : this) {
      if (first) {
        first = false;
      } else {
        builder.append(", ");
      }
      builder.append(doc);
    }
    builder.append("]");
    return builder.toString();
  }
}
