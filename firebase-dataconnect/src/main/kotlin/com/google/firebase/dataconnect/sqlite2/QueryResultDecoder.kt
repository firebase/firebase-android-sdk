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

package com.google.firebase.dataconnect.sqlite2

import com.google.firebase.dataconnect.sqlite2.QueryResultCodec.Entity
import com.google.firebase.dataconnect.util.ProtoUtil.buildStructProto
import com.google.protobuf.Struct
import com.google.protobuf.Value
import java.io.ByteArrayInputStream
import java.io.DataInput
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer

/**
 * This class is NOT thread safe. The behavior of an instance of this class when used concurrently
 * from multiple threads without external synchronization is undefined.
 */
internal class QueryResultDecoder(
  private val dataInput: DataInput,
  private val entities: List<Entity>
) {

  private val charsetDecoder = Charsets.UTF_8.newDecoder()

  fun decode(): Struct = buildStructProto {
    val keyCount = dataInput.readStructKeyCount()
    repeat(keyCount) {
      val key = dataInput.readString()
      when (dataInput.readKindCase()) {
        Value.KindCase.NUMBER_VALUE -> put(key, dataInput.readDouble())
        Value.KindCase.BOOL_VALUE -> put(key, dataInput.readBoolean())
        Value.KindCase.NULL_VALUE -> putNull(key)
        else -> TODO()
      }
    }
  }

  private fun DataInput.readString(): String {
    val byteCount = dataInput.readStringByteCount()

    val encodedStringBytes = ByteArray(byteCount)
    readFully(encodedStringBytes)

    charsetDecoder.reset()
    val decodedString: CharBuffer = charsetDecoder.decode(ByteBuffer.wrap(encodedStringBytes))
    return decodedString.toString()
  }

  sealed class DecodeException(message: String) : Exception(message)

  class NegativeStructKeyCountException(message: String) : DecodeException(message)

  class NegativeStringByteCountException(message: String) : DecodeException(message)

  class UnknownKindCaseByteException(message: String) : DecodeException(message)

  companion object {

    fun decode(byteArray: ByteArray, entities: List<Entity>): Struct =
      ByteArrayInputStream(byteArray).use { byteArrayInputStream ->
        DataInputStream(byteArrayInputStream).use { dataInputStream ->
          val decoder = QueryResultDecoder(dataInputStream, entities)
          decoder.decode()
        }
      }

    private fun DataInput.readStructKeyCount(): Int =
      readInt().also {
        if (it < 0) {
          throw NegativeStructKeyCountException(
            "read struct key count $it, but expected " +
              "a number greater than or equal to zero [y9253xj96g]"
          )
        }
      }

    private fun DataInput.readStringByteCount(): Int =
      readInt().also {
        if (it < 0) {
          throw NegativeStringByteCountException(
            "read string byte count $it, but expected " +
              "a number greater than or equal to zero [a9kma55y7m]"
          )
        }
      }

    private val kindCaseByKindCaseInt: Map<Byte, Value.KindCase> =
      mapOf(
        QueryResultCodec.VALUE_NUMBER to Value.KindCase.NUMBER_VALUE,
        QueryResultCodec.VALUE_BOOL to Value.KindCase.BOOL_VALUE,
        QueryResultCodec.VALUE_NULL to Value.KindCase.NULL_VALUE,
      )

    private fun DataInput.readKindCase(): Value.KindCase =
      readByte().let { byte ->
        val kindCase = kindCaseByKindCaseInt[byte]
        if (kindCase === null) {
          throw UnknownKindCaseByteException(
            "read unknown kind case byte $byte, but expected one of " +
              kindCaseByKindCaseInt.keys.sorted().joinToString { it.toString() } +
              " [pmkb3sc2mn]"
          )
        }
        kindCase
      }
  }
}
