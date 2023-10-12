// Copyright 2022 Google LLC
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

package com.google.firebase.storage.ktx

/**
 * Used to emit events about the progress of storage tasks.
 * @deprecated **Deprecation Notice:** The Kotlin extensions (KTX) APIs have been added to their
 * respective main modules, and the Kotlin extension (KTX) APIs in
 * `com.google.firebase.firebase-storage-ktx` are now deprecated. As early as April 2024, we'll no
 * longer release KTX modules. For details, see the
 * [FAQ about this initiative.](https://firebase.google.com/docs/android/kotlin-migration)
 */
@Deprecated(
  "Use `com.google.firebase.storage.TaskState` from the main module instead.",
  ReplaceWith(
    expression = "TaskState<T>",
    imports = ["com.google.firebase.Firebase", "com.google.firebase.storage.TaskState"]
  )
)
abstract class TaskState<T> private constructor() {
  /**
   * Called periodically as data is transferred and can be used to populate an upload/download
   * indicator.
   * @deprecated **Deprecation Notice:** The Kotlin extensions (KTX) APIs have been added to their
   * respective main modules, and the Kotlin extension (KTX) APIs in
   * `com.google.firebase.firebase-storage-ktx` are now deprecated. As early as April 2024, we'll no
   * longer release KTX modules. For details, see the
   * [FAQ about this initiative.](https://firebase.google.com/docs/android/kotlin-migration)
   */
  @Deprecated(
    "Use `com.google.firebase.storage.TaskState.InProgress(snapshot)` from the main module instead.",
    ReplaceWith(
      expression = "InProgress<T>(snapshot)",
      imports = ["com.google.firebase.Firebase", "com.google.firebase.storage.TaskState.InProgress"]
    )
  )
  class InProgress<T>(val snapshot: T) : TaskState<T>()

  /**
   * Called any time the upload/download is paused.
   * @deprecated **Deprecation Notice:** The Kotlin extensions (KTX) APIs have been added to their
   * respective main modules, and the Kotlin extension (KTX) APIs in
   * `com.google.firebase.firebase-storage-ktx` are now deprecated. As early as April 2024, we'll no
   * longer release KTX modules. For details, see the
   * [FAQ about this initiative.](https://firebase.google.com/docs/android/kotlin-migration)
   */
  @Deprecated(
    "Use `com.google.firebase.storage.TaskState.Paused(snapshot)` from the main module instead.",
    ReplaceWith(
      expression = "Paused<T>(snapshot)",
      imports = ["com.google.firebase.Firebase", "com.google.firebase.storage.TaskState.Paused"]
    )
  )
  class Paused<T>(val snapshot: T) : TaskState<T>()
}
