/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.database.ktx

import com.google.firebase.database.DataSnapshot

/**
 * Used to emit events about changes in the child locations of a given [Query] when using the
 * [childEvents] Flow.
 * @deprecated **Deprecation Notice:** The Kotlin extensions (KTX) APIs have been added to their
 * respective main modules, and the Kotlin extension (KTX) APIs in
 * `com.google.firebase.firebase-database-ktx` are now deprecated. As early as April 2024, we'll no
 * longer release KTX modules. For details, see the
 * [FAQ about this initiative.](https://firebase.google.com/docs/android/kotlin-migration)
 */
@Deprecated(
  "Migrate to use the KTX API from the main module: https://firebase.google.com/docs/android/kotlin-migration.",
  ReplaceWith("")
)
sealed class ChildEvent {
  /**
   * Emitted when a new child is added to the location.
   *
   * @param snapshot An immutable snapshot of the data at the new child location
   * @param previousChildName The key name of sibling location ordered before the new child. This
   * ```
   *     will be null for the first child node of a location.
   * ```
   * @deprecated **Deprecation Notice:** The Kotlin extensions (KTX) APIs have been added to their
   * respective main modules, and the Kotlin extension (KTX) APIs in
   * `com.google.firebase.firebase-database-ktx` are now deprecated. As early as April 2024, we'll
   * no longer release KTX modules. For details, see the
   * [FAQ about this initiative.](https://firebase.google.com/docs/android/kotlin-migration)
   */
  @Deprecated(
    "Migrate to use the KTX API from the main module: https://firebase.google.com/docs/android/kotlin-migration.",
    ReplaceWith("")
  )
  data class Added(val snapshot: DataSnapshot, val previousChildName: String?) : ChildEvent()

  /**
   * Emitted when the data at a child location has changed.
   *
   * @param snapshot An immutable snapshot of the data at the new data at the child location
   * @param previousChildName The key name of sibling location ordered before the child. This will
   * ```
   *     be null for the first child node of a location.
   * ```
   * @deprecated **Deprecation Notice:** The Kotlin extensions (KTX) APIs have been added to their
   * respective main modules, and the Kotlin extension (KTX) APIs in
   * `com.google.firebase.firebase-database-ktx` are now deprecated. As early as April 2024, we'll
   * no longer release KTX modules. For details, see the
   * [FAQ about this initiative.](https://firebase.google.com/docs/android/kotlin-migration)
   */
  @Deprecated(
    "Migrate to use the KTX API from the main module: https://firebase.google.com/docs/android/kotlin-migration.",
    ReplaceWith("")
  )
  data class Changed(val snapshot: DataSnapshot, val previousChildName: String?) : ChildEvent()

  /**
   * Emitted when a child is removed from the location.
   *
   * @param snapshot An immutable snapshot of the data at the child that was removed.
   * @deprecated **Deprecation Notice:** The Kotlin extensions (KTX) APIs have been added to their
   * respective main modules, and the Kotlin extension (KTX) APIs in
   * `com.google.firebase.firebase-database-ktx` are now deprecated. As early as April 2024, we'll
   * no longer release KTX modules. For details, see the
   * [FAQ about this initiative.](https://firebase.google.com/docs/android/kotlin-migration)
   */
  @Deprecated(
    "Migrate to use the KTX API from the main module: https://firebase.google.com/docs/android/kotlin-migration.",
    ReplaceWith("")
  )
  data class Removed(val snapshot: DataSnapshot) : ChildEvent()

  /**
   * Emitted when a child location's priority changes.
   *
   * @param snapshot An immutable snapshot of the data at the location that moved.
   * @param previousChildName The key name of the sibling location ordered before the child
   * ```
   *     location. This will be null if this location is ordered first.
   * ```
   * @deprecated **Deprecation Notice:** The Kotlin extensions (KTX) APIs have been added to their
   * respective main modules, and the Kotlin extension (KTX) APIs in
   * `com.google.firebase.firebase-database-ktx` are now deprecated. As early as April 2024, we'll
   * no longer release KTX modules. For details, see the
   * [FAQ about this initiative.](https://firebase.google.com/docs/android/kotlin-migration)
   */
  @Deprecated(
    "Migrate to use the KTX API from the main module: https://firebase.google.com/docs/android/kotlin-migration.",
    ReplaceWith("")
  )
  data class Moved(val snapshot: DataSnapshot, val previousChildName: String?) : ChildEvent()
}
