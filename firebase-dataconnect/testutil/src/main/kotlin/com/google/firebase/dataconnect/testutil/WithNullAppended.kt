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

package com.google.firebase.dataconnect.testutil

/**
 * Creates and returns a new [List] that contains all of the elements in the receiving [List] but
 * with a `null` element added to the end.
 */
@JvmName("withNullAppendedCollection")
fun <T> Collection<T>.withNullAppended(): List<T?> =
  buildList(size + 1) {
    addAll(this@withNullAppended)
    add(null)
  }

/**
 * Creates and returns a new [List] that contains all of the elements in the receiving [Iterable]
 * but with a `null` element added to the end.
 */
@JvmName("withNullAppendedIterable")
fun <T> Iterable<T>.withNullAppended(): List<T?> = buildList {
  addAll(this@withNullAppended)
  add(null)
}
