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

import com.google.firebase.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.AppCheckToken
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.FirebaseAppCheck.AppCheckListener
import com.google.firebase.components.Component
import com.google.firebase.components.ComponentRegistrar
import com.google.firebase.ktx.Firebase
import com.google.firebase.platforminfo.LibraryVersionComponent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow

/** Returns the [FirebaseAppCheck] instance of the default [FirebaseApp]. */
val Firebase.appCheck: FirebaseAppCheck
  get() = FirebaseAppCheck.getInstance()

/** Returns the [FirebaseAppCheck] instance of a given [FirebaseApp]. */
fun Firebase.appCheck(app: FirebaseApp) = FirebaseAppCheck.getInstance(app)

/**
 * Registers an [AppCheckListener] to changes in the token state. This [Flow] should be used ONLY if
 * you need to authorize requests to a non-Firebase backend. Requests to Firebase backends are
 * authorized automatically if configured.
 *
 * Back-pressure is handled by dropping the oldest value in the buffer on overflow.
 */
val FirebaseAppCheck.tokenChanges: Flow<AppCheckToken>
  get() =
    callbackFlow {
        val tokenListener = AppCheckListener { appCheckToken -> trySendBlocking(appCheckToken) }

        addAppCheckListener(tokenListener)

        awaitClose { removeAppCheckListener(tokenListener) }
      }
      .buffer(capacity = Channel.CONFLATED)

/**
 * Destructuring declaration for [AppCheckToken] to provide token.
 *
 * @return the token of the [AppCheckToken]
 */
operator fun AppCheckToken.component1() = token

/**
 * Destructuring declaration for [AppCheckToken] to provide expireTimeMillis.
 *
 * @return the expireTimeMillis of the [AppCheckToken]
 */
operator fun AppCheckToken.component2() = expireTimeMillis

internal const val LIBRARY_NAME: String = "fire-app-check-ktx"

/** @suppress */
class FirebaseAppCheckKtxRegistrar : ComponentRegistrar {
  override fun getComponents(): List<Component<*>> =
    listOf(LibraryVersionComponent.create(LIBRARY_NAME, BuildConfig.VERSION_NAME))
}
