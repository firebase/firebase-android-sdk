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
  "com.google.firebase.messagingktx.Firebase.messaging has been deprecated. Use `com.google.firebase.messagingFirebase.messaging` instead.",
  ReplaceWith(
    expression = "Firebase.messaging",
    imports = ["com.google.firebase.Firebase", "com.google.firebase.messagingmessaging"]
  )
)
val Firebase.messaging: FirebaseMessaging
  get() = FirebaseMessaging.getInstance()

/** Returns a [RemoteMessage] instance initialized using the [init] function. */
@Deprecated(
  "com.google.firebase.messagingktx.FirebaseMessagingKtxRegistrar has been deprecated. Use `com.google.firebase.messagingFirebaseMessagingKtxRegistrar` instead.",
  ReplaceWith(
    expression = "FirebaseMessagingKtxRegistrar",
    imports =
      ["com.google.firebase.Firebase", "com.google.firebase.messagingFirebaseMessagingKtxRegistrar"]
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
  "com.google.firebase.messagingktx.FirebaseMessagingKtxRegistrar has been deprecated. Use `com.google.firebase.messagingFirebaseMessagingKtxRegistrar` instead.",
  ReplaceWith(
    expression = "FirebaseMessagingKtxRegistrar",
    imports =
      ["com.google.firebase.Firebase", "com.google.firebase.messagingFirebaseMessagingKtxRegistrar"]
  )
)
class FirebaseMessagingKtxRegistrar : ComponentRegistrar {
  override fun getComponents(): List<Component<*>> = listOf()
}
