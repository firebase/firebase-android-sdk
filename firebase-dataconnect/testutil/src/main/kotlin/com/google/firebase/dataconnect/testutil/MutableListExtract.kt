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

/**
 * Removes all elements from the receiving list that satisfy the given predicate, and returns a list
 * containing the removed elements. The order of the elements in the returned list and the order in
 * which the given predicate will be called is undefined.
 */
fun <T> MutableList<T>.extract(predicate: (index: Int, element: T) -> Boolean): List<T> {
  // Shuffle the indices so that callers can not rely on a particular ordering of predicate calls.
  val indices = indices.shuffled()

  val indicesToRemove = indices.filter { predicate(it, get(it)) }

  val removedElements = indicesToRemove.sortedDescending().map { removeAt(it) }

  // Shuffle the returned list so that callers can not rely on a particular ordering.
  return removedElements.shuffled()
}
