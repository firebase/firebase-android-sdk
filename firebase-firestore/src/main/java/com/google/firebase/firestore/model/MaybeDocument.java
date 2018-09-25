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

/**
 * The result of a lookup for a given path may be an existing document or a marker that this
 * document does not exist at a given version.
 */
public abstract class MaybeDocument {

  private final DocumentKey key;

  private final SnapshotVersion version;

  MaybeDocument(DocumentKey key, SnapshotVersion version) {
    this.key = key;
    this.version = version;
  }

  /** The key for this document */
  public DocumentKey getKey() {
    return key;
  }

  /**
   * Returns the version of this document if it exists or a version at which this document was
   * guaranteed to not exist.
   */
  public SnapshotVersion getVersion() {
    return version;
  }

  /**
   * Whether this document has a local mutation applied that has not yet been acknowledged by Watch.
   */
  public abstract boolean hasPendingWrites();
}
