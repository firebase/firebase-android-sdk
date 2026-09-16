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
 * These are deliberately a distinct type rather than reserved [Int] values. A numeric offset of
 * `0` is *not* equivalent to [CURRENT]: in a `range` frame, [CURRENT] cuts off strictly at the
 * current document's position, while an offset of `0` includes every document whose sort value
 * ties with the current one. Given documents with sort values `[10, 10, 10]`, a frame evaluated at
 * the second document with an unbounded lower bound yields the first two documents under
 * [CURRENT], but all three under an offset of `0`.
 *
 * Use these constants (or the [WindowSpec.CURRENT] / [WindowSpec.UNBOUNDED] aliases) for symbolic
 * bounds, and plain numbers for offsets:
 * ```
 * WindowSpec.documents(WindowSpec.UNBOUNDED, WindowSpec.CURRENT) // symbolic
 * WindowSpec.range(30, WindowSpec.CURRENT, "day")                // mixed
 * WindowSpec.documents(-1, 2)                                    // numeric offsets
 * ```
 */
enum class WindowBound {
  /** The current document's position in the frame. Encoded as the string `"current"`. */
  CURRENT,

  /** No boundary in this direction. Encoded as the string `"unbounded"`. */
  UNBOUNDED;

  /** The wire representation of this boundary. */
  internal fun wireName(): String =
    when (this) {
      CURRENT -> "current"
      UNBOUNDED -> "unbounded"
    }
}
