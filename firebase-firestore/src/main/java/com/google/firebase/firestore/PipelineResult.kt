/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.firestore

import com.google.common.annotations.Beta
import com.google.firebase.Timestamp
import com.google.firebase.firestore.model.Document
import com.google.firebase.firestore.model.Values
import com.google.firestore.v1.Value

/**
 * Represents the results of a Pipeline query, including the data and metadata. It is usually
 * accessed via [Pipeline.Snapshot].
 */
@Beta
class PipelineResult
internal constructor(
  private val userDataWriter: UserDataWriter,
  ref: DocumentReference?,
  private val fields: Map<String, Value>,
  createTime: Timestamp?,
  updateTime: Timestamp?,
) {

  internal constructor(
    document: Document,
    serverTimestampBehavior: DocumentSnapshot.ServerTimestampBehavior,
    firestore: FirebaseFirestore
  ) : this(
    UserDataWriter(firestore, serverTimestampBehavior),
    DocumentReference(document.key, firestore),
    document.data.fieldsMap,
    document.createTime?.timestamp,
    document.version.timestamp
  )

  /** The time the document was created. Null if this result is not a document. */
  val createTime: Timestamp? = createTime

  /**
   * The time the document was last updated (at the time the snapshot was generated). Null if this
   * result is not a document.
   */
  val updateTime: Timestamp? = updateTime

  /**
   * The reference to the document, if the query returns the document id for a document. The name
   * field will be returned by default if querying a document.
   *
   * Document ids will not be returned if certain pipeline stages omit the document id. For example,
   * [Pipeline.select], [Pipeline.removeFields] and [Pipeline.aggregate] can omit the document id.
   *
   * @return [DocumentReference] Reference to the document, if applicable.
   */
  val ref: DocumentReference? = ref

  /**
   * Returns the ID of the document represented by this result. Returns null if this result is not
   * corresponding to a Firestore document.
   *
   * @return ID of document, if applicable.
   */
  fun getId(): String? = ref?.id

  /**
   * Retrieves all fields in the result as an object map.
   *
   * @return Map of field names to objects.
   */
  fun getData(): Map<String, Any?> = userDataWriter.convertObject(fields)

  private fun extractNestedValue(fieldPath: FieldPath): Value? {
    val segments = fieldPath.internalPath.iterator()
    if (!segments.hasNext()) {
      return Values.encodeValue(fields)
    }
    val firstSegment = segments.next()
    if (!fields.containsKey(firstSegment)) {
      return null
    }
    var value: Value? = fields[firstSegment]
    for (segment in segments) {
      if (value == null || !value.hasMapValue()) {
        return null
      }
      value = value.mapValue.getFieldsOrDefault(segment, null)
    }
    return value
  }

  /**
   * Retrieves the field specified by [field].
   *
   * @param field The field path (e.g. "foo" or "foo.bar") to a specific field.
   * @return The data at the specified field location or null if no such field exists.
   */
  fun get(field: String): Any? = get(FieldPath.fromDotSeparatedPath(field))

  /**
   * Retrieves the field specified by [fieldPath].
   *
   * @param fieldPath The field path to a specific field.
   * @return The data at the specified field location or null if no such field exists.
   */
  fun get(fieldPath: FieldPath): Any? = userDataWriter.convertValue(extractNestedValue(fieldPath))

  override fun toString() = "PipelineResult{ref=$ref, updateTime=$updateTime}, data=${getData()}"
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    other as PipelineResult
    if (ref != other.ref) return false
    if (fields != other.fields) return false
    return true
  }

  override fun hashCode(): Int {
    var result = ref?.hashCode() ?: 0
    result = 31 * result + fields.hashCode()
    return result
  }
}
