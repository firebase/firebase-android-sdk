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
import com.google.protobuf.Struct
import java.io.ByteArrayOutputStream
import java.io.DataOutput
import java.io.DataOutputStream

/**
 * This class is NOT thread safe. The behavior of an instance of this class when used concurrently
 * from multiple threads without external synchronization is undefined.
 */
internal class QueryResultEncoder(private val dataOutput: DataOutput) {

  val entities: MutableList<Entity> = mutableListOf()

  class EncodeResult(val byteArray: ByteArray, val entities: List<Entity>)

  fun encode(queryResult: Struct) {}

  companion object {

    fun encode(queryResult: Struct): EncodeResult =
      ByteArrayOutputStream().use { byteArrayOutputStream ->
        val entities =
          DataOutputStream(byteArrayOutputStream).use { dataOutputStream ->
            val encoder = QueryResultEncoder(dataOutputStream)
            encoder.encode(queryResult)
            encoder.entities
          }
        EncodeResult(byteArrayOutputStream.toByteArray(), entities)
      }
  }
}
