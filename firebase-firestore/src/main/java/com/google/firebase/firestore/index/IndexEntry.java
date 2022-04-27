// Copyright 2021 Google LLC
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

package com.google.firebase.firestore.index;

import static com.google.firebase.firestore.util.Util.compareByteArrays;

import com.google.auto.value.AutoValue;
import com.google.firebase.firestore.model.DocumentKey;

/** Represents an index entry saved by the SDK in its local storage. */
@AutoValue
public abstract class IndexEntry implements Comparable<IndexEntry> {

  public static IndexEntry create(
      int indexId, DocumentKey documentKey, byte[] arrayValue, byte[] directionalValue) {
    return new AutoValue_IndexEntry(indexId, documentKey, arrayValue, directionalValue);
  }

  public abstract int getIndexId();

  public abstract DocumentKey getDocumentKey();

  @SuppressWarnings("mutable")
  public abstract byte[] getArrayValue();

  @SuppressWarnings("mutable")
  public abstract byte[] getDirectionalValue();

  @Override
  public int compareTo(IndexEntry other) {
    int cmp = Integer.compare(getIndexId(), other.getIndexId());
    if (cmp != 0) return cmp;

    cmp = getDocumentKey().compareTo(other.getDocumentKey());
    if (cmp != 0) return cmp;

    cmp = compareByteArrays(getArrayValue(), other.getArrayValue());
    if (cmp != 0) return cmp;

    return compareByteArrays(getDirectionalValue(), other.getDirectionalValue());
  }
}
