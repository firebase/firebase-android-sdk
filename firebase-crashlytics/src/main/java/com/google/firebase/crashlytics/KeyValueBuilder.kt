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

package com.google.firebase.crashlytics

/** Helper class to enable fluent syntax in [setCustomKeys] */
@Suppress("DEPRECATION")
class KeyValueBuilder(
  // TODO(mrober): Remove this param and make ctor internal in 2025.
  @Deprecated(message = "The crashlytics instance is no longer needed and will be removed in 2025.")
  private val crashlytics: FirebaseCrashlytics? = null
) {
  private val builder = CustomKeysAndValues.Builder()

  internal fun build(): CustomKeysAndValues = builder.build()

  /** Sets a custom key and value that are associated with reports. */
  fun key(key: String, value: Boolean) {
    crashlytics?.setCustomKey(key, value) ?: builder.putBoolean(key, value)
  }

  /** Sets a custom key and value that are associated with reports. */
  fun key(key: String, value: Double) {
    crashlytics?.setCustomKey(key, value) ?: builder.putDouble(key, value)
  }

  /** Sets a custom key and value that are associated with reports. */
  fun key(key: String, value: Float) {
    crashlytics?.setCustomKey(key, value) ?: builder.putFloat(key, value)
  }

  /** Sets a custom key and value that are associated with reports. */
  fun key(key: String, value: Int) {
    crashlytics?.setCustomKey(key, value) ?: builder.putInt(key, value)
  }

  /** Sets a custom key and value that are associated with reports. */
  fun key(key: String, value: Long) {
    crashlytics?.setCustomKey(key, value) ?: builder.putLong(key, value)
  }

  /** Sets a custom key and value that are associated with reports. */
  fun key(key: String, value: String) {
    crashlytics?.setCustomKey(key, value) ?: builder.putString(key, value)
  }
}
