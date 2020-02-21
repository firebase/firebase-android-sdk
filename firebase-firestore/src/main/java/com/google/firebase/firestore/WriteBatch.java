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

package com.google.firebase.firestore;

import static com.google.firebase.firestore.util.Preconditions.checkNotNull;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.core.UserData.ParsedSetData;
import com.google.firebase.firestore.core.UserData.ParsedUpdateData;
import com.google.firebase.firestore.model.mutation.DeleteMutation;
import com.google.firebase.firestore.model.mutation.Mutation;
import com.google.firebase.firestore.model.mutation.Precondition;
import com.google.firebase.firestore.util.Util;
import java.util.ArrayList;
import java.util.Map;

/**
 * A write batch, used to perform multiple writes as a single atomic unit.
 *
 * <p>A Batch object can be acquired by calling {@link FirebaseFirestore#batch()}. It provides
 * methods for adding writes to the write batch. None of the writes will be committed (or visible
 * locally) until {@link #commit()} is called.
 *
 * <p>Unlike transactions, write batches are persisted offline and therefore are preferable when you
 * don't need to condition your writes on read data.
 *
 * <p><b>Subclassing Note</b>: Cloud Firestore classes are not meant to be subclassed except for use
 * in test mocks. Subclassing is not supported in production code and new SDK releases may break
 * code that does so.
 */
public class WriteBatch {
  private final FirebaseFirestore firestore;
  private final ArrayList<Mutation> mutations = new ArrayList<>();
  private boolean committed = false;

  WriteBatch(FirebaseFirestore firestore) {
    this.firestore = checkNotNull(firestore);
  }

  /**
   * Overwrites the document referred to by the provided {@code DocumentReference}. If the document
   * does not yet exist, it will be created. If a document already exists, it will be overwritten.
   *
   * @param documentRef The {@code DocumentReference} to overwrite.
   * @param data The data to write to the document (e.g. a Map or a POJO containing the desired
   *     document contents).
   * @return This {@code WriteBatch} instance. Used for chaining method calls.
   */
  @NonNull
  public WriteBatch set(@NonNull DocumentReference documentRef, @NonNull Object data) {
    return set(documentRef, data, SetOptions.OVERWRITE);
  }

  /**
   * Writes to the document referred to by the provided {@code DocumentReference}. If the document
   * does not yet exist, it will be created. If you pass {@code SetOptions}, the provided data can
   * be merged into an existing document.
   *
   * @param documentRef The {@code DocumentReference} to overwrite.
   * @param data The data to write to the document (e.g. a Map or a POJO containing the desired
   *     document contents).
   * @param options An object to configure the set behavior.
   * @return This {@code WriteBatch} instance. Used for chaining method calls.
   */
  @NonNull
  public WriteBatch set(
      @NonNull DocumentReference documentRef, @NonNull Object data, @NonNull SetOptions options) {
    firestore.validateReference(documentRef);
    checkNotNull(data, "Provided data must not be null.");
    checkNotNull(options, "Provided options must not be null.");
    verifyNotCommitted();
    ParsedSetData parsed =
        options.isMerge()
            ? firestore.getUserDataReader().parseMergeData(data, options.getFieldMask())
            : firestore.getUserDataReader().parseSetData(data);
    mutations.addAll(parsed.toMutationList(documentRef.getKey(), Precondition.NONE));
    return this;
  }

  /**
   * Updates fields in the document referred to by the provided {@code DocumentReference}. If no
   * document exists yet, the update will fail.
   *
   * @param documentRef The {@code DocumentReference} to update.
   * @param data A map of field / value pairs to update. Fields can contain dots to reference nested
   *     fields within the document.
   * @return This {@code WriteBatch} instance. Used for chaining method calls.
   */
  @NonNull
  public WriteBatch update(
      @NonNull DocumentReference documentRef, @NonNull Map<String, Object> data) {
    ParsedUpdateData parsedData = firestore.getUserDataReader().parseUpdateData(data);
    return update(documentRef, parsedData);
  }

  /**
   * Updates field in the document referred to by the provided {@code DocumentReference}. If no
   * document exists yet, the update will fail.
   *
   * @param documentRef The {@code DocumentReference} to update.
   * @param field The first field to update. Fields can contain dots to reference a nested field
   *     within the document.
   * @param value The first value
   * @param moreFieldsAndValues Additional field/value pairs.
   * @return This {@code WriteBatch} instance. Used for chaining method calls.
   */
  @NonNull
  public WriteBatch update(
      @NonNull DocumentReference documentRef,
      @NonNull String field,
      @Nullable Object value,
      Object... moreFieldsAndValues) {
    ParsedUpdateData parsedData =
        firestore
            .getUserDataReader()
            .parseUpdateData(
                Util.collectUpdateArguments(
                    /* fieldPathOffset= */ 1, field, value, moreFieldsAndValues));
    return update(documentRef, parsedData);
  }

  /**
   * Updates fields in the document referred to by the provided {@code DocumentReference}. If no
   * document exists yet, the update will fail.
   *
   * @param documentRef The {@code DocumentReference} to update.
   * @param fieldPath The first field to update.
   * @param value The first value
   * @param moreFieldsAndValues Additional field/value pairs.
   * @return This {@code WriteBatch} instance. Used for chaining method calls.
   */
  @NonNull
  public WriteBatch update(
      @NonNull DocumentReference documentRef,
      @NonNull FieldPath fieldPath,
      @Nullable Object value,
      Object... moreFieldsAndValues) {
    ParsedUpdateData parsedData =
        firestore
            .getUserDataReader()
            .parseUpdateData(
                Util.collectUpdateArguments(
                    /* fieldPathOffset= */ 1, fieldPath, value, moreFieldsAndValues));
    return update(documentRef, parsedData);
  }

  private WriteBatch update(
      @NonNull DocumentReference documentRef, @NonNull ParsedUpdateData updateData) {
    firestore.validateReference(documentRef);
    verifyNotCommitted();
    mutations.addAll(updateData.toMutationList(documentRef.getKey(), Precondition.exists(true)));
    return this;
  }

  /**
   * Deletes the document referred to by the provided {@code DocumentReference}.
   *
   * @param documentRef The {@code DocumentReference} to delete.
   * @return This {@code WriteBatch} instance. Used for chaining method calls.
   */
  @NonNull
  public WriteBatch delete(@NonNull DocumentReference documentRef) {
    firestore.validateReference(documentRef);
    verifyNotCommitted();
    mutations.add(new DeleteMutation(documentRef.getKey(), Precondition.NONE));
    return this;
  }

  /**
   * Commits all of the writes in this write batch as a single atomic unit.
   *
   * @return A Task that will be resolved when the write finishes.
   */
  @NonNull
  public Task<Void> commit() {
    verifyNotCommitted();
    committed = true;
    if (mutations.size() > 0) {
      return firestore.getClient().write(mutations);
    } else {
      return Tasks.forResult(null);
    }
  }

  private void verifyNotCommitted() {
    if (committed) {
      throw new IllegalStateException(
          "A write batch can no longer be used after commit() has been called.");
    }
  }

  /**
   * An interface for providing code to be executed within a {@code WriteBatch} context.
   *
   * @see FirebaseFirestore#runBatch(WriteBatch.Function)
   */
  public interface Function {

    void apply(@NonNull WriteBatch batch);
  }
}
