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

import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableSet;
import java.util.PriorityQueue;
import java.util.TreeSet;

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
            return Document.KEY_COMPARATOR.compare(left, right);
          } else {
            return comparison;
          }
        };

    Map<DocumentKey, Document> keyIndex = Collections.emptyMap();
    NavigableSet<Document> sortedSet = new TreeSet<>(adjustedComparator);
    return new DocumentSet(
        Collections.emptyMap(),
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            ? Collections.unmodifiableNavigableSet(sortedSet)
            : sortedSet);
  }

  /**
   * An index of the documents in the DocumentSet, indexed by document key. The index exists to
   * guarantee the uniqueness of document keys in the set and to allow lookup and removal of
   * documents by key.
   */
  private final Map<DocumentKey, Document> keyIndex;

  /**
   * The main collection of documents in the DocumentSet. The documents are ordered by the provided
   * comparator. The collection exists in addition to the index to allow ordered traversal of the
   * DocumentSet.
   */
  private final NavigableSet<Document> sortedSet;

  private DocumentSet(Map<DocumentKey, Document> keyIndex, NavigableSet<Document> sortedSet) {
    if (keyIndex == null) {
      throw new NullPointerException("keyIndex==null");
    }
    if (sortedSet == null) {
      throw new NullPointerException("sortedSet==null");
    }
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
    return sortedSet.isEmpty() ? null : sortedSet.first();
  }

  /**
   * Returns the last document in the set according to the set's ordering, or null if the set is
   * empty.
   */
  @Nullable
  public Document getLastDocument() {
    return sortedSet.isEmpty() ? null : sortedSet.last();
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
    return sortedSet.lower(document);
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
    return sortedSet.headSet(document, false).size();
  }

  /**
   * Returns a copy of the documents in this set as array. This is O(n) in the size of the set TODO:
   * Consider making this backed by the set instead to achieve O(1)?
   */
  public ArrayList<Document> toList() {
    ArrayList<Document> documents = new ArrayList<>(size());
    for (Document document : this) {
      documents.add(document);
    }
    return documents;
  }

  @Override
  @NonNull
  public Iterator<Document> iterator() {
    return Collections.unmodifiableSet(sortedSet).iterator();
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
      result = 31 * result + document.getKey().hashCode();
      result = 31 * result + document.getData().hashCode();
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

  public Builder toBuilder() {
    return new Builder(this);
  }

  public static final class Builder {

    private HashMap<DocumentKey, Document> keyIndex;
    private TreeSet<Document> sortedSet;

    public Builder(DocumentSet documentSet) {
      this.keyIndex = new HashMap<>(documentSet.keyIndex);
      this.sortedSet = new TreeSet<>(documentSet.sortedSet);
    }

    public DocumentSet build() {
      try {
        return new DocumentSet(
            Collections.unmodifiableMap(keyIndex),
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? Collections.unmodifiableNavigableSet(sortedSet)
                : sortedSet);
      } finally {
        // Null out these references to prevent further usages of this builder which would be
        // realized by the returned DocumentSet, which is supposed to be "immutable".
        keyIndex = null;
        sortedSet = null;
      }
    }

    public Builder add(Document document) {
      DocumentKey key = document.getKey();

      Document oldDocument = keyIndex.remove(key);
      if (oldDocument != null) {
        sortedSet.remove(oldDocument);
      }

      keyIndex.put(key, document);
      sortedSet.add(document);

      return this;
    }

    public Builder remove(DocumentKey key) {
      Document oldDocument = keyIndex.remove(key);
      if (oldDocument != null) {
        sortedSet.remove(oldDocument);
      }
      return this;
    }

    public int size() {
      return keyIndex.size();
    }

    public int indexOf(DocumentKey key) {
      Document document = keyIndex.get(key);
      if (document == null) {
        return -1;
      }
      return sortedSet.headSet(document, false).size();
    }

    public PriorityQueue<Document> toPriorityQueue() {
      return toPriorityQueue(this.sortedSet.comparator());
    }

    public PriorityQueue<Document> toReversePriorityQueue() {
      return toPriorityQueue(Collections.reverseOrder(this.sortedSet.comparator()));
    }

    private PriorityQueue<Document> toPriorityQueue(Comparator<? super Document> comparator) {
      int initialCapacity = Math.max(sortedSet.size(), 1);
      PriorityQueue<Document> priorityQueue = new PriorityQueue<>(initialCapacity, comparator);
      priorityQueue.addAll(sortedSet);
      return priorityQueue;
    }
  }
}
