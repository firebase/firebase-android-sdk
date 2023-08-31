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
  "com.google.firebase.functionsktx.Firebase.functions has been deprecated. Use `com.google.firebase.functionsFirebase.functions` instead.",
  ReplaceWith(
    expression = "Firebase.functions",
    imports = ["com.google.firebase.Firebase", "com.google.firebase.functionsfunctions"]
  )
)
val Firebase.functions: FirebaseFunctions
  get() = FirebaseFunctions.getInstance()

/** Returns the [FirebaseFunctions] instance of a given [regionOrCustomDomain]. */
@Deprecated(
  "com.google.firebase.functionsktx.Firebase.ctions(regionOrCustomDomain) has been deprecated. Use `com.google.firebase.functionsFirebase.ctions(regionOrCustomDomain)` instead.",
  ReplaceWith(
    expression = "Firebase.ctions(regionOrCustomDomain)",
    imports = ["com.google.firebase.Firebase", "com.google.firebase.functionsctions"]
  )
)
fun Firebase.functions(regionOrCustomDomain: String): FirebaseFunctions =
  FirebaseFunctions.getInstance(regionOrCustomDomain)

/** Returns the [FirebaseFunctions] instance of a given [FirebaseApp]. */
@Deprecated(
  "com.google.firebase.functionsktx.Firebase.ctions(app) has been deprecated. Use `com.google.firebase.functionsFirebase.ctions(app)` instead.",
  ReplaceWith(
    expression = "Firebase.ctions(app)",
    imports = ["com.google.firebase.Firebase", "com.google.firebase.functionsctions"]
  )
)
fun Firebase.functions(app: FirebaseApp): FirebaseFunctions = FirebaseFunctions.getInstance(app)

/** Returns the [FirebaseFunctions] instance of a given [FirebaseApp] and [regionOrCustomDomain]. */
@Deprecated(
  "com.google.firebase.functionsktx.Firebase.ctions(app, regionOrCustomDomain) has been deprecated. Use `com.google.firebase.functionsFirebase.ctions(app, regionOrCustomDomain)` instead.",
  ReplaceWith(
    expression = "Firebase.ctions(app, regionOrCustomDomain)",
    imports = ["com.google.firebase.Firebase", "com.google.firebase.functionsctions"]
  )
)
fun Firebase.functions(app: FirebaseApp, regionOrCustomDomain: String): FirebaseFunctions =
  FirebaseFunctions.getInstance(app, regionOrCustomDomain)

/** @suppress */
@Deprecated(
  "com.google.firebase.functionsktx.FirebaseFunctionsKtxRegistrar has been deprecated. Use `com.google.firebase.functionsFirebaseFunctionsKtxRegistrar` instead.",
  ReplaceWith(
    expression = "FirebaseFunctionsKtxRegistrar",
    imports =
      ["com.google.firebase.Firebase", "com.google.firebase.functionsFirebaseFunctionsKtxRegistrar"]
  )
)
@Keep
class FirebaseFunctionsKtxRegistrar : ComponentRegistrar {
  override fun getComponents(): List<Component<*>> = listOf()
}

/** Returns a reference to the Callable HTTPS trigger with the given name and call options. */
@Deprecated(
  "com.google.firebase.functionsktx.FirebaseFunctions.getHttpsCallable( has been deprecated. Use `com.google.firebase.functionsFirebaseFunctions.getHttpsCallable(` instead.",
  ReplaceWith(
    expression = "FirebaseFunctions.getHttpsCallable(",
    imports =
      [
        "com.google.firebase.Firebase",
        "com.google.firebase.functionsFirebaseFunctions.getHttpsCallable"
      ]
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

/** Returns a reference to the Callable HTTPS trigger with the given URL and call options. */
@Deprecated(
  "com.google.firebase.functionsktx.FirebaseFunctions.getHttpsCallableFromUrl( has been deprecated. Use `com.google.firebase.functionsFirebaseFunctions.getHttpsCallableFromUrl(` instead.",
  ReplaceWith(
    expression = "FirebaseFunctions.getHttpsCallableFromUrl(",
    imports =
      [
        "com.google.firebase.Firebase",
        "com.google.firebase.functionsFirebaseFunctions.getHttpsCallableFromUrl"
      ]
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
