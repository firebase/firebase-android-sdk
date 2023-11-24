// Copyright 2023 Google LLC
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
package com.google.firebase.dataconnect

import com.google.protobuf.Struct
import com.google.protobuf.Value
import com.google.protobuf.Value.KindCase
import java.io.DataOutputStream
import java.security.DigestOutputStream
import java.security.MessageDigest

fun calculateSha512(struct: Struct): ByteArray =
  calculateSha512(Value.newBuilder().setStructValue(struct).build())

fun calculateSha512(value: Value): ByteArray {
  val digest = MessageDigest.getInstance("SHA-512")
  val out = DataOutputStream(DigestOutputStream(NullOutputStream, digest))

  val calculateDigest =
    DeepRecursiveFunction<Value, Unit> {
      val kind = it.kindCase
      out.writeInt(kind.ordinal)

      when (kind) {
        KindCase.NULL_VALUE -> {
          /* nothing to write for null */
        }
        KindCase.BOOL_VALUE -> out.writeBoolean(it.boolValue)
        KindCase.NUMBER_VALUE -> out.writeDouble(it.numberValue)
        KindCase.STRING_VALUE -> out.writeUTF(it.stringValue)
        KindCase.LIST_VALUE ->
          it.listValue.valuesList.forEachIndexed { index, elementValue ->
            out.writeInt(index)
            callRecursive(elementValue)
          }
        KindCase.STRUCT_VALUE ->
          it.structValue.fieldsMap.entries
            .sortedBy { (key, _) -> key }
            .forEach { (key, elementValue) ->
              out.writeUTF(key)
              callRecursive(elementValue)
            }
        else -> throw IllegalArgumentException("unsupported kind: $kind")
      }
    }

  calculateDigest(value)
  return digest.digest()
}
