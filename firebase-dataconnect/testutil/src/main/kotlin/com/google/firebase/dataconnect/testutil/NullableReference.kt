/*
 * Copyright 2024 Google LLC
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

/**
 * A class that simply wraps a reference to another object, which may be null. This class can be
 * useful for use in the case where the meaning of `null` is overloaded, such as
 * [io.kotest.property.Arb.edgecase] and [kotlinx.coroutines.flow.MutableStateFlow.compareAndSet]
 */
class NullableReference<out T>(val ref: T? = null) {
  override fun equals(other: Any?) = (other is NullableReference<*>) && other.ref == ref
  override fun hashCode() = ref?.hashCode() ?: 0
  override fun toString() = ref?.toString() ?: "null"
}
