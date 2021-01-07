// Copyright 2020 Google LLC
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

import androidx.annotation.Nullable;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.MaybeDocument;
import com.google.firebase.firestore.util.Assert;

/**
 * A mutation that verifies the existence of the document at the given key with the provided
 * precondition.
 *
 * <p>The `verify` operation is only used in Transactions, and this class serves primarily to
 * facilitate serialization into protos.
 */
public final class VerifyMutation extends Mutation {

  public VerifyMutation(DocumentKey key, Precondition precondition) {
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

    VerifyMutation that = (VerifyMutation) o;
    return hasSameKeyAndPrecondition(that);
  }

  @Override
  public int hashCode() {
    return keyAndPreconditionHashCode();
  }

  @Override
  public String toString() {
    return "VerifyMutation{" + keyAndPreconditionToString() + "}";
  }

  @Override
  public MaybeDocument applyToRemoteDocument(
      @Nullable MaybeDocument maybeDoc, MutationResult mutationResult) {
    throw Assert.fail("VerifyMutation should only be used in Transactions.");
  }

  @Nullable
  @Override
  public MaybeDocument applyToLocalView(
      @Nullable MaybeDocument maybeDoc, @Nullable MaybeDocument baseDoc, Timestamp localWriteTime) {
    throw Assert.fail("VerifyMutation should only be used in Transactions.");
  }
}
