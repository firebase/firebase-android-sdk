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

internal data class SemanticVersion(val major: Int, val minor: Int, val patch: Int) :
  Comparable<SemanticVersion> {

  /**
   * Encodes this semantic version into a positive 32-bit integer such two [SemanticVersion]
   * instances will be ordered according to [compareTo] identically to that of the values returned
   * from their corresponding [encodeToInt] methods.
   *
   * Use [decodeFromInt] for the inverse operation.
   */
  fun encodeToInt(): Int = (major * 1000000) + (minor * 1000) + patch

  override fun toString() = "$major.$minor.$patch"

  override fun compareTo(other: SemanticVersion) = comparator.compare(this, other)

  companion object {

    val comparator: Comparator<SemanticVersion> = SemanticVersionComparator()

    private class SemanticVersionComparator : Comparator<SemanticVersion> {
      override fun compare(o1: SemanticVersion, o2: SemanticVersion): Int {
        run {
          val result = o1.major.compareTo(o2.major)
          if (result != 0) {
            return result
          }
        }
        run {
          val result = o1.minor.compareTo(o2.minor)
          if (result != 0) {
            return result
          }
        }
        return o1.patch.compareTo(o2.patch)
      }
    }

    /**
     * Creates an object equal to the original [SemanticVersion] whose [encodeToInt] method returned
     * the given integer.
     */
    fun decodeFromInt(value: Int): SemanticVersion {
      val major = value / 1000000
      val valueWithoutMajor = value - (major * 1000000)
      val minor = valueWithoutMajor / 1000
      val patch = valueWithoutMajor - (minor * 1000)
      return SemanticVersion(major, minor, patch)
    }
  }
}

internal fun Int.decodeSemanticVersion(): SemanticVersion = SemanticVersion.decodeFromInt(this)
