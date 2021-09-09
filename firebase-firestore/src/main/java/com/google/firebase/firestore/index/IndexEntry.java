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

/**
 * Represents an index entry saved by the SDK in the local storage. Temporary placeholder, since
 * we'll probably serialize the indexValue right away rather than store it.
 */
// TODO(indexing)
public class IndexEntry {
  private final int indexId;
  private final byte[] indexValue;
  private final String uid;
  private final String documentId;

  public IndexEntry(int indexId, byte[] indexValue, String uid, String documentId) {
    this.indexId = indexId;
    this.indexValue = indexValue;
    this.uid = uid;
    this.documentId = documentId;
  }

  public int getIndexId() {
    return indexId;
  }

  public byte[] getIndexValue() {
    return indexValue;
  }

  public String getUid() {
    return uid;
  }

  public String getDocumentId() {
    return documentId;
  }
}
