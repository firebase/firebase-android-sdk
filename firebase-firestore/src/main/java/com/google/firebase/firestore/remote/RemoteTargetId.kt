// Copyright 2026 Google LLC
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
package com.google.firebase.firestore.remote

/** Represents a transient, session-specific target ID used strictly over the watch stream. */
class RemoteTargetId private constructor(private val value: Int) : Comparable<RemoteTargetId> {
  fun value(): Int {
    return value
  }

  override fun equals(o: Any?): Boolean {
    if (this === o) {
      return true
    }
    if (o == null || javaClass != o.javaClass) {
      return false
    }
    return value == (o as RemoteTargetId).value
  }

  override fun hashCode(): Int {
    return value
  }

  override fun compareTo(other: RemoteTargetId): Int {
    return Integer.compare(value, other.value)
  }

  override fun toString(): String {
    return "RemoteTargetId(" + value + ")"
  }

  companion object {
    @JvmStatic
    fun from(value: Int): RemoteTargetId {
      return RemoteTargetId(value)
    }

    @JvmStatic
    fun from(values: List<Int>): List<RemoteTargetId> {
      val result: MutableList<RemoteTargetId> = ArrayList<RemoteTargetId>(values.size)
      for (value in values) {
        result.add(from(value))
      }
      return result
    }
  }
}
