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

/**
 * Represents a constant value that can be used in a Firestore pipeline expression.
 *
 * You can create a [Constant] instance using the static [of] method:
 */
abstract class Constant internal constructor() : Expr() {

  private class ValueConstant(val value: Value) : Constant() {
    override fun toProto(userDataReader: UserDataReader): Value = value
  }

  companion object {
    internal val NULL: Constant = ValueConstant(Values.NULL_VALUE)

    internal fun of(value: Value): Constant {
      return ValueConstant(value)
    }

    /**
     * Create a [Constant] instance for a [String] value.
     *
     * @param value The [String] value.
     * @return A new [Constant] instance.
     */
    @JvmStatic
    fun of(value: String): Constant {
      return ValueConstant(encodeValue(value))
    }

    /**
     * Create a [Constant] instance for a [Number] value.
     *
     * @param value The [Number] value.
     * @return A new [Constant] instance.
     */
    @JvmStatic
    fun of(value: Number): Constant {
      return ValueConstant(encodeValue(value))
    }

    /**
     * Create a [Constant] instance for a [Date] value.
     *
     * @param value The [Date] value.
     * @return A new [Constant] instance.
     */
    @JvmStatic
    fun of(value: Date): Constant {
      return ValueConstant(encodeValue(value))
    }

    /**
     * Create a [Constant] instance for a [Timestamp] value.
     *
     * @param value The [Timestamp] value.
     * @return A new [Constant] instance.
     */
    @JvmStatic
    fun of(value: Timestamp): Constant {
      return ValueConstant(encodeValue(value))
    }

    /**
     * Create a [Constant] instance for a [Boolean] value.
     *
     * @param value The [Boolean] value.
     * @return A new [Constant] instance.
     */
    @JvmStatic
    fun of(value: Boolean): Constant {
      return ValueConstant(encodeValue(value))
    }

    /**
     * Create a [Constant] instance for a [GeoPoint] value.
     *
     * @param value The [GeoPoint] value.
     * @return A new [Constant] instance.
     */
    @JvmStatic
    fun of(value: GeoPoint): Constant {
      return ValueConstant(encodeValue(value))
    }

    /**
     * Create a [Constant] instance for a [Blob] value.
     *
     * @param value The [Blob] value.
     * @return A new [Constant] instance.
     */
    @JvmStatic
    fun of(value: Blob): Constant {
      return ValueConstant(encodeValue(value))
    }

    /**
     * Create a [Constant] instance for a [DocumentReference] value.
     *
     * @param ref The [DocumentReference] value.
     * @return A new [Constant] instance.
     */
    @JvmStatic
    fun of(ref: DocumentReference): Constant {
      return object : Constant() {
        override fun toProto(userDataReader: UserDataReader): Value {
          userDataReader.validateDocumentReference(ref, ::IllegalArgumentException)
          return encodeValue(ref)
        }
      }
    }

    /**
     * Create a [Constant] instance for a [VectorValue] value.
     *
     * @param value The [VectorValue] value.
     * @return A new [Constant] instance.
     */
    @JvmStatic
    fun of(value: VectorValue): Constant {
      return ValueConstant(encodeValue(value))
    }

    /**
     * [Constant] instance for a null value.
     *
     * @return A [Constant] instance.
     */
    @JvmStatic
    fun nullValue(): Constant {
      return NULL
    }

    /**
     * Create a vector [Constant] instance for a [DoubleArray] value.
     *
     * @param vector The [VectorValue] value.
     * @return A new [Constant] instance.
     */
    @JvmStatic
    fun vector(vector: DoubleArray): Constant {
      return ValueConstant(Values.encodeVectorValue(vector))
    }

    /**
     * Create a vector [Constant] instance for a [VectorValue] value.
     *
     * @param vector The [VectorValue] value.
     * @return A new [Constant] instance.
     */
    @JvmStatic
    fun vector(vector: VectorValue): Constant {
      return ValueConstant(encodeValue(vector))
    }
  }
}
