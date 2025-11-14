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

package com.google.firebase.dataconnect.sqlite

import com.google.firebase.dataconnect.util.ProtoUtil.toCompactString
import com.google.protobuf.Struct
import java.util.Objects

internal object QueryResultCodec {

  class Entity(
    val id: String,
    val encodedId: ByteArray,
    val data: Struct,
  ) {

    override fun hashCode(): Int =
      Objects.hash(Entity::class.java, id, encodedId.contentHashCode(), data)

    override fun equals(other: Any?): Boolean =
      other is Entity &&
        other.id == id &&
        other.encodedId.contentEquals(encodedId) &&
        other.data == data

    override fun toString(): String =
      "Entity{id=$id, encodedId=${encodedId.contentToString()}, data=${data.toCompactString()}}"
  }

  const val QUERY_RESULT_HEADER: Int = 0x46444353 // "FDCS" in ASCII encoding
  const val VALUE_NULL: Byte = 1
  const val VALUE_NUMBER: Byte = 2
  const val VALUE_BOOL_TRUE: Byte = 3
  const val VALUE_BOOL_FALSE: Byte = 4
  const val VALUE_STRUCT: Byte = 5
  const val VALUE_LIST: Byte = 6
  const val VALUE_KIND_NOT_SET: Byte = 7
  const val VALUE_ENTITY: Byte = 8
  const val VALUE_STRING_EMPTY: Byte = 9
  const val VALUE_STRING_1BYTE: Byte = 10
  const val VALUE_STRING_2BYTE: Byte = 11
  const val VALUE_STRING_1CHAR: Byte = 12
  const val VALUE_STRING_2CHAR: Byte = 13
  const val VALUE_STRING_UTF8: Byte = 14
  const val VALUE_STRING_UTF16: Byte = 15
}
