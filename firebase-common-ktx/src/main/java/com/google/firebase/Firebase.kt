// Copyright 2018 Google LLC
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

package com.google.firebase

import android.content.Context

/**
 * Main entry point to Firebase Kotlin APIs.
 *
 * <p>Individual SDKs "register" themselves with this object by adding extension methods/properties
 * to it.
 */
object Firebase

/**
 * Returns the default (first initialized) instance of the {@link FirebaseApp}.
 *
 * @throws IllegalStateException if the default app was not initialized.
 */
val Firebase.app: FirebaseApp
    get() = FirebaseApp.getInstance()

/**
 * Returns the instance identified by the unique name, or throws if it does not exist.
 *
 * @param name represents the name of the {@link FirebaseApp} instance.
 * @throws IllegalStateException if the [FirebaseApp] was not initialized via
 * [Firebase.initializeApp].
 */
fun Firebase.app(name: String): FirebaseApp = FirebaseApp.getInstance(name)

/**
 * A factory method to initialize a {@link FirebaseApp}.
 *
 * @param context represents the {@link Context}
 * @param name unique name for the app. It is an error to initialize an app with an already
 *     existing name. Starting and ending whitespace characters in the name are ignored (trimmed).
 * @param options represents the global {@link FirebaseOptions}
 * @throws IllegalStateException if an app with the same name has already been initialized.
 * @return an instance of {@link FirebaseApp}
 */
fun Firebase.initializeApp(context: Context, name: String, options: FirebaseOptions): FirebaseApp =
    FirebaseApp.initializeApp(context, options, name)

/**
 * [FirebaseOptions] builder.
 */
fun Firebase.options(
    applicationId: String,
    apiKey: String,
    block: FirebaseOptions.Builder.() -> Unit = {}
): FirebaseOptions =
    FirebaseOptions.Builder().setApplicationId(applicationId).setApiKey(apiKey).apply(block).build()
