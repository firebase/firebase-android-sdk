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
import com.google.firebase.platforminfo.LibraryVersionComponent

internal const val LIBRARY_NAME: String = "fire-fcm-ktx"

/** Returns the [FirebaseMessaging] instance of the default [FirebaseApp]. */
val Firebase.messaging: FirebaseMessaging
    get() = FirebaseMessaging.getInstance()

/** Returns a [RemoteMessage] instance initialized using the [init] function. */
inline fun remoteMessage(to: String, crossinline init: RemoteMessage.Builder.() -> Unit): RemoteMessage {
    val builder = RemoteMessage.Builder(to)
    builder.init()
    return builder.build()
}

/** @suppress */
class FirebaseMessagingKtxRegistrar : ComponentRegistrar {
    override fun getComponents(): List<Component<*>> =
            listOf(LibraryVersionComponent.create(LIBRARY_NAME, BuildConfig.VERSION_NAME))
}
