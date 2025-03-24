// Copyright 2025 Google LLC
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

package com.google.firebase.firestore.pipeline

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.UserDataReader
import com.google.firebase.firestore.VectorValue
import com.google.firebase.firestore.model.Values
import com.google.firebase.firestore.model.Values.encodeValue
import com.google.firestore.v1.Value
import java.util.Date

abstract class Constant internal constructor() : Expr() {

  private class ValueConstant(val value: Value) : Constant() {
    override fun toProto(userDataReader: UserDataReader): Value = value
  }

  companion object {
    internal val NULL: Constant = ValueConstant(Values.NULL_VALUE)

    internal fun of(value: Value): Constant {
      return ValueConstant(value)
    }

    @JvmStatic
    fun of(value: String): Constant {
      return ValueConstant(encodeValue(value))
    }

    @JvmStatic
    fun of(value: Number): Constant {
      return ValueConstant(encodeValue(value))
    }

    @JvmStatic
    fun of(value: Date): Constant {
      return ValueConstant(encodeValue(value))
    }

    @JvmStatic
    fun of(value: Timestamp): Constant {
      return ValueConstant(encodeValue(value))
    }

    @JvmStatic
    fun of(value: Boolean): Constant {
      return ValueConstant(encodeValue(value))
    }

    @JvmStatic
    fun of(value: GeoPoint): Constant {
      return ValueConstant(encodeValue(value))
    }

    @JvmStatic
    fun of(value: Blob): Constant {
      return ValueConstant(encodeValue(value))
    }

    @JvmStatic
    fun of(ref: DocumentReference): Constant {
      return object : Constant() {
        override fun toProto(userDataReader: UserDataReader): Value {
          userDataReader.validateDocumentReference(ref, ::IllegalArgumentException)
          return encodeValue(ref)
        }
      }
    }

    @JvmStatic
    fun of(value: VectorValue): Constant {
      return ValueConstant(encodeValue(value))
    }

    @JvmStatic
    fun nullValue(): Constant {
      return NULL
    }

    @JvmStatic
    fun vector(value: DoubleArray): Constant {
      return ValueConstant(Values.encodeVectorValue(value))
    }

    @JvmStatic
    fun vector(value: VectorValue): Constant {
      return ValueConstant(encodeValue(value))
    }
  }
}
