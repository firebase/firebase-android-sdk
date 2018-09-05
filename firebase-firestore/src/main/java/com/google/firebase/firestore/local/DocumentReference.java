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

import static com.google.firebase.firestore.util.Util.compareIntegers;

import com.google.firebase.firestore.model.DocumentKey;
import java.util.Comparator;

/**
 * An immutable value used to keep track of an association between some referencing target or batch
 * and a document key that the target or batch references.
 *
 * <p>A reference can be from either listen targets (identified by their target ID) or mutation
 * batches (identified by their batch ID). See GarbageCollector for more details.
 *
 * <p>Not to be confused with DocumentReference in the public API.
 */
class DocumentReference {

  private final DocumentKey key;
  private final int targetOrBatchId;

  /** Initializes the document reference with the given key and ID. */
  public DocumentReference(DocumentKey key, int targetOrBatchId) {
    this.key = key;
    this.targetOrBatchId = targetOrBatchId;
  }

  /** Returns the document key that's the target of this reference. */
  DocumentKey getKey() {
    return key;
  }

  /**
   * Returns the targetID of a referring target or the batchID of a referring mutation batch. (Which
   * this is depends upon which ReferenceSet this reference is a part of.)
   */
  int getId() {
    return targetOrBatchId;
  }

  /** Sorts document references by key then ID. */
  static final Comparator<DocumentReference> BY_KEY =
      (o1, o2) -> {
        int keyComp = o1.key.compareTo(o2.key);
        if (keyComp != 0) {
          return keyComp;
        }

        return compareIntegers(o1.targetOrBatchId, o2.targetOrBatchId);
      };

  static final Comparator<DocumentReference> BY_TARGET =
      (o1, o2) -> {
        int targetComp = compareIntegers(o1.targetOrBatchId, o2.targetOrBatchId);

        if (targetComp != 0) {
          return targetComp;
        }

        return o1.key.compareTo(o2.key);
      };
}
