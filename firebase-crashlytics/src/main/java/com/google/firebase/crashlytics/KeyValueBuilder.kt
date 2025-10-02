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

/** Helper class to enable convenient syntax in [setCustomKeys] and [recordException] */
class KeyValueBuilder
private constructor(
  private val builder: CustomKeysAndValues.Builder,
) {
  internal constructor() : this(CustomKeysAndValues.Builder())

  internal fun build(): CustomKeysAndValues = builder.build()

  /** Sets a custom key and value that are associated with reports. */
  fun key(key: String, value: Boolean) {
    builder.putBoolean(key, value)
  }

  /** Sets a custom key and value that are associated with reports. */
  fun key(key: String, value: Double) {
    builder.putDouble(key, value)
  }

  /** Sets a custom key and value that are associated with reports. */
  fun key(key: String, value: Float) {
    builder.putFloat(key, value)
  }

  /** Sets a custom key and value that are associated with reports. */
  fun key(key: String, value: Int) {
    builder.putInt(key, value)
  }

  /** Sets a custom key and value that are associated with reports. */
  fun key(key: String, value: Long) {
    builder.putLong(key, value)
  }

  /** Sets a custom key and value that are associated with reports. */
  fun key(key: String, value: String) {
    builder.putString(key, value)
  }
}
