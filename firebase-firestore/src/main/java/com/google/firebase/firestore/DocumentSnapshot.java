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
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.util.CustomClassMapper;
import com.google.firestore.v1.Value;
import java.util.Date;
import java.util.Map;

/**
 * A {@code DocumentSnapshot} contains data read from a document in your Cloud Firestore database.
 * The data can be extracted with the {@link #getData()} or {@link #get(String)} methods.
 *
 * <p>If the {@code DocumentSnapshot} points to a non-existing document, {@link #getData()} and its
 * corresponding methods will return {@code null}. You can always explicitly check for a document's
 * existence by calling {@link #exists()}.
 *
 * <p><b>Subclassing Note</b>: Cloud Firestore classes are not meant to be subclassed except for use
 * in test mocks. Subclassing is not supported in production code and new SDK releases may break
 * code that does so.
 */
public class DocumentSnapshot {

  /**
   * Controls the return value for server timestamps that have not yet been set to their final
   * value.
   */
  public enum ServerTimestampBehavior {
    /**
     * Return {@code null} for {@link com.google.firebase.firestore.FieldValue#serverTimestamp
     * ServerTimestamps} that have not yet been set to their final value.
     */
    NONE,

    /**
     * Return local estimates for {@link com.google.firebase.firestore.FieldValue#serverTimestamp
     * ServerTimestamps} that have not yet been set to their final value. This estimate will likely
     * differ from the final value and may cause these pending values to change once the server
     * result becomes available.
     */
    ESTIMATE,

    /**
     * Return the previous value for {@link com.google.firebase.firestore.FieldValue#serverTimestamp
     * ServerTimestamps} that have not yet been set to their final value.
     */
    PREVIOUS;

    static final ServerTimestampBehavior DEFAULT = ServerTimestampBehavior.NONE;
  }

  private final FirebaseFirestore firestore;

  private final DocumentKey key;

  /** Is {@code null} if the document doesn't exist */
  private final @Nullable Document doc;

  private final SnapshotMetadata metadata;

  DocumentSnapshot(
      FirebaseFirestore firestore,
      DocumentKey key,
      @Nullable Document doc,
      boolean isFromCache,
      boolean hasPendingWrites) {
    this.firestore = checkNotNull(firestore);
    this.key = checkNotNull(key);
    this.doc = doc;
    this.metadata = new SnapshotMetadata(hasPendingWrites, isFromCache);
  }

  static DocumentSnapshot fromDocument(
      FirebaseFirestore firestore, Document doc, boolean fromCache, boolean hasPendingWrites) {
    return new DocumentSnapshot(firestore, doc.getKey(), doc, fromCache, hasPendingWrites);
  }

  static DocumentSnapshot fromNoDocument(
      FirebaseFirestore firestore, DocumentKey key, boolean fromCache, boolean hasPendingWrites) {
    return new DocumentSnapshot(firestore, key, null, fromCache, hasPendingWrites);
  }

  /** @return The id of the document. */
  @NonNull
  public String getId() {
    return key.getPath().getLastSegment();
  }

  /** @return The metadata for this document snapshot. */
  @NonNull
  public SnapshotMetadata getMetadata() {
    return metadata;
  }

  /** @return true if the document existed in this snapshot. */
  public boolean exists() {
    return doc != null;
  }

  @Nullable
  Document getDocument() {
    return doc;
  }

  /**
   * Returns the fields of the document as a Map or {@code null} if the document doesn't exist.
   * Field values will be converted to their native Java representation.
   *
   * @return The fields of the document as a Map or {@code null} if the document doesn't exist.
   */
  @Nullable
  public Map<String, Object> getData() {
    return getData(ServerTimestampBehavior.DEFAULT);
  }

  /**
   * Returns the fields of the document as a Map or {@code null} if the document doesn't exist.
   * Field values will be converted to their native Java representation.
   *
   * @param serverTimestampBehavior Configures the behavior for server timestamps that have not yet
   *     been set to their final value.
   * @return The fields of the document as a Map or {@code null} if the document doesn't exist.
   */
  @Nullable
  public Map<String, Object> getData(@NonNull ServerTimestampBehavior serverTimestampBehavior) {
    checkNotNull(
        serverTimestampBehavior, "Provided serverTimestampBehavior value must not be null.");
    UserDataWriter userDataWriter = new UserDataWriter(firestore, serverTimestampBehavior);
    return doc == null ? null : userDataWriter.convertObject(doc.getData().getFieldsMap());
  }

  /**
   * Returns the contents of the document converted to a POJO or {@code null} if the document
   * doesn't exist.
   *
   * @param valueType The Java class to create
   * @return The contents of the document in an object of type T or {@code null} if the document
   *     doesn't exist.
   */
  @Nullable
  public <T> T toObject(@NonNull Class<T> valueType) {
    return toObject(valueType, ServerTimestampBehavior.DEFAULT);
  }

  /**
   * Returns the contents of the document converted to a POJO or {@code null} if the document
   * doesn't exist.
   *
   * @param valueType The Java class to create
   * @param serverTimestampBehavior Configures the behavior for server timestamps that have not yet
   *     been set to their final value.
   * @return The contents of the document in an object of type T or {@code null} if the document
   *     doesn't exist.
   */
  @Nullable
  public <T> T toObject(
      @NonNull Class<T> valueType, @NonNull ServerTimestampBehavior serverTimestampBehavior) {
    checkNotNull(valueType, "Provided POJO type must not be null.");
    checkNotNull(
        serverTimestampBehavior, "Provided serverTimestampBehavior value must not be null.");
    Map<String, Object> data = getData(serverTimestampBehavior);
    return data == null
        ? null
        : CustomClassMapper.convertToCustomClass(data, valueType, getReference());
  }

  /**
   * Returns whether or not the field exists in the document. Returns false if the document does not
   * exist.
   *
   * @param field the path to the field.
   * @return true iff the field exists.
   */
  public boolean contains(@NonNull String field) {
    return contains(FieldPath.fromDotSeparatedPath(field));
  }

  /**
   * Returns whether or not the field exists in the document. Returns false if the document does not
   * exist.
   *
   * @param fieldPath the path to the field.
   * @return true iff the field exists.
   */
  public boolean contains(@NonNull FieldPath fieldPath) {
    checkNotNull(fieldPath, "Provided field path must not be null.");
    return (doc != null) && (doc.getField(fieldPath.getInternalPath()) != null);
  }

  /**
   * Returns the value at the field or {@code null} if the field doesn't exist.
   *
   * @param field The path to the field
   * @return The value at the given field or {@code null}.
   */
  @Nullable
  public Object get(@NonNull String field) {
    return get(FieldPath.fromDotSeparatedPath(field), ServerTimestampBehavior.DEFAULT);
  }

  /**
   * Returns the value at the field or {@code null} if the field doesn't exist.
   *
   * @param field The path to the field
   * @param serverTimestampBehavior Configures the behavior for server timestamps that have not yet
   *     been set to their final value.
   * @return The value at the given field or {@code null}.
   */
  @Nullable
  public Object get(
      @NonNull String field, @NonNull ServerTimestampBehavior serverTimestampBehavior) {
    return get(FieldPath.fromDotSeparatedPath(field), serverTimestampBehavior);
  }

  /**
   * Returns the value at the field or {@code null} if the field or document doesn't exist.
   *
   * @param fieldPath The path to the field
   * @return The value at the given field or {@code null}.
   */
  @Nullable
  public Object get(@NonNull FieldPath fieldPath) {
    return get(fieldPath, ServerTimestampBehavior.DEFAULT);
  }

  /**
   * Returns the value at the field or {@code null} if the field or document doesn't exist.
   *
   * @param fieldPath The path to the field
   * @param serverTimestampBehavior Configures the behavior for server timestamps that have not yet
   *     been set to their final value.
   * @return The value at the given field or {@code null}.
   */
  @Nullable
  public Object get(
      @NonNull FieldPath fieldPath, @NonNull ServerTimestampBehavior serverTimestampBehavior) {
    checkNotNull(fieldPath, "Provided field path must not be null.");
    checkNotNull(
        serverTimestampBehavior, "Provided serverTimestampBehavior value must not be null.");
    return getInternal(fieldPath.getInternalPath(), serverTimestampBehavior);
  }

  /**
   * Returns the value at the field, converted to a POJO, or {@code null} if the field or document
   * doesn't exist.
   *
   * @param field The path to the field
   * @param valueType The Java class to convert the field value to.
   * @return The value at the given field or {@code null}.
   */
  @Nullable
  public <T> T get(@NonNull String field, @NonNull Class<T> valueType) {
    return get(FieldPath.fromDotSeparatedPath(field), valueType, ServerTimestampBehavior.DEFAULT);
  }

  /**
   * Returns the value at the field, converted to a POJO, or {@code null} if the field or document
   * doesn't exist.
   *
   * @param field The path to the field
   * @param valueType The Java class to convert the field value to.
   * @param serverTimestampBehavior Configures the behavior for server timestamps that have not yet
   *     been set to their final value.
   * @return The value at the given field or {@code null}.
   */
  @Nullable
  public <T> T get(
      @NonNull String field,
      @NonNull Class<T> valueType,
      @NonNull ServerTimestampBehavior serverTimestampBehavior) {
    return get(FieldPath.fromDotSeparatedPath(field), valueType, serverTimestampBehavior);
  }

  /**
   * Returns the value at the field, converted to a POJO, or {@code null} if the field or document
   * doesn't exist.
   *
   * @param fieldPath The path to the field
   * @param valueType The Java class to convert the field value to.
   * @return The value at the given field or {@code null}.
   */
  @Nullable
  public <T> T get(@NonNull FieldPath fieldPath, @NonNull Class<T> valueType) {
    return get(fieldPath, valueType, ServerTimestampBehavior.DEFAULT);
  }

  /**
   * Returns the value at the field, converted to a POJO, or {@code null} if the field or document
   * doesn't exist.
   *
   * @param fieldPath The path to the field
   * @param valueType The Java class to convert the field value to.
   * @param serverTimestampBehavior Configures the behavior for server timestamps that have not yet
   *     been set to their final value.
   * @return The value at the given field or {@code null}.
   */
  @Nullable
  public <T> T get(
      @NonNull FieldPath fieldPath,
      @NonNull Class<T> valueType,
      @NonNull ServerTimestampBehavior serverTimestampBehavior) {
    Object data = get(fieldPath, serverTimestampBehavior);
    return data == null
        ? null
        : CustomClassMapper.convertToCustomClass(data, valueType, getReference());
  }

  /**
   * Returns the value of the field as a boolean. If the value is not a boolean this will throw a
   * runtime exception.
   *
   * @param field The path to the field.
   * @return The value of the field
   */
  @Nullable
  public Boolean getBoolean(@NonNull String field) {
    return getTypedValue(field, Boolean.class);
  }

  /**
   * Returns the value of the field as a double.
   *
   * @param field The path to the field.
   * @throws RuntimeException if the value is not a number.
   * @return The value of the field
   */
  @Nullable
  public Double getDouble(@NonNull String field) {
    Number val = getTypedValue(field, Number.class);
    return val != null ? val.doubleValue() : null;
  }

  /**
   * Returns the value of the field as a String.
   *
   * @param field The path to the field.
   * @throws RuntimeException if the value is not a String.
   * @return The value of the field
   */
  @Nullable
  public String getString(@NonNull String field) {
    return getTypedValue(field, String.class);
  }

  /**
   * Returns the value of the field as a long.
   *
   * @param field The path to the field.
   * @throws RuntimeException if the value is not a number.
   * @return The value of the field
   */
  @Nullable
  public Long getLong(@NonNull String field) {
    Number val = getTypedValue(field, Number.class);
    return val != null ? val.longValue() : null;
  }

  /**
   * Returns the value of the field as a Date.
   *
   * @param field The path to the field.
   * @throws RuntimeException if the value is not a Date.
   * @return The value of the field
   */
  @Nullable
  public Date getDate(@NonNull String field) {
    return getDate(field, ServerTimestampBehavior.DEFAULT);
  }

  /**
   * Returns the value of the field as a Date.
   *
   * @param field The path to the field.
   * @param serverTimestampBehavior Configures the behavior for server timestamps that have not yet
   *     been set to their final value.
   * @throws RuntimeException if the value is not a Date.
   * @return The value of the field
   */
  @Nullable
  public Date getDate(
      @NonNull String field, @NonNull ServerTimestampBehavior serverTimestampBehavior) {
    checkNotNull(field, "Provided field path must not be null.");
    checkNotNull(
        serverTimestampBehavior, "Provided serverTimestampBehavior value must not be null.");
    @Nullable Timestamp timestamp = getTimestamp(field, serverTimestampBehavior);
    return timestamp != null ? timestamp.toDate() : null;
  }

  /**
   * Returns the value of the field as a {@code com.google.firebase.Timestamp}.
   *
   * @param field The path to the field.
   * @throws RuntimeException if this is not a timestamp field.
   * @return The value of the field
   */
  @Nullable
  public Timestamp getTimestamp(@NonNull String field) {
    return getTimestamp(field, ServerTimestampBehavior.DEFAULT);
  }

  /**
   * Returns the value of the field as a {@code com.google.firebase.Timestamp}.
   *
   * @param field The path to the field.
   * @param serverTimestampBehavior Configures the behavior for server timestamps that have not yet
   *     been set to their final value.
   * @throws RuntimeException if the value is not a timestamp field.
   * @return The value of the field
   */
  @Nullable
  public Timestamp getTimestamp(
      @NonNull String field, @NonNull ServerTimestampBehavior serverTimestampBehavior) {
    checkNotNull(field, "Provided field path must not be null.");
    checkNotNull(
        serverTimestampBehavior, "Provided serverTimestampBehavior value must not be null.");
    Object maybeTimestamp =
        getInternal(
            FieldPath.fromDotSeparatedPath(field).getInternalPath(), serverTimestampBehavior);
    return castTypedValue(maybeTimestamp, field, Timestamp.class);
  }

  /**
   * Returns the value of the field as a Blob.
   *
   * @param field The path to the field.
   * @throws RuntimeException if the value is not a Blob.
   * @return The value of the field
   */
  @Nullable
  public Blob getBlob(@NonNull String field) {
    return getTypedValue(field, Blob.class);
  }

  /**
   * Returns the value of the field as a GeoPoint.
   *
   * @param field The path to the field.
   * @throws RuntimeException if the value is not a GeoPoint.
   * @return The value of the field
   */
  @Nullable
  public GeoPoint getGeoPoint(@NonNull String field) {
    return getTypedValue(field, GeoPoint.class);
  }

  /**
   * Returns the value of the field as a DocumentReference.
   *
   * @param field The path to the field.
   * @throws RuntimeException if the value is not a DocumentReference.
   * @return The value of the field
   */
  @Nullable
  public DocumentReference getDocumentReference(@NonNull String field) {
    return getTypedValue(field, DocumentReference.class);
  }

  /**
   * Gets the reference to the document.
   *
   * @return The reference to the document.
   */
  @NonNull
  public DocumentReference getReference() {
    return new DocumentReference(key, firestore);
  }

  @Nullable
  private <T> T getTypedValue(String field, Class<T> clazz) {
    checkNotNull(field, "Provided field must not be null.");
    Object value = get(field, ServerTimestampBehavior.DEFAULT);
    return castTypedValue(value, field, clazz);
  }

  @Nullable
  private <T> T castTypedValue(Object value, String field, Class<T> clazz) {
    if (value == null) {
      return null;
    } else if (!clazz.isInstance(value)) {
      throw new RuntimeException("Field '" + field + "' is not a " + clazz.getName());
    }
    return clazz.cast(value);
  }

  @Nullable
  private Object getInternal(
      @NonNull com.google.firebase.firestore.model.FieldPath fieldPath,
      @NonNull ServerTimestampBehavior serverTimestampBehavior) {
    if (doc != null) {
      Value val = doc.getField(fieldPath);
      if (val != null) {
        UserDataWriter userDataWriter = new UserDataWriter(firestore, serverTimestampBehavior);
        return userDataWriter.convertValue(val);
      }
    }
    return null;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof DocumentSnapshot)) {
      return false;
    }
    DocumentSnapshot other = (DocumentSnapshot) obj;
    return firestore.equals(other.firestore)
        && key.equals(other.key)
        && (doc == null ? other.doc == null : doc.equals(other.doc))
        && metadata.equals(other.metadata);
  }

  @Override
  public int hashCode() {
    int hash = firestore.hashCode();
    hash = hash * 31 + key.hashCode();
    hash = hash * 31 + (doc != null ? doc.hashCode() : 0);
    hash = hash * 31 + metadata.hashCode();
    return hash;
  }

  @Override
  public String toString() {
    return "DocumentSnapshot{" + "key=" + key + ", metadata=" + metadata + ", doc=" + doc + '}';
  }
}
