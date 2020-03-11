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

import static com.google.firebase.firestore.util.Assert.fail;
import static com.google.firebase.firestore.util.Preconditions.checkNotNull;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.core.UserData.ParsedSetData;
import com.google.firebase.firestore.core.UserData.ParsedUpdateData;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.MaybeDocument;
import com.google.firebase.firestore.model.NoDocument;
import com.google.firebase.firestore.util.Executors;
import com.google.firebase.firestore.util.Util;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * A {@code Transaction} is passed to a Function to provide the methods to read and write data
 * within the transaction context.
 *
 * <p><b>Subclassing Note</b>: Cloud Firestore classes are not meant to be subclassed except for use
 * in test mocks. Subclassing is not supported in production code and new SDK releases may break
 * code that does so.
 *
 * @see FirebaseFirestore#runTransaction(Function)
 */
public class Transaction {
  private final com.google.firebase.firestore.core.Transaction transaction;
  private final FirebaseFirestore firestore;

  Transaction(
      com.google.firebase.firestore.core.Transaction transaction, FirebaseFirestore firestore) {
    this.transaction = checkNotNull(transaction);
    this.firestore = checkNotNull(firestore);
  }

  /**
   * Overwrites the document referred to by the provided {@code DocumentReference}. If the document
   * does not yet exist, it will be created. If a document already exists, it will be overwritten.
   *
   * @param documentRef The {@code DocumentReference} to overwrite.
   * @param data The data to write to the document (e.g. a Map or a POJO containing the desired
   *     document contents).
   * @return This {@code Transaction} instance. Used for chaining method calls.
   */
  @NonNull
  public Transaction set(@NonNull DocumentReference documentRef, @NonNull Object data) {
    return set(documentRef, data, SetOptions.OVERWRITE);
  }

  /**
   * Writes to the document referred to by the provided DocumentReference. If the document does not
   * yet exist, it will be created. If you pass {@code SetOptions}, the provided data can be merged
   * into an existing document.
   *
   * @param documentRef The {@code DocumentReference} to overwrite.
   * @param data The data to write to the document (e.g. a Map or a POJO containing the desired
   *     document contents).
   * @param options An object to configure the set behavior.
   * @return This {@code Transaction} instance. Used for chaining method calls.
   */
  @NonNull
  public Transaction set(
      @NonNull DocumentReference documentRef, @NonNull Object data, @NonNull SetOptions options) {
    firestore.validateReference(documentRef);
    checkNotNull(data, "Provided data must not be null.");
    checkNotNull(options, "Provided options must not be null.");
    ParsedSetData parsed =
        options.isMerge()
            ? firestore.getUserDataReader().parseMergeData(data, options.getFieldMask())
            : firestore.getUserDataReader().parseSetData(data);
    transaction.set(documentRef.getKey(), parsed);
    return this;
  }

  /**
   * Updates fields in the document referred to by the provided {@code DocumentReference}. If no
   * document exists yet, the update will fail.
   *
   * @param documentRef The {@code DocumentReference} to update.
   * @param data A map of field / value pairs to update. Fields can contain dots to reference nested
   *     fields within the document.
   * @return This {@code Transaction} instance. Used for chaining method calls.
   */
  @NonNull
  public Transaction update(
      @NonNull DocumentReference documentRef, @NonNull Map<String, Object> data) {
    ParsedUpdateData parsedData = firestore.getUserDataReader().parseUpdateData(data);
    return update(documentRef, parsedData);
  }

  /**
   * Updates fields in the document referred to by the provided {@code DocumentReference}. If no
   * document exists yet, the update will fail.
   *
   * @param documentRef The {@code DocumentReference} to update.
   * @param field The first field to update. Fields can contain dots to reference a nested field
   *     within the document.
   * @param value The first value
   * @param moreFieldsAndValues Additional field/value pairs.
   * @return This {@code Transaction} instance. Used for chaining method calls.
   */
  @NonNull
  public Transaction update(
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
   * @return This {@code Transaction} instance. Used for chaining method calls.
   */
  @NonNull
  public Transaction update(
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

  private Transaction update(
      @NonNull DocumentReference documentRef, @NonNull ParsedUpdateData updateData) {
    firestore.validateReference(documentRef);
    transaction.update(documentRef.getKey(), updateData);
    return this;
  }

  /**
   * Deletes the document referred to by the provided {@code DocumentReference}.
   *
   * @param documentRef The {@code DocumentReference} to delete.
   * @return This {@code Transaction} instance. Used for chaining method calls.
   */
  @NonNull
  public Transaction delete(@NonNull DocumentReference documentRef) {
    firestore.validateReference(documentRef);
    transaction.delete(documentRef.getKey());
    return this;
  }

  /**
   * Reads the document referenced by the provided {@code DocumentReference}
   *
   * @param documentRef The {@code DocumentReference} to read.
   * @return A Task that will be resolved with the contents of the Document at this {@code
   *     DocumentReference}.
   */
  private Task<DocumentSnapshot> getAsync(DocumentReference documentRef) {
    return transaction
        .lookup(Collections.singletonList(documentRef.getKey()))
        .continueWith(
            Executors.DIRECT_EXECUTOR,
            task -> {
              if (!task.isSuccessful()) {
                throw task.getException();
              }
              List<MaybeDocument> docs = task.getResult();
              if (docs.size() != 1) {
                throw fail("Mismatch in docs returned from document lookup.");
              }
              MaybeDocument doc = docs.get(0);
              if (doc instanceof Document) {
                return DocumentSnapshot.fromDocument(
                    firestore, (Document) doc, /*fromCache=*/ false, /*hasPendingWrites=*/ false);
              } else if (doc instanceof NoDocument) {
                return DocumentSnapshot.fromNoDocument(
                    firestore, doc.getKey(), /*fromCache=*/ false, /*hasPendingWrites=*/ false);
              } else {
                throw fail(
                    "BatchGetDocumentsRequest returned unexpected document type: "
                        + doc.getClass().getCanonicalName());
              }
            });
  }

  /**
   * Reads the document referenced by this {@code DocumentReference}
   *
   * @param documentRef The {@code DocumentReference} to read.
   * @return The contents of the Document at this {@code DocumentReference}.
   */
  @NonNull
  public DocumentSnapshot get(@NonNull DocumentReference documentRef)
      throws FirebaseFirestoreException {
    firestore.validateReference(documentRef);
    try {
      return Tasks.await(getAsync(documentRef));
    } catch (ExecutionException ee) {
      if (ee.getCause() instanceof FirebaseFirestoreException) {
        throw ((FirebaseFirestoreException) ee.getCause());
      }
      throw new RuntimeException(ee.getCause());
    } catch (InterruptedException ie) {
      throw new RuntimeException(ie);
    }
  }

  /**
   * An interface for providing code to be executed within a transaction context.
   *
   * @see FirebaseFirestore#runTransaction(Function)
   */
  public interface Function<TResult> {
    @Nullable
    TResult apply(@NonNull Transaction transaction) throws FirebaseFirestoreException;
  }
}
