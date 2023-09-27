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

package com.google.firebase.appcheck.ktx

import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.AppCheckToken
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.components.Component
import com.google.firebase.components.ComponentRegistrar
import com.google.firebase.ktx.Firebase
import com.google.firebase.ktx.app

/** Returns the [FirebaseAppCheck] instance of the default [FirebaseApp]. */
@Deprecated(
  "Use `com.google.firebase.appcheck.Firebase.appCheck` from the main module instead. The Kotlin extensions (KTX) APIs have been added to their respective main modules, and the Kotlin extension (KTX) APIs in `com.google.firebase.firebase-appcheck-ktx` are now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the [FAQ about this initiative.](https://firebase.google.com/docs/android/ktx-apis-to-main-modules){:.external}",
  ReplaceWith(
    expression = "com.google.firebase.Firebase.appCheck",
    imports = ["com.google.firebase.Firebase", "com.google.firebase.appcheck.appCheck"]
  )
)
val Firebase.appCheck: FirebaseAppCheck
  get() = FirebaseAppCheck.getInstance()

/** Returns the [FirebaseAppCheck] instance of a given [FirebaseApp]. */
@Deprecated(
  "Use `com.google.firebase.appcheck.Firebase.appCheck(app)` from the main module instead. The Kotlin extensions (KTX) APIs have been added to their respective main modules, and the Kotlin extension (KTX) APIs in `com.google.firebase.firebase-appcheck-ktx` are now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the [FAQ about this initiative.](https://firebase.google.com/docs/android/ktx-apis-to-main-modules){:.external}",
  ReplaceWith(
    expression = "com.google.firebase.Firebase.appCheck(app)",
    imports = ["com.google.firebase.Firebase", "com.google.firebase.appcheck.appCheck"]
  )
)
fun Firebase.appCheck(app: FirebaseApp) = FirebaseAppCheck.getInstance(app)

/**
 * Destructuring declaration for [AppCheckToken] to provide token.
 *
 * @return the token of the [AppCheckToken]
 */
@Deprecated(
  "Use `com.google.firebase.appcheck.AppCheckToken.component1` from the main module instead. The Kotlin extensions (KTX) APIs have been added to their respective main modules, and the Kotlin extension (KTX) APIs in `com.google.firebase.firebase-appcheck-ktx` are now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the [FAQ about this initiative.](https://firebase.google.com/docs/android/ktx-apis-to-main-modules){:.external}",
  ReplaceWith(expression = "component1()", imports = ["com.google.firebase.appcheck.component1"])
)
operator fun AppCheckToken.component1() = token

/**
 * Destructuring declaration for [AppCheckToken] to provide expireTimeMillis.
 *
 * @return the expireTimeMillis of the [AppCheckToken]
 */
@Deprecated(
  "Use `com.google.firebase.appcheck.AppCheckToken.component2` from the main module instead. The Kotlin extensions (KTX) APIs have been added to their respective main modules, and the Kotlin extension (KTX) APIs in `com.google.firebase.firebase-appcheck-ktx` are now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the [FAQ about this initiative.](https://firebase.google.com/docs/android/ktx-apis-to-main-modules){:.external}",
  ReplaceWith(expression = "component2()", imports = ["com.google.firebase.appcheck.component2"])
)
operator fun AppCheckToken.component2() = expireTimeMillis

/** @suppress */
@Deprecated(
  "Use `com.google.firebase.appcheck.FirebaseAppCheckKtxRegistrar` from the main module instead. The Kotlin extensions (KTX) APIs have been added to their respective main modules, and the Kotlin extension (KTX) APIs in `com.google.firebase.firebase-appcheck-ktx` are now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the [FAQ about this initiative.](https://firebase.google.com/docs/android/ktx-apis-to-main-modules){:.external}",
  ReplaceWith(
    expression = "FirebaseAppCheckKtxRegistrar",
    imports =
      ["com.google.firebase.Firebase", "com.google.firebase.appcheck.FirebaseAppCheckKtxRegistrar"]
  )
)
class FirebaseAppCheckKtxRegistrar : ComponentRegistrar {
  override fun getComponents(): List<Component<*>> = listOf()
}
