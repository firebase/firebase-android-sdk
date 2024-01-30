/*
 * Copyright 2019 Google LLC
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

package com.google.firebase.functions.ktx

import androidx.annotation.Keep
import com.google.firebase.FirebaseApp
import com.google.firebase.components.Component
import com.google.firebase.components.ComponentRegistrar
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableOptions
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.ktx.Firebase
import java.net.URL

/**
 * Accessing this object for Kotlin apps has changed; see the
 * [migration guide](https://firebase.google.com/docs/android/kotlin-migration).
 *
 * Returns the [FirebaseFunctions] instance of the default [FirebaseApp].
 * @deprecated **Deprecation Notice:** The Kotlin extensions (KTX) APIs have been added to their
 * respective main modules, and the Kotlin extension (KTX) APIs in
 * `com.google.firebase.firebase-functions-ktx` are now deprecated. As early as April 2024, we'll no
 * longer release KTX modules. For details, see the
 * [FAQ about this initiative.](https://firebase.google.com/docs/android/kotlin-migration)
 */
val Firebase.functions: FirebaseFunctions
  get() = FirebaseFunctions.getInstance()

/**
 * Accessing this object for Kotlin apps has changed; see the
 * [migration guide](https://firebase.google.com/docs/android/kotlin-migration).
 *
 * Returns the [FirebaseFunctions] instance of a given [regionOrCustomDomain].
 * @deprecated **Deprecation Notice:** The Kotlin extensions (KTX) APIs have been added to their
 * respective main modules, and the Kotlin extension (KTX) APIs in
 * `com.google.firebase.firebase-functions-ktx` are now deprecated. As early as April 2024, we'll no
 * longer release KTX modules. For details, see the
 * [FAQ about this initiative.](https://firebase.google.com/docs/android/kotlin-migration)
 */
fun Firebase.functions(regionOrCustomDomain: String): FirebaseFunctions =
  FirebaseFunctions.getInstance(regionOrCustomDomain)

/**
 * Accessing this object for Kotlin apps has changed; see the
 * [migration guide](https://firebase.google.com/docs/android/kotlin-migration).
 *
 * Returns the [FirebaseFunctions] instance of a given [FirebaseApp].
 * @deprecated **Deprecation Notice:** The Kotlin extensions (KTX) APIs have been added to their
 * respective main modules, and the Kotlin extension (KTX) APIs in
 * `com.google.firebase.firebase-functions-ktx` are now deprecated. As early as April 2024, we'll no
 * longer release KTX modules. For details, see the
 * [FAQ about this initiative.](https://firebase.google.com/docs/android/kotlin-migration)
 */
fun Firebase.functions(app: FirebaseApp): FirebaseFunctions = FirebaseFunctions.getInstance(app)

/**
 * Accessing this object for Kotlin apps has changed; see the
 * [migration guide](https://firebase.google.com/docs/android/kotlin-migration).
 *
 * Returns the [FirebaseFunctions] instance of a given [FirebaseApp] and [regionOrCustomDomain].
 * @deprecated **Deprecation Notice:** The Kotlin extensions (KTX) APIs have been added to their
 * respective main modules, and the Kotlin extension (KTX) APIs in
 * `com.google.firebase.firebase-functions-ktx` are now deprecated. As early as April 2024, we'll no
 * longer release KTX modules. For details, see the
 * [FAQ about this initiative.](https://firebase.google.com/docs/android/kotlin-migration)
 */
fun Firebase.functions(app: FirebaseApp, regionOrCustomDomain: String): FirebaseFunctions =
  FirebaseFunctions.getInstance(app, regionOrCustomDomain)

/**
 * @suppress
 * @deprecated **Deprecation Notice:** The Kotlin extensions (KTX) APIs have been added to their
 * respective main modules, and the Kotlin extension (KTX) APIs in
 * `com.google.firebase.firebase-functions-ktx` are now deprecated. As early as April 2024, we'll no
 * longer release KTX modules. For details, see the
 * [FAQ about this initiative.](https://firebase.google.com/docs/android/kotlin-migration)
 */
@Deprecated(
  "Migrate to use the KTX API from the main module: https://firebase.google.com/docs/android/kotlin-migration.",
  ReplaceWith("")
)
@Keep
class FirebaseFunctionsKtxRegistrar : ComponentRegistrar {
  override fun getComponents(): List<Component<*>> = listOf()
}

/**
 * Returns a reference to the Callable HTTPS trigger with the given name and call options.
 * @deprecated **Deprecation Notice:** The Kotlin extensions (KTX) APIs have been added to their
 * respective main modules, and the Kotlin extension (KTX) APIs in
 * `com.google.firebase.firebase-functions-ktx` are now deprecated. As early as April 2024, we'll no
 * longer release KTX modules. For details, see the
 * [FAQ about this initiative.](https://firebase.google.com/docs/android/kotlin-migration)
 */
@Deprecated(
  "Migrate to use the KTX API from the main module: https://firebase.google.com/docs/android/kotlin-migration.",
  ReplaceWith("")
)
fun FirebaseFunctions.getHttpsCallable(
  name: String,
  init: HttpsCallableOptions.Builder.() -> Unit
): HttpsCallableReference {
  val builder = HttpsCallableOptions.Builder()
  builder.init()
  return getHttpsCallable(name, builder.build())
}

/**
 * Returns a reference to the Callable HTTPS trigger with the given URL and call options.
 * @deprecated **Deprecation Notice:** The Kotlin extensions (KTX) APIs have been added to their
 * respective main modules, and the Kotlin extension (KTX) APIs in
 * `com.google.firebase.firebase-functions-ktx` are now deprecated. As early as April 2024, we'll no
 * longer release KTX modules. For details, see the
 * [FAQ about this initiative.](https://firebase.google.com/docs/android/kotlin-migration)
 */
@Deprecated(
  "Migrate to use the KTX API from the main module: https://firebase.google.com/docs/android/kotlin-migration.",
  ReplaceWith("")
)
fun FirebaseFunctions.getHttpsCallableFromUrl(
  url: URL,
  init: HttpsCallableOptions.Builder.() -> Unit
): HttpsCallableReference {
  val builder = HttpsCallableOptions.Builder()
  builder.init()
  return getHttpsCallableFromUrl(url, builder.build())
}
