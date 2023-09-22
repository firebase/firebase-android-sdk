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
  "Use `com.google.firebase.Firebase.crashlytics` from the main module instead. The Kotlin extensions (KTX) APIs have been added to their respective main modules, and the Kotlin extension (KTX) APIs in `com.google.firebase firebase-crashlytics-ktx` are now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the <a href=\"https://firebase.google.com/docs/android/ktx-apis-to-main-modules\">FAQ about this initiative.</a>",
  ReplaceWith(
    expression = "Firebase.crashlytics",
    imports = ["com.google.firebase.Firebase", "com.google.firebase.crashlytics.crashlytics"]
  )
)
val Firebase.crashlytics: FirebaseCrashlytics
  get() = FirebaseCrashlytics.getInstance()

/** Associates all key-value parameters with the reports */
@Deprecated(
  "Use `com.google.firebase.crashlytics.FirebaseCrashlytics.setCustomKeys(init)` from the main module instead. The Kotlin extensions (KTX) APIs have been added to their respective main modules, and the Kotlin extension (KTX) APIs in `com.google.firebase firebase-crashlytics-ktx` are now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the <a href=\"https://firebase.google.com/docs/android/ktx-apis-to-main-modules\">FAQ about this initiative.</a>",
  ReplaceWith(
    expression = "FirebaseCrashlytics.setCustomKeys(init)",
    imports =
      [
        "com.google.firebase.Firebase",
        "com.google.firebase.crashlytics.FirebaseCrashlytics.setCustomKeys"
      ]
  )
)
fun FirebaseCrashlytics.setCustomKeys(init: KeyValueBuilder.() -> Unit) {
  val builder = KeyValueBuilder(this)
  builder.init()
}

/** @suppress */
@Deprecated(
  "Use `com.google.firebase.crashlytics.FirebaseCrashlyticsKtxRegistrar` from the main module instead. The Kotlin extensions (KTX) APIs have been added to their respective main modules, and the Kotlin extension (KTX) APIs in `com.google.firebase firebase-crashlytics-ktx` are now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the <a href=\"https://firebase.google.com/docs/android/ktx-apis-to-main-modules\">FAQ about this initiative.</a>",
  ReplaceWith(
    expression = "FirebaseCrashlyticsKtxRegistrar",
    imports =
      [
        "com.google.firebase.Firebase",
        "com.google.firebase.crashlytics.FirebaseCrashlyticsKtxRegistrar"
      ]
  )
)
@Keep
internal class FirebaseCrashlyticsKtxRegistrar : ComponentRegistrar {
  override fun getComponents(): List<Component<*>> = listOf()

  companion object {}
}
