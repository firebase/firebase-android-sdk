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

package com.google.firebase.firestore.model.mutation;

import static com.google.firebase.firestore.util.Assert.hardAssert;

import androidx.annotation.Nullable;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.MaybeDocument;
import com.google.firebase.firestore.model.NoDocument;
import com.google.firebase.firestore.model.SnapshotVersion;

/** Represents a Delete operation */
public final class DeleteMutation extends Mutation {

  public DeleteMutation(DocumentKey key, Precondition precondition) {
    super(key, precondition);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    DeleteMutation that = (DeleteMutation) o;
    return hasSameKeyAndPrecondition(that);
  }

  @Override
  public int hashCode() {
    return keyAndPreconditionHashCode();
  }

  @Override
  public String toString() {
    return "DeleteMutation{" + keyAndPreconditionToString() + "}";
  }

  @Override
  public MaybeDocument applyToRemoteDocument(
      @Nullable MaybeDocument maybeDoc, MutationResult mutationResult) {
    verifyKeyMatches(maybeDoc);

    hardAssert(
        mutationResult.getTransformResults() == null,
        "Transform results received by DeleteMutation.");

    // Unlike applyToLocalView, if we're applying a mutation to a remote document the server has
    // accepted the mutation so the precondition must have held.

    // We store the deleted document at the commit version of the delete. Any document version
    // that the server sends us before the delete was applied is discarded
    return new NoDocument(getKey(), mutationResult.getVersion(), /*hasCommittedMutations=*/ true);
  }

  @Nullable
  @Override
  public MaybeDocument applyToLocalView(
      @Nullable MaybeDocument maybeDoc, @Nullable MaybeDocument baseDoc, Timestamp localWriteTime) {
    verifyKeyMatches(maybeDoc);

    if (!this.getPrecondition().isValidFor(maybeDoc)) {
      return maybeDoc;
    }

    return new NoDocument(getKey(), SnapshotVersion.NONE, /*hasCommittedMutations=*/ false);
  }
}
