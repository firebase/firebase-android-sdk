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

import com.google.firebase.database.collection.ImmutableSortedMap;

/** Provides static helpers around document collections. */
public class DocumentCollections {
  // Since immutable maps are covariant in the value type, we don't care about the value type
  private static final ImmutableSortedMap<DocumentKey, ?> EMPTY_DOCUMENT_MAP =
      ImmutableSortedMap.Builder.emptyMap(DocumentKey.comparator());

  /** Returns an empty, immutable document map */
  @SuppressWarnings("unchecked")
  public static ImmutableSortedMap<DocumentKey, Document> emptyDocumentMap() {
    return (ImmutableSortedMap<DocumentKey, Document>) EMPTY_DOCUMENT_MAP;
  }

  /** Returns an empty, immutable "maybe" document map */
  @SuppressWarnings("unchecked")
  public static ImmutableSortedMap<DocumentKey, MaybeDocument> emptyMaybeDocumentMap() {
    return (ImmutableSortedMap<DocumentKey, MaybeDocument>) EMPTY_DOCUMENT_MAP;
  }

  /** Returns an empty, immutable versions map */
  @SuppressWarnings("unchecked")
  public static ImmutableSortedMap<DocumentKey, SnapshotVersion> emptyVersionMap() {
    return (ImmutableSortedMap<DocumentKey, SnapshotVersion>) EMPTY_DOCUMENT_MAP;
  }
}
