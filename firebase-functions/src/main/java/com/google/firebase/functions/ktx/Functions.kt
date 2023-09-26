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

/** Returns the [FirebaseFunctions] instance of the default [FirebaseApp]. */
@Deprecated(
  "Use `com.google.firebase.Firebase.functions` from the main module instead. The Kotlin extensions (KTX) APIs have been added to their respective main modules, and the Kotlin extension (KTX) APIs in `com.google.firebase.firebase-functions-ktx` are now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the [FAQ about this initiative.](https://firebase.google.com/docs/android/ktx-apis-to-main-modules){:.external}",
  ReplaceWith(
    expression = "com.google.firebase.Firebase.functions",
    imports = ["com.google.firebase.Firebase", "com.google.firebase.functions.functions"]
  )
)
val Firebase.functions: FirebaseFunctions
  get() = FirebaseFunctions.getInstance()

/** Returns the [FirebaseFunctions] instance of a given [regionOrCustomDomain]. */
@Deprecated(
  "Use `com.google.firebase.Firebase.functions(regionOrCustomDomain)` from the main module instead. The Kotlin extensions (KTX) APIs have been added to their respective main modules, and the Kotlin extension (KTX) APIs in `com.google.firebase.firebase-functions-ktx` are now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the [FAQ about this initiative.](https://firebase.google.com/docs/android/ktx-apis-to-main-modules){:.external}",
  ReplaceWith(
    expression = "com.google.firebase.Firebase.functions(regionOrCustomDomain)",
    imports = ["com.google.firebase.Firebase", "com.google.firebase.functions.functions"]
  )
)
fun Firebase.functions(regionOrCustomDomain: String): FirebaseFunctions =
  FirebaseFunctions.getInstance(regionOrCustomDomain)

/** Returns the [FirebaseFunctions] instance of a given [FirebaseApp]. */
@Deprecated(
  "Use `com.google.firebase.functions.Firebase.functions(app)` from the main module instead. The Kotlin extensions (KTX) APIs have been added to their respective main modules, and the Kotlin extension (KTX) APIs in `com.google.firebase.firebase-functions-ktx` are now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the [FAQ about this initiative.](https://firebase.google.com/docs/android/ktx-apis-to-main-modules){:.external}",
  ReplaceWith(
    expression = "com.google.firebase.Firebase.functions(app)",
    imports = ["com.google.firebase.Firebase", "com.google.firebase.functions.functions"]
  )
)
fun Firebase.functions(app: FirebaseApp): FirebaseFunctions = FirebaseFunctions.getInstance(app)

/** Returns the [FirebaseFunctions] instance of a given [FirebaseApp] and [regionOrCustomDomain]. */
@Deprecated(
  "Use `com.google.firebase.functions.Firebase.functions(app, regionOrCustomDomain)` from the main module instead. The Kotlin extensions (KTX) APIs have been added to their respective main modules, and the Kotlin extension (KTX) APIs in `com.google.firebase.firebase-functions-ktx` are now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the [FAQ about this initiative.](https://firebase.google.com/docs/android/ktx-apis-to-main-modules){:.external}",
  ReplaceWith(
    expression = "com.google.firebase.Firebase.functions(app, regionOrCustomDomain)",
    imports = ["com.google.firebase.Firebase", "com.google.firebase.functions.functions"]
  )
)
fun Firebase.functions(app: FirebaseApp, regionOrCustomDomain: String): FirebaseFunctions =
  FirebaseFunctions.getInstance(app, regionOrCustomDomain)

/** @suppress */
@Deprecated(
  "Use `com.google.firebase.functions.FirebaseFunctionsKtxRegistrar` from the main module instead. The Kotlin extensions (KTX) APIs have been added to their respective main modules, and the Kotlin extension (KTX) APIs in `com.google.firebase.firebase-functions-ktx` are now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the [FAQ about this initiative.](https://firebase.google.com/docs/android/ktx-apis-to-main-modules){:.external}",
  ReplaceWith(
    expression = "FirebaseFunctionsKtxRegistrar",
    imports =
      [
        "com.google.firebase.Firebase",
        "com.google.firebase.functions.FirebaseFunctionsKtxRegistrar"
      ]
  )
)
@Keep
class FirebaseFunctionsKtxRegistrar : ComponentRegistrar {
  override fun getComponents(): List<Component<*>> = listOf()
}

/** Returns a reference to the Callable HTTPS trigger with the given name and call options. */
@Deprecated(
  "Use `com.google.firebase.functions.FirebaseFunctions.getHttpsCallable(name, init)` from the main module instead. The Kotlin extensions (KTX) APIs have been added to their respective main modules, and the Kotlin extension (KTX) APIs in `com.google.firebase.firebase-functions-ktx` are now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the [FAQ about this initiative.](https://firebase.google.com/docs/android/ktx-apis-to-main-modules){:.external}",
  ReplaceWith(
    expression = "getHttpsCallable(name, init)",
    imports = ["com.google.firebase.Firebase", "com.google.firebase.functions.getHttpsCallable"]
  )
)
fun FirebaseFunctions.getHttpsCallable(
  name: String,
  init: HttpsCallableOptions.Builder.() -> Unit
): HttpsCallableReference {
  val builder = HttpsCallableOptions.Builder()
  builder.init()
  return getHttpsCallable(name, builder.build())
}

val x = Firebase.functions.getHttpsCallable("Vinay") { println("Vinay")}

/** Returns a reference to the Callable HTTPS trigger with the given URL and call options. */
@Deprecated(
  "Use `com.google.firebase.functions.FirebaseFunctions.getHttpsCallableFromUrl(url, init)` from the main module instead. The Kotlin extensions (KTX) APIs have been added to their respective main modules, and the Kotlin extension (KTX) APIs in `com.google.firebase.firebase-functions-ktx` are now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the [FAQ about this initiative.](https://firebase.google.com/docs/android/ktx-apis-to-main-modules){:.external}",
  ReplaceWith(
    expression = "getHttpsCallableFromUrl(url, init)",
    imports =
      ["com.google.firebase.Firebase", "com.google.firebase.functions.getHttpsCallableFromUrl"]
  )
)
fun FirebaseFunctions.getHttpsCallableFromUrl(
  url: URL,
  init: HttpsCallableOptions.Builder.() -> Unit
): HttpsCallableReference {
  val builder = HttpsCallableOptions.Builder()
  builder.init()
  return getHttpsCallableFromUrl(url, builder.build())
}


