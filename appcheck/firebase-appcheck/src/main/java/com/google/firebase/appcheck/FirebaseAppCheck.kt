/*
 * Copyright 2022 Google LLC
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

package com.google.firebase.appcheck

import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.components.Component
import com.google.firebase.components.ComponentRegistrar

/** Returns the [FirebaseAppCheck] instance of the default [FirebaseApp]. */
val Firebase.appCheck: FirebaseAppCheck
  get() = FirebaseAppCheck.getInstance()

/** Returns the [FirebaseAppCheck] instance of a given [FirebaseApp]. */
fun Firebase.appCheck(app: FirebaseApp) = FirebaseAppCheck.getInstance(app)

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

/** @suppress */
class FirebaseAppCheckKtxRegistrar : ComponentRegistrar {
  override fun getComponents(): List<Component<*>> = listOf()
}
