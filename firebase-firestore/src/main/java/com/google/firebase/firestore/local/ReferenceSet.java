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

import static java.util.Collections.emptyList;

import com.google.firebase.database.collection.ImmutableSortedSet;
import com.google.firebase.firestore.model.DocumentKey;
import java.util.Iterator;

/**
 * A collection of references to a document from some kind of numbered entity (either a target ID or
 * batch ID). As references are added to or removed from the set corresponding events are emitted to
 * a registered garbage collector.
 *
 * <p>Each reference is represented by a DocumentReference object. Each of them contains enough
 * information to uniquely identify the reference. They are all stored primarily in a set sorted by
 * key. A document is considered garbage if there's no references in that set (this can be
 * efficiently checked thanks to sorting by key).
 *
 * <p>ReferenceSet also keeps a secondary set that contains references sorted by IDs. This one is
 * used to efficiently implement removal of all references by some target ID.
 */
public class ReferenceSet {

  /** A set of outstanding references to a document sorted by key. */
  private ImmutableSortedSet<DocumentReference> referencesByKey;

  /** A set of outstanding references to a document sorted by target ID (or batch ID). */
  private ImmutableSortedSet<DocumentReference> referencesByTarget;

  public ReferenceSet() {
    referencesByKey = new ImmutableSortedSet<>(emptyList(), DocumentReference.BY_KEY);
    referencesByTarget = new ImmutableSortedSet<>(emptyList(), DocumentReference.BY_TARGET);
  }

  /** Returns true if the reference set contains no references. */
  public boolean isEmpty() {
    return referencesByKey.isEmpty();
  }

  /** Adds a reference to the given document key for the given ID. */
  public void addReference(DocumentKey key, int targetOrBatchId) {
    DocumentReference ref = new DocumentReference(key, targetOrBatchId);
    referencesByKey = referencesByKey.insert(ref);
    referencesByTarget = referencesByTarget.insert(ref);
  }

  /** Add references to the given document keys for the given ID. */
  public void addReferences(ImmutableSortedSet<DocumentKey> keys, int targetOrBatchId) {
    for (DocumentKey key : keys) {
      addReference(key, targetOrBatchId);
    }
  }

  /** Removes a reference to the given document key for the given ID. */
  public void removeReference(DocumentKey key, int targetOrBatchId) {
    removeReference(new DocumentReference(key, targetOrBatchId));
  }

  /** Removes references to the given document keys for the given ID. */
  public void removeReferences(ImmutableSortedSet<DocumentKey> keys, int targetOrBatchId) {
    for (DocumentKey key : keys) {
      removeReference(key, targetOrBatchId);
    }
  }

  /**
   * Clears all references with a given ID. Calls removeReference() for each key removed.
   *
   * @return The keys of the documents that were removed.
   */
  public ImmutableSortedSet<DocumentKey> removeReferencesForId(int targetId) {
    DocumentKey emptyKey = DocumentKey.empty();
    DocumentReference startRef = new DocumentReference(emptyKey, targetId);
    Iterator<DocumentReference> it = referencesByTarget.iteratorFrom(startRef);
    ImmutableSortedSet<DocumentKey> keys = DocumentKey.emptyKeySet();
    while (it.hasNext()) {
      DocumentReference ref = it.next();
      if (ref.getId() == targetId) {
        keys = keys.insert(ref.getKey());
        removeReference(ref);
      } else {
        break;
      }
    }

    return keys;
  }

  /** Clears all references for all IDs. */
  public void removeAllReferences() {
    for (DocumentReference reference : referencesByKey) {
      removeReference(reference);
    }
  }

  private void removeReference(DocumentReference ref) {
    referencesByKey = referencesByKey.remove(ref);
    referencesByTarget = referencesByTarget.remove(ref);
  }

  /** Returns all of the document keys that have had references added for the given ID. */
  public ImmutableSortedSet<DocumentKey> referencesForId(int target) {
    DocumentKey emptyKey = DocumentKey.empty();
    DocumentReference startRef = new DocumentReference(emptyKey, target);

    Iterator<DocumentReference> iterator = referencesByTarget.iteratorFrom(startRef);
    ImmutableSortedSet<DocumentKey> keys = DocumentKey.emptyKeySet();
    while (iterator.hasNext()) {
      DocumentReference reference = iterator.next();
      if (reference.getId() == target) {
        keys = keys.insert(reference.getKey());
      } else {
        break;
      }
    }
    return keys;
  }

  public boolean containsKey(DocumentKey key) {
    DocumentReference ref = new DocumentReference(key, 0);

    Iterator<DocumentReference> iterator = referencesByKey.iteratorFrom(ref);
    if (!iterator.hasNext()) {
      return false;
    }

    DocumentKey firstKey = iterator.next().getKey();
    return firstKey.equals(key);
  }
}
