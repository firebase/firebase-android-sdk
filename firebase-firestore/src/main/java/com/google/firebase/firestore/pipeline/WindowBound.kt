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

package com.google.firebase.firestore.pipeline

/**
 * A symbolic window frame boundary, as opposed to a numeric offset.
 *
 * Use these constants for symbolic bounds, and plain numbers for offsets:
 * ```
 * WindowSpec().documents(WindowBound.UNBOUNDED, WindowBound.CURRENT) // symbolic
 * WindowSpec().range(30, WindowBound.CURRENT, "day")                 // mixed
 * WindowSpec().documents(-1, 2)                                      // numeric offsets
 * ```
 *
 * In a `documents` frame, [CURRENT] refers strictly to the current document's position (documents
 * with tied sort values are not included). In a `range` frame, [CURRENT] is peer-inclusive (like
 * SQL `CURRENT ROW` in `RANGE` mode): it includes the current document and all peer documents whose
 * sort value(s) tie with the current document, making it semantically equivalent to a numeric
 * offset of `0` (though [CURRENT] and `0` still encode differently on the wire as `"current"` vs
 * `0`).
 */
class WindowBound private constructor(internal val value: String) {
  companion object {
    /**
     * The current document's position in a `documents` frame, or the current document and all peers
     * with tied sort values in a `range` frame.
     */
    @JvmField val CURRENT = WindowBound("current")

    /** No boundary in this direction. */
    @JvmField val UNBOUNDED = WindowBound("unbounded")
  }

  override fun equals(other: Any?): Boolean =
    this === other || (other is WindowBound && value == other.value)

  override fun hashCode(): Int = value.hashCode()

  override fun toString(): String = "WindowBound($value)"
}
