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
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.util.Assert;
import java.util.Map;

/**
 * A {@code QueryDocumentSnapshot} contains data read from a document in your Cloud Firestore
 * database as part of a query. The document is guaranteed to exist and its data can be extracted
 * using the {@link #getData()} or the various {@code get()} methods in {@link DocumentSnapshot}
 * (such as {@link DocumentSnapshot#get(String)}).
 *
 * <p>{@code QueryDocumentSnapshot} offers the same API surface as {@code DocumentSnapshot}. Since
 * query results contain only existing documents, the {@link #exists()} method will always return
 * true and {@link #getData()} will never be {@code null}.
 *
 * <p><b>Subclassing Note</b>: Cloud Firestore classes are not meant to be subclassed except for use
 * in test mocks. Subclassing is not supported in production code and new SDK releases may break
 * code that does so.
 */
public class QueryDocumentSnapshot extends DocumentSnapshot {

  private QueryDocumentSnapshot(
      FirebaseFirestore firestore,
      DocumentKey key,
      @Nullable Document doc,
      boolean isFromCache,
      boolean hasPendingWrites) {
    super(firestore, key, doc, isFromCache, hasPendingWrites);
  }

  static QueryDocumentSnapshot fromDocument(
      FirebaseFirestore firestore, Document doc, boolean fromCache, boolean hasPendingWrites) {
    return new QueryDocumentSnapshot(firestore, doc.getKey(), doc, fromCache, hasPendingWrites);
  }

  /**
   * Returns the fields of the document as a Map. Field values will be converted to their native
   * Java representation.
   *
   * @return The fields of the document as a Map.
   */
  @NonNull
  @Override
  public Map<String, Object> getData() {
    Map<String, Object> result = super.getData();
    Assert.hardAssert(result != null, "Data in a QueryDocumentSnapshot should be non-null");
    return result;
  }

  /**
   * Returns the fields of the document as a Map. Field values will be converted to their native
   * Java representation.
   *
   * @param serverTimestampBehavior Configures the behavior for server timestamps that have not yet
   *     been set to their final value.
   * @return The fields of the document as a Map or {@code null} if the document doesn't exist.
   */
  @NonNull
  @Override
  public Map<String, Object> getData(@NonNull ServerTimestampBehavior serverTimestampBehavior) {
    checkNotNull(
        serverTimestampBehavior, "Provided serverTimestampBehavior value must not be null.");
    Map<String, Object> result = super.getData(serverTimestampBehavior);
    Assert.hardAssert(result != null, "Data in a QueryDocumentSnapshot should be non-null");
    return result;
  }

  /**
   * Returns the contents of the document converted to a POJO.
   *
   * @param valueType The Java class to create
   * @return The contents of the document in an object of type T
   */
  @NonNull
  @Override
  public <T> T toObject(@NonNull Class<T> valueType) {
    T result = super.toObject(valueType);
    Assert.hardAssert(result != null, "Object in a QueryDocumentSnapshot should be non-null");
    return result;
  }

  /**
   * Returns the contents of the document converted to a POJO.
   *
   * @param valueType The Java class to create
   * @param serverTimestampBehavior Configures the behavior for server timestamps that have not yet
   *     been set to their final value.
   * @return The contents of the document in an object of type T.
   */
  @NonNull
  @Override
  public <T> T toObject(
      @NonNull Class<T> valueType, @NonNull ServerTimestampBehavior serverTimestampBehavior) {
    checkNotNull(
        serverTimestampBehavior, "Provided serverTimestampBehavior value must not be null.");
    T result = super.toObject(valueType, serverTimestampBehavior);
    Assert.hardAssert(result != null, "Object in a QueryDocumentSnapshot should be non-null");
    return result;
  }
}
