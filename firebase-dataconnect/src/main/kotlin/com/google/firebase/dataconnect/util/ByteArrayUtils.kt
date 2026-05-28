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

package com.google.firebase.dataconnect.util

/**
 * Compares this [ByteArray] with another [ByteArray] lexicographically based on the signed values
 * of their bytes.
 *
 * The comparison is performed byte-by-byte from left to right (index 0 upwards) until a difference
 * is found:
 * - If a byte difference is found, the result of comparing the signed values of those two bytes is
 * returned.
 * - If no difference is found up to the length of the shorter array, the shorter array is
 * considered lexicographically less than (smaller than) the longer one.
 * - If the arrays have the same size and identical elements, they are considered equal.
 *
 * This is a backport/alternative to `java.util.Arrays.compare(byte[], byte[])` which is only
 * available on Android API level 33 or greater.
 *
 * @param other The [ByteArray] to be compared with this one.
 * @return A negative integer, zero, or a positive integer as this [ByteArray] is lexicographically
 * less than, equal to, or greater than the other [ByteArray], respectively.
 */
internal fun ByteArray.contentCompareTo(other: ByteArray): Int = contentCompare(this, other)

// NOTE: Replace this method with Arrays.compare() once minSdkVersion is set to 33 or greater.
// At the time of writing (May 2026) minSdkVersion is 23, so I predict this will be around 2032 :)
private fun contentCompare(array1: ByteArray, array2: ByteArray): Int {
  if (array1 === array2) {
    return 0
  }

  val minSize = minOf(array1.size, array2.size)
  for (i in 0 until minSize) {
    val cmp = array1[i].compareTo(array2[i])
    if (cmp != 0) return cmp
  }

  return array1.size.compareTo(array2.size)
}
