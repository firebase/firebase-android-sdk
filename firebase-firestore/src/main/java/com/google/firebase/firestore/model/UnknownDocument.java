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
 * A class representing an existing document whose data is unknown (e.g. a document that was updated
 * without a known base document).
 */
public final class UnknownDocument extends MaybeDocument {
  public UnknownDocument(DocumentKey key, SnapshotVersion version) {
    super(key, version);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    UnknownDocument that = (UnknownDocument) o;

    return getVersion().equals(that.getVersion()) && getKey().equals(that.getKey());
  }

  @Override
  public boolean hasPendingWrites() {
    return true;
  }

  @Override
  public int hashCode() {
    int result = getKey().hashCode();
    result = 31 * result + getVersion().hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "UnknownDocument{key=" + getKey() + ", version=" + getVersion() + '}';
  }
}
