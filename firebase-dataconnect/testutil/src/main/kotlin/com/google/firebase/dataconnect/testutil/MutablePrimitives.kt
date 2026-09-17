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
package com.google.firebase.dataconnect.testutil

class MutableBoolean(var value: Boolean) {
  override fun toString() = value.toString()

  override fun hashCode() = value.hashCode()

  override fun equals(other: Any?) = other is MutableBoolean && other.value == value
}

class MutableByte(var value: Byte) {
  override fun toString() = value.toString()

  override fun hashCode() = value.hashCode()

  override fun equals(other: Any?) = other is MutableByte && other.value == value
}

class MutableShort(var value: Short) {
  override fun toString() = value.toString()

  override fun hashCode() = value.hashCode()

  override fun equals(other: Any?) = other is MutableShort && other.value == value
}

class MutableInt(var value: Int) {
  override fun toString() = value.toString()

  override fun hashCode() = value.hashCode()

  override fun equals(other: Any?) = other is MutableInt && other.value == value
}

class MutableLong(var value: Long) {
  override fun toString() = value.toString()

  override fun hashCode() = value.hashCode()

  override fun equals(other: Any?) = other is MutableLong && other.value == value
}

class MutableChar(var value: Char) {
  override fun toString() = value.toString()

  override fun hashCode() = value.hashCode()

  override fun equals(other: Any?) = other is MutableChar && other.value == value
}

class MutableFloat(var value: Float) {
  override fun toString() = value.toString()

  override fun hashCode() = value.hashCode()

  // Use compareTo instead of == because == is false for NaN == NaN (violating reflexivity)
  // and true for -0.0f == 0.0f (violating equals/hashCode contract since they have different hash
  // codes).
  override fun equals(other: Any?) = other is MutableFloat && value.compareTo(other.value) == 0
}

class MutableDouble(var value: Double) {
  override fun toString() = value.toString()

  override fun hashCode() = value.hashCode()

  // Use compareTo instead of == because == is false for NaN == NaN (violating reflexivity)
  // and true for -0.0 == 0.0 (violating equals/hashCode contract since they have different hash
  // codes).
  override fun equals(other: Any?) = other is MutableDouble && value.compareTo(other.value) == 0
}

class MutableReference<T>(var value: T) {
  override fun toString() = value.toString()

  override fun hashCode() = value.hashCode()

  override fun equals(other: Any?) = other is MutableReference<*> && other.value == value
}
