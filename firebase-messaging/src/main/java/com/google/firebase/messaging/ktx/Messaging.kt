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

package com.google.firebase.messaging.ktx

import com.google.firebase.FirebaseApp
import com.google.firebase.components.Component
import com.google.firebase.components.ComponentRegistrar
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.RemoteMessage

/** Returns the [FirebaseMessaging] instance of the default [FirebaseApp]. */
@Deprecated(
  "Use `com.google.firebase.messaging` from the main module instead. The Kotlin extensions (KTX) APIs have been added to their respective main modules, and the Kotlin extension (KTX) APIs in `com.google.firebase.firebase-messaging-ktx` are now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the <a href=\"https://firebase.google.com/docs/android/ktx-apis-to-main-modules\">FAQ about this initiative.</a>",
  ReplaceWith(
    expression = "com.google.firebase.Firebase.messaging",
    imports = ["com.google.firebase.Firebase", "com.google.firebase.messaging.messaging"]
  )
)
val Firebase.messaging: FirebaseMessaging
  get() = FirebaseMessaging.getInstance()

/** Returns a [RemoteMessage] instance initialized using the [init] function. */
@Deprecated(
  "Use `com.google.firebase.messaging.remoteMessage(to, init)` from the main module instead. The Kotlin extensions (KTX) APIs have been added to their respective main modules, and the Kotlin extension (KTX) APIs in `com.google.firebase.firebase-messaging-ktx` are now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the <a href=\"https://firebase.google.com/docs/android/ktx-apis-to-main-modules\">FAQ about this initiative.</a>",
  ReplaceWith(
    expression = "remoteMessage(to, init)",
    imports = ["com.google.firebase.Firebase", "com.google.firebase.messaging.remoteMessage"]
  )
)
inline fun remoteMessage(
  to: String,
  crossinline init: RemoteMessage.Builder.() -> Unit
): RemoteMessage {
  val builder = RemoteMessage.Builder(to)
  builder.init()
  return builder.build()
}

/** @suppress */
@Deprecated(
  "Use `com.google.firebase.messaging.FirebaseMessagingKtxRegistrar` from the main module instead. The Kotlin extensions (KTX) APIs have been added to their respective main modules, and the Kotlin extension (KTX) APIs in `com.google.firebase.firebase-messaging-ktx` are now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the <a href=\"https://firebase.google.com/docs/android/ktx-apis-to-main-modules\">FAQ about this initiative.</a>",
  ReplaceWith(
    expression = "FirebaseMessagingKtxRegistrar",
    imports =
      [
        "com.google.firebase.Firebase",
        "com.google.firebase.messaging.FirebaseMessagingKtxRegistrar"
      ]
  )
)
class FirebaseMessagingKtxRegistrar : ComponentRegistrar {
  override fun getComponents(): List<Component<*>> = listOf()
}
