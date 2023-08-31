// Copyright 2020 Google LLC
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

package com.google.firebase.crashlytics.ktx

import androidx.annotation.Keep
import com.google.firebase.FirebaseApp
import com.google.firebase.components.Component
import com.google.firebase.components.ComponentRegistrar
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.ktx.Firebase

/** Returns the [FirebaseCrashlytics] instance of the default [FirebaseApp]. */
@Deprecated(
  "com.google.firebase.crashlyticsktx.Firebase.crashlytics has been deprecated. Use `com.google.firebase.crashlyticsFirebase.crashlytics` instead.",
  ReplaceWith(
    expression = "Firebase.crashlytics",
    imports = ["com.google.firebase.Firebase", "com.google.firebase.crashlyticscrashlytics"]
  )
)
val Firebase.crashlytics: FirebaseCrashlytics
  get() = FirebaseCrashlytics.getInstance()

/** Associates all key-value parameters with the reports */
@Deprecated(
  "com.google.firebase.crashlyticsktx.FirebaseCrashlytics.setCustomKeys(init) -> Unit) { has been deprecated. Use `com.google.firebase.crashlyticsFirebaseCrashlytics.setCustomKeys(init) -> Unit) {` instead.",
  ReplaceWith(
    expression = "FirebaseCrashlytics.setCustomKeys(init) -> Unit) {",
    imports =
      [
        "com.google.firebase.Firebase",
        "com.google.firebase.crashlyticsFirebaseCrashlytics.setCustomKeys"
      ]
  )
)
fun FirebaseCrashlytics.setCustomKeys(init: KeyValueBuilder.() -> Unit) {
  val builder = KeyValueBuilder(this)
  builder.init()
}

/** @suppress */
@Deprecated(
  "com.google.firebase.crashlyticsktx. has been deprecated. Use `com.google.firebase.crashlytics` instead.",
  ReplaceWith(
    expression = "",
    imports = ["com.google.firebase.Firebase", "com.google.firebase.crashlytics"]
  )
)
@Keep
internal class FirebaseCrashlyticsKtxRegistrar : ComponentRegistrar {
  override fun getComponents(): List<Component<*>> = listOf()

  companion object {}
}
