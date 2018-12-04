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

/** Represents that no documents exists for the key at the given version. */
public final class NoDocument extends MaybeDocument {

  private boolean hasCommittedMutations;

  public NoDocument(DocumentKey key, SnapshotVersion version, boolean hasCommittedMutations) {
    super(key, version);
    this.hasCommittedMutations = hasCommittedMutations;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    NoDocument that = (NoDocument) o;

    return hasCommittedMutations == that.hasCommittedMutations
        && getVersion().equals(that.getVersion())
        && getKey().equals(that.getKey());
  }

  @Override
  public boolean hasPendingWrites() {
    return hasCommittedMutations();
  }

  public boolean hasCommittedMutations() {
    return hasCommittedMutations;
  }

  @Override
  public int hashCode() {
    int result = getKey().hashCode();
    result = 31 * result + (hasCommittedMutations ? 1 : 0);
    result = 31 * result + getVersion().hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "NoDocument{key="
        + getKey()
        + ", version="
        + getVersion()
        + ", hasCommittedMutations="
        + hasCommittedMutations()
        + "}";
  }
}
