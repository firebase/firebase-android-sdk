/*
 * Copyright 2020 Google LLC
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

package com.google.firebase.crashlytics.ktx

import com.google.firebase.crashlytics.FirebaseCrashlytics

/** Helper class to enable fluent syntax in [setCustomKeys] */
@Deprecated(
  "Use `com.google.firebase.crashlytics.KeyValueBuilder` from the main module.",
)
class KeyValueBuilder(private val crashlytics: FirebaseCrashlytics) {

  /**
   * Sets a custom key and value that are associated with reports.
   * @deprecated **Deprecation Notice:** The Kotlin extensions (KTX) APIs have been added to their
   * respective main modules, and the Kotlin extension (KTX) APIs in
   * `com.google.firebase:firebase-crashlytics-ktx` are now deprecated. As early as April 2024,
   * we'll no longer release KTX modules. For details, see the
   * [FAQ about this initiative.](https://firebase.google.com/docs/android/kotlin-migration)
   */
  @Deprecated(
    "Use `com.google.firebase.crashlytics.KeyValueBuilder.key(key, value)` from the main module.",
    ReplaceWith("")
  )
  fun key(key: String, value: Boolean) = crashlytics.setCustomKey(key, value)

  /**
   * Sets a custom key and value that are associated with reports.
   * @deprecated **Deprecation Notice:** The Kotlin extensions (KTX) APIs have been added to their
   * respective main modules, and the Kotlin extension (KTX) APIs in
   * `com.google.firebase:firebase-crashlytics-ktx` are now deprecated. As early as April 2024,
   * we'll no longer release KTX modules. For details, see the
   * [FAQ about this initiative.](https://firebase.google.com/docs/android/kotlin-migration)
   */
  @Deprecated(
    "Use `com.google.firebase.crashlytics.KeyValueBuilder.key(key, value)` from the main module.",
    ReplaceWith("")
  )
  fun key(key: String, value: Double) = crashlytics.setCustomKey(key, value)

  /**
   * Sets a custom key and value that are associated with reports.
   * @deprecated **Deprecation Notice:** The Kotlin extensions (KTX) APIs have been added to their
   * respective main modules, and the Kotlin extension (KTX) APIs in
   * `com.google.firebase:firebase-crashlytics-ktx` are now deprecated. As early as April 2024,
   * we'll no longer release KTX modules. For details, see the
   * [FAQ about this initiative.](https://firebase.google.com/docs/android/kotlin-migration)
   */
  @Deprecated(
    "Use `com.google.firebase.crashlytics.KeyValueBuilder.key(key, value)` from the main module.",
    ReplaceWith("")
  )
  fun key(key: String, value: Float) = crashlytics.setCustomKey(key, value)

  /**
   * Sets a custom key and value that are associated with reports.
   * @deprecated **Deprecation Notice:** The Kotlin extensions (KTX) APIs have been added to their
   * respective main modules, and the Kotlin extension (KTX) APIs in
   * `com.google.firebase:firebase-crashlytics-ktx` are now deprecated. As early as April 2024,
   * we'll no longer release KTX modules. For details, see the
   * [FAQ about this initiative.](https://firebase.google.com/docs/android/kotlin-migration)
   */
  @Deprecated(
    "Use `com.google.firebase.crashlytics.KeyValueBuilder.key(key, value)` from the main module.",
    ReplaceWith("")
  )
  fun key(key: String, value: Int) = crashlytics.setCustomKey(key, value)

  /**
   * Sets a custom key and value that are associated with reports.
   * @deprecated **Deprecation Notice:** The Kotlin extensions (KTX) APIs have been added to their
   * respective main modules, and the Kotlin extension (KTX) APIs in
   * `com.google.firebase:firebase-crashlytics-ktx` are now deprecated. As early as April 2024,
   * we'll no longer release KTX modules. For details, see the
   * [FAQ about this initiative.](https://firebase.google.com/docs/android/kotlin-migration)
   */
  @Deprecated(
    "Use `com.google.firebase.crashlytics.KeyValueBuilder.key(key, value)` from the main module.",
    ReplaceWith("")
  )
  fun key(key: String, value: Long) = crashlytics.setCustomKey(key, value)

  /**
   * Sets a custom key and value that are associated with reports.
   * @deprecated **Deprecation Notice:** The Kotlin extensions (KTX) APIs have been added to their
   * respective main modules, and the Kotlin extension (KTX) APIs in
   * `com.google.firebase:firebase-crashlytics-ktx` are now deprecated. As early as April 2024,
   * we'll no longer release KTX modules. For details, see the
   * [FAQ about this initiative.](https://firebase.google.com/docs/android/kotlin-migration)
   */
  @Deprecated(
    "Use `com.google.firebase.crashlytics.KeyValueBuilder.key(key, value)` from the main module.",
    ReplaceWith("")
  )
  fun key(key: String, value: String) = crashlytics.setCustomKey(key, value)
}
