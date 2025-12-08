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

import kotlin.random.Random

fun <T> List<T>.randomPartitions(partitionCount: Int, random: Random): List<List<T>> =
  randomPartitions(this, partitionCount, random)

@JvmName("randomPartitionsPrivate")
private fun <T> randomPartitions(
  listToPartition: List<T>,
  partitionCount: Int,
  random: Random
): List<List<T>> {
  require(partitionCount > 0 && partitionCount <= listToPartition.size) {
    "invalid partitionCount: $partitionCount (must be between 1 and ${listToPartition.size})"
  }

  val offsets = buildList {
    addAll(listToPartition.indices)
    check(removeFirst() == 0)
    shuffle(random)
    while (size > partitionCount - 1) {
      removeLast()
    }
    add(0)
    add(listToPartition.size)
    sort()
  }

  val partitions = buildList {
    offsets.windowed(2) { window -> add(listToPartition.subList(window[0], window[1])) }
  }

  return partitions
}
