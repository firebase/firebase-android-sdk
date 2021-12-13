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

package com.google.firebase.firestore.index

import com.google.firebase.firestore.model.DocumentKey
import com.google.firebase.firestore.util.Util

/** Represents an index entry saved by the SDK in its local storage. */
data class IndexEntry(
    val indexId: Int,
    val documentKey: DocumentKey,
    val arrayValue: ByteArray,
    val directionalValue: ByteArray
) : Comparable<IndexEntry> {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as IndexEntry

    if (indexId != other.indexId) return false
    if (documentKey != other.documentKey) return false
    if (!arrayValue.contentEquals(other.arrayValue)) return false
    if (!directionalValue.contentEquals(other.directionalValue)) return false

    return true
  }

  override fun hashCode(): Int {
    var result = indexId
    result = 31 * result + documentKey.hashCode()
    result = 31 * result + arrayValue.contentHashCode()
    result = 31 * result + directionalValue.contentHashCode()
    return result
  }

  override fun compareTo(other: IndexEntry): Int {
    var cmp = indexId.compareTo(other.indexId)
    if (cmp != 0) return cmp

    cmp = documentKey.compareTo(other.documentKey)
    if (cmp != 0) return cmp

    cmp = Util.compareByteArrays(directionalValue, other.directionalValue)
    if (cmp != 0) return cmp

    return Util.compareByteArrays(arrayValue, other.arrayValue)
  }
}
