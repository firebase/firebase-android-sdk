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

package com.google.firebase.ktx

import android.content.Context
import androidx.annotation.Keep
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.annotations.concurrent.Background
import com.google.firebase.annotations.concurrent.Blocking
import com.google.firebase.annotations.concurrent.Lightweight
import com.google.firebase.annotations.concurrent.UiThread
import com.google.firebase.components.Component
import com.google.firebase.components.ComponentRegistrar
import com.google.firebase.components.Dependency
import com.google.firebase.components.Qualified
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher

/**
 * All fields in this object are deprecated; Use `com.google.firebase.Firebase` instead.
 *
 * Single access point to all firebase SDKs from Kotlin. Acts as a target for extension methods
 * provided by sdks.
 *
 * @deprecated **Deprecation Notice:** The Kotlin extensions (KTX) APIs have been added to their
 * respective main modules, and the Kotlin extension (KTX) APIs in
 * `com.google.firebase.firebase-common-ktx` are now deprecated. As early as April 2024, we'll no
 * longer release KTX modules. For details, see the
 * [FAQ about this initiative.](https://firebase.google.com/docs/android/kotlin-migration)
 */
object Firebase

/**
 * Accessing this object for Kotlin apps has changed; see the migration guide:
 * https://firebase.google.com/docs/android/kotlin-migration.
 *
 * Returns the default firebase app instance.
 * @deprecated **Deprecation Notice:** The Kotlin extensions (KTX) APIs have been added to their
 * respective main modules, and the Kotlin extension (KTX) APIs in
 * `com.google.firebase.firebase-common-ktx` are now deprecated. As early as April 2024, we'll no
 * longer release KTX modules. For details, see the
 * [FAQ about this initiative.](https://firebase.google.com/docs/android/kotlin-migration).
 */
val Firebase.app: FirebaseApp
  get() = FirebaseApp.getInstance()

/**
 * Accessing this object for Kotlin apps has changed; see the migration guide:
 * https://firebase.google.com/docs/android/kotlin-migration.
 *
 * Returns a named firebase app instance.
 * @deprecated **Deprecation Notice:** The Kotlin extensions (KTX) APIs have been added to their
 * respective main modules, and the Kotlin extension (KTX) APIs in
 * `com.google.firebase.firebase-common-ktx` are now deprecated. As early as April 2024, we'll no
 * longer release KTX modules. For details, see the
 * [FAQ about this initiative.](https://firebase.google.com/docs/android/kotlin-migration).
 */
fun Firebase.app(name: String): FirebaseApp = FirebaseApp.getInstance(name)

/**
 * Initializes and returns a FirebaseApp.
 * @deprecated **Deprecation Notice:** The Kotlin extensions (KTX) APIs have been added to their
 * respective main modules, and the Kotlin extension (KTX) APIs in
 * `com.google.firebase.firebase-common-ktx` are now deprecated. As early as April 2024, we'll no
 * longer release KTX modules. For details, see the
 * [FAQ about this initiative.](https://firebase.google.com/docs/android/kotlin-migration)
 */
@Deprecated(
  "Migrate to use the KTX API from the main module: https://firebase.google.com/docs/android/kotlin-migration.",
  ReplaceWith("")
)
fun Firebase.initialize(context: Context): FirebaseApp? = FirebaseApp.initializeApp(context)

/**
 * Initializes and returns a FirebaseApp.
 * @deprecated **Deprecation Notice:** The Kotlin extensions (KTX) APIs have been added to their
 * respective main modules, and the Kotlin extension (KTX) APIs in
 * `com.google.firebase.firebase-common-ktx` are now deprecated. As early as April 2024, we'll no
 * longer release KTX modules. For details, see the
 * [FAQ about this initiative.](https://firebase.google.com/docs/android/kotlin-migration)
 */
@Deprecated(
  "Migrate to use the KTX API from the main module: https://firebase.google.com/docs/android/kotlin-migration.",
  ReplaceWith("")
)
fun Firebase.initialize(context: Context, options: FirebaseOptions): FirebaseApp =
  FirebaseApp.initializeApp(context, options)

/**
 * Initializes and returns a FirebaseApp.
 * @deprecated **Deprecation Notice:** The Kotlin extensions (KTX) APIs have been added to their
 * respective main modules, and the Kotlin extension (KTX) APIs in
 * `com.google.firebase.firebase-common-ktx` are now deprecated. As early as April 2024, we'll no
 * longer release KTX modules. For details, see the
 * [FAQ about this initiative.](https://firebase.google.com/docs/android/kotlin-migration)
 */
@Deprecated(
  "Migrate to use the KTX API from the main module: https://firebase.google.com/docs/android/kotlin-migration.",
  ReplaceWith("")
)
fun Firebase.initialize(context: Context, options: FirebaseOptions, name: String): FirebaseApp =
  FirebaseApp.initializeApp(context, options, name)

/**
 * Accessing this object for Kotlin apps has changed; see the migration guide:
 * https://firebase.google.com/docs/android/kotlin-migration.
 *
 * Returns options of default FirebaseApp
 * @deprecated **Deprecation Notice:** The Kotlin extensions (KTX) APIs have been added to their
 * respective main modules, and the Kotlin extension (KTX) APIs in
 * `com.google.firebase.firebase-common-ktx` are now deprecated. As early as April 2024, we'll no
 * longer release KTX modules. For details, see the
 * [FAQ about this initiative.](https://firebase.google.com/docs/android/kotlin-migration)
 */
val Firebase.options: FirebaseOptions
  get() = Firebase.app.options

/** @suppress */
@Deprecated(
  "Migrate to use the KTX API from the main module: https://firebase.google.com/docs/android/kotlin-migration.",
  ReplaceWith("")
)
@Keep
class FirebaseCommonKtxRegistrar : ComponentRegistrar {
  override fun getComponents(): List<Component<*>> {
    return listOf(
      coroutineDispatcher<Background>(),
      coroutineDispatcher<Lightweight>(),
      coroutineDispatcher<Blocking>(),
      coroutineDispatcher<UiThread>()
    )
  }
}

private inline fun <reified T : Annotation> coroutineDispatcher(): Component<CoroutineDispatcher> =
  Component.builder(Qualified.qualified(T::class.java, CoroutineDispatcher::class.java))
    .add(Dependency.required(Qualified.qualified(T::class.java, Executor::class.java)))
    .factory { c ->
      c.get(Qualified.qualified(T::class.java, Executor::class.java)).asCoroutineDispatcher()
    }
    .build()
