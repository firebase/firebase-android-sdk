// Copyright 2019 Google LLC
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

package com.google.firebase.inappmessaging.display.ktx

import androidx.annotation.Keep
import com.google.firebase.FirebaseApp
import com.google.firebase.components.Component
import com.google.firebase.components.ComponentRegistrar
import com.google.firebase.inappmessaging.display.FirebaseInAppMessagingDisplay
import com.google.firebase.ktx.Firebase

/**
 * Returns the [FirebaseInAppMessagingDisplay] instance of the default [FirebaseApp].
 * @deprecated **Deprecation Notice:** The Kotlin extensions (KTX) APIs have been added to their
 * respective main modules, and the Kotlin extension (KTX) APIs in
 * `com.google.firebase.firebase-inappmessaging-display-ktx` are now deprecated. As early as April
 * 2024, we'll no longer release KTX modules. For details, see the
 * [FAQ about this initiative.](https://firebase.google.com/docs/android/ktx-apis-to-main-modules)
 */
@Deprecated(
  "Use `com.google.firebase.Firebase.inAppMessagingDisplay` from the main module instead.",
  ReplaceWith(
    expression = "com.google.firebase.Firebase.inAppMessagingDisplay",
    imports =
      [
        "com.google.firebase.Firebase",
        "com.google.firebase.inappmessaging.display.inAppMessagingDisplay"
      ]
  )
)
val Firebase.inAppMessagingDisplay: FirebaseInAppMessagingDisplay
  get() = FirebaseInAppMessagingDisplay.getInstance()

/**
 * @suppress
 * @deprecated **Deprecation Notice:** The Kotlin extensions (KTX) APIs have been added to their
 * respective main modules, and the Kotlin extension (KTX) APIs in
 * `com.google.firebase.firebase-inappmessaging-display-ktx` are now deprecated. As early as April
 * 2024, we'll no longer release KTX modules. For details, see the
 * [FAQ about this initiative.](https://firebase.google.com/docs/android/ktx-apis-to-main-modules)
 */
@Deprecated(
  "Use `com.google.firebase.inappmessaging.display.FirebaseInAppMessagingDisplayKtxRegistrar` from the main module instead.",
  ReplaceWith(
    expression = "FirebaseInAppMessagingDisplayKtxRegistrar",
    imports =
      [
        "com.google.firebase.Firebase",
        "com.google.firebase.inappmessaging.display.FirebaseInAppMessagingDisplayKtxRegistrar"
      ]
  )
)
@Keep
class FirebaseInAppMessagingDisplayKtxRegistrar : ComponentRegistrar {
  override fun getComponents(): List<Component<*>> = listOf()
}
