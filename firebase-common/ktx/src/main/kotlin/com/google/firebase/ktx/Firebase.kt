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
package com.google.firebase.ktx

import android.content.Context
import androidx.annotation.Keep
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.components.Component
import com.google.firebase.components.ComponentRegistrar
import com.google.firebase.platforminfo.LibraryVersionComponent

/**
 * Single access point to all firebase SDKs from Kotlin.
 *
 * <p>Acts as a target for extension methods provided by sdks.
 */
object Firebase

/** Returns the default firebase app instance. */
val Firebase.app: FirebaseApp
    get() = FirebaseApp.getInstance()

/** Returns a named firebase app instance. */
fun Firebase.app(name: String): FirebaseApp = FirebaseApp.getInstance(name)

/** Initializes and returns a FirebaseApp. */
fun Firebase.initialize(context: Context): FirebaseApp? = FirebaseApp.initializeApp(context)

/** Initializes and returns a FirebaseApp. */
fun Firebase.initialize(context: Context, options: FirebaseOptions): FirebaseApp =
        FirebaseApp.initializeApp(context, options)

/** Initializes and returns a FirebaseApp. */
fun Firebase.initialize(context: Context, options: FirebaseOptions, name: String): FirebaseApp =
        FirebaseApp.initializeApp(context, options, name)

/** Returns options of default FirebaseApp */
val Firebase.options: FirebaseOptions
    get() = Firebase.app.options

internal const val LIBRARY_NAME: String = "fire-core-ktx"

/** @suppress */
@Keep
class FirebaseCommonKtxRegistrar : ComponentRegistrar {
    override fun getComponents(): List<Component<*>> {
        return listOf(
                LibraryVersionComponent.create(LIBRARY_NAME, BuildConfig.VERSION_NAME))
    }
}
