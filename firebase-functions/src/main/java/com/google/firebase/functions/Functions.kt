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

package com.google.firebase.functions

import androidx.annotation.Keep
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.components.Component
import com.google.firebase.components.ComponentRegistrar
import java.net.URL

/** Returns the [FirebaseFunctions] instance of the default [FirebaseApp]. */
val Firebase.functions: FirebaseFunctions
  get() = FirebaseFunctions.getInstance()

/** Returns the [FirebaseFunctions] instance of a given [regionOrCustomDomain]. */
fun Firebase.functions(regionOrCustomDomain: String): FirebaseFunctions =
  FirebaseFunctions.getInstance(regionOrCustomDomain)

/** Returns the [FirebaseFunctions] instance of a given [FirebaseApp]. */
fun Firebase.functions(app: FirebaseApp): FirebaseFunctions = FirebaseFunctions.getInstance(app)

/** Returns the [FirebaseFunctions] instance of a given [FirebaseApp] and [regionOrCustomDomain]. */
fun Firebase.functions(app: FirebaseApp, regionOrCustomDomain: String): FirebaseFunctions =
  FirebaseFunctions.getInstance(app, regionOrCustomDomain)

/** @suppress */
@Keep
class FirebaseFunctionsKtxRegistrar : ComponentRegistrar {
  override fun getComponents(): List<Component<*>> = listOf()
}

/** Returns a reference to the Callable HTTPS trigger with the given name and call options. */
fun FirebaseFunctions.getHttpsCallable(
  name: String,
  init: HttpsCallableOptions.Builder.() -> Unit
): HttpsCallableReference {
  val builder = HttpsCallableOptions.Builder()
  builder.init()
  return getHttpsCallable(name, builder.build())
}

/** Returns a reference to the Callable HTTPS trigger with the given URL and call options. */
fun FirebaseFunctions.getHttpsCallableFromUrl(
  url: URL,
  init: HttpsCallableOptions.Builder.() -> Unit
): HttpsCallableReference {
  val builder = HttpsCallableOptions.Builder()
  builder.init()
  return getHttpsCallableFromUrl(url, builder.build())
}
