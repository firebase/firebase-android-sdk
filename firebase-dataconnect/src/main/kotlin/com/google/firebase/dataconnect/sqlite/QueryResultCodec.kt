/*
 * Copyright 2026 Google LLC
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

internal object QueryResultCodec {

  // The magic string is chosen such that it fails to parse by any UTF8 decoder that strictly
  // adheres to the standard. The first two bytes are an overlong encoding of the code point U+0046
  // ("Latin Capital Letter F"). The third byte, 0xF8, (1111 1000 in binary) indicates a 5-byte
  // sequence which does not exist in UTF-8, which has a maximum of 4 bytes in a sequence. The final
  // byte is simply a valid "continuation byte" for a multi-byte UTF-8 code point encoding.
  const val QUERY_RESULT_MAGIC: Int = 0xC186F880.toInt()

  const val VALUE_NULL: Byte = 1
  const val VALUE_KIND_NOT_SET: Byte = 2
  const val VALUE_ENTITY: Byte = 3

  const val VALUE_NUMBER_DOUBLE: Byte = 4
  const val VALUE_NUMBER_POSITIVE_ZERO: Byte = 5
  const val VALUE_NUMBER_NEGATIVE_ZERO: Byte = 6
  const val VALUE_NUMBER_FIXED32: Byte = 7
  const val VALUE_NUMBER_UINT32: Byte = 8
  const val VALUE_NUMBER_SINT32: Byte = 9
  const val VALUE_NUMBER_UINT64: Byte = 10
  const val VALUE_NUMBER_SINT64: Byte = 11

  const val VALUE_BOOL_TRUE: Byte = 12
  const val VALUE_BOOL_FALSE: Byte = 13

  const val VALUE_STRUCT: Byte = 14
  const val VALUE_LIST: Byte = 15
  const val VALUE_LIST_OF_ENTITIES: Byte = 16

  const val VALUE_STRING_EMPTY: Byte = 17
  const val VALUE_STRING_1BYTE: Byte = 18
  const val VALUE_STRING_2BYTE: Byte = 19
  const val VALUE_STRING_1CHAR: Byte = 20
  const val VALUE_STRING_2CHAR: Byte = 21
  const val VALUE_STRING_UTF8: Byte = 22
  const val VALUE_STRING_UTF16: Byte = 23

  const val VALUE_PATH_SEGMENT_FIELD: Byte = 24
  const val VALUE_PATH_SEGMENT_LIST_INDEX: Byte = 25
}
