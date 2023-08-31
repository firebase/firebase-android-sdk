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

/** Returns the [FirebaseAppCheck] instance of the default [FirebaseApp]. */
@Deprecated(
  "com.google.firebase.appcheckktx.Firebase.appCheck has been deprecated. Use `com.google.firebase.appcheckFirebase.appCheck` instead.",
  ReplaceWith(
    expression = "Firebase.appCheck",
    imports = ["com.google.firebase.Firebase", "com.google.firebase.appcheckappCheck"]
  )
)
val Firebase.appCheck: FirebaseAppCheck
  get() = FirebaseAppCheck.getInstance()

/** Returns the [FirebaseAppCheck] instance of a given [FirebaseApp]. */
@Deprecated(
  "com.google.firebase.appcheckktx.Firebase.appCheck(app) has been deprecated. Use `com.google.firebase.appcheckFirebase.appCheck(app)` instead.",
  ReplaceWith(
    expression = "Firebase.appCheck(app)",
    imports = ["com.google.firebase.Firebase", "com.google.firebase.appcheckappCheck"]
  )
)
fun Firebase.appCheck(app: FirebaseApp) = FirebaseAppCheck.getInstance(app)

/**
 * Destructuring declaration for [AppCheckToken] to provide token.
 *
 * @return the token of the [AppCheckToken]
 */
@Deprecated(
  "com.google.firebase.appcheckktx.FirebaseAppCheckKtxRegistrar has been deprecated. Use `com.google.firebase.appcheckFirebaseAppCheckKtxRegistrar` instead.",
  ReplaceWith(
    expression = "FirebaseAppCheckKtxRegistrar",
    imports =
      ["com.google.firebase.Firebase", "com.google.firebase.appcheckFirebaseAppCheckKtxRegistrar"]
  )
)
operator fun AppCheckToken.component1() = token

/**
 * Destructuring declaration for [AppCheckToken] to provide expireTimeMillis.
 *
 * @return the expireTimeMillis of the [AppCheckToken]
 */
@Deprecated(
  "com.google.firebase.appcheckktx.FirebaseAppCheckKtxRegistrar has been deprecated. Use `com.google.firebase.appcheckFirebaseAppCheckKtxRegistrar` instead.",
  ReplaceWith(
    expression = "FirebaseAppCheckKtxRegistrar",
    imports =
      ["com.google.firebase.Firebase", "com.google.firebase.appcheckFirebaseAppCheckKtxRegistrar"]
  )
)
operator fun AppCheckToken.component2() = expireTimeMillis

/** @suppress */
@Deprecated(
  "com.google.firebase.appcheckktx.FirebaseAppCheckKtxRegistrar has been deprecated. Use `com.google.firebase.appcheckFirebaseAppCheckKtxRegistrar` instead.",
  ReplaceWith(
    expression = "FirebaseAppCheckKtxRegistrar",
    imports =
      ["com.google.firebase.Firebase", "com.google.firebase.appcheckFirebaseAppCheckKtxRegistrar"]
  )
)
class FirebaseAppCheckKtxRegistrar : ComponentRegistrar {
  override fun getComponents(): List<Component<*>> = listOf()
}
