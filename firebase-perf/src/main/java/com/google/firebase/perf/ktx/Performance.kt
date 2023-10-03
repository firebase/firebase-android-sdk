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

package com.google.firebase.perf.ktx

import androidx.annotation.Keep
import com.google.firebase.FirebaseApp
import com.google.firebase.components.Component
import com.google.firebase.components.ComponentRegistrar
import com.google.firebase.ktx.Firebase
import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.perf.metrics.HttpMetric
import com.google.firebase.perf.metrics.Trace

/**
 * Returns the [FirebasePerformance] instance of the default [FirebaseApp].
 * @deprecated **Deprecation Notice:** The Kotlin extensions (KTX) APIs have been added to their
 * respective main modules, and the Kotlin extension (KTX) APIs in
 * `com.google.firebase.firebase-perf-ktx` are now deprecated. As early as April 2024, we'll no
 * longer release KTX modules. For details, see the
 * [FAQ about this initiative.](https://firebase.google.com/docs/android/ktx-apis-to-main-modules)
 */
@Deprecated(
  "Use `com.google.firebase.Firebase.performance` from the main module instead.",
  ReplaceWith(
    expression = "com.google.firebase.Firebase.performance",
    imports = ["com.google.firebase.Firebase", "com.google.firebase.perf.performance"]
  )
)
val Firebase.performance: FirebasePerformance
  get() = FirebasePerformance.getInstance()

/**
 * Measures the time it takes to run the [block] wrapped by calls to [start] and [stop] using
 * [HttpMetric].
 * @deprecated **Deprecation Notice:** The Kotlin extensions (KTX) APIs have been added to their
 * respective main modules, and the Kotlin extension (KTX) APIs in
 * `com.google.firebase.firebase-perf-ktx` are now deprecated. As early as April 2024, we'll no
 * longer release KTX modules. For details, see the
 * [FAQ about this initiative.](https://firebase.google.com/docs/android/ktx-apis-to-main-modules)
 */
@Deprecated(
  "Use `com.google.firebase.perf.HttpMetric.trace(block)` from the main module instead.",
  ReplaceWith(
    expression = "trace(block)",
    imports = ["com.google.firebase.Firebase", "com.google.firebase.perf.trace"]
  )
)
inline fun HttpMetric.trace(block: HttpMetric.() -> Unit) {
  start()
  try {
    block()
  } finally {
    stop()
  }
}

/**
 * Measures the time it takes to run the [block] wrapped by calls to [start] and [stop] using
 * [Trace].
 * @deprecated **Deprecation Notice:** The Kotlin extensions (KTX) APIs have been added to their
 * respective main modules, and the Kotlin extension (KTX) APIs in
 * `com.google.firebase.firebase-perf-ktx` are now deprecated. As early as April 2024, we'll no
 * longer release KTX modules. For details, see the
 * [FAQ about this initiative.](https://firebase.google.com/docs/android/ktx-apis-to-main-modules)
 */
@Deprecated(
  "Use `com.google.firebase.perf.Trace.trace<T>(block)` from the main module instead.",
  ReplaceWith(
    expression = "Trace.trace<T>(block)",
    imports = ["com.google.firebase.Firebase", "com.google.firebase.perf.trace"]
  )
)
inline fun <T> Trace.trace(block: Trace.() -> T): T {
  start()
  try {
    return block()
  } finally {
    stop()
  }
}

/**
 * Creates a [Trace] object with given [name] and measures the time it takes to run the [block]
 * wrapped by calls to [start] and [stop].
 * @deprecated **Deprecation Notice:** The Kotlin extensions (KTX) APIs have been added to their
 * respective main modules, and the Kotlin extension (KTX) APIs in
 * `com.google.firebase.firebase-perf-ktx` are now deprecated. As early as April 2024, we'll no
 * longer release KTX modules. For details, see the
 * [FAQ about this initiative.](https://firebase.google.com/docs/android/ktx-apis-to-main-modules)
 */
@Deprecated(
  "Use `com.google.firebase.perf.Trace.trace<T>(name, block)` from the main module instead.",
  ReplaceWith(
    expression = "trace<T>(name, block)",
    imports = ["com.google.firebase.Firebase", "com.google.firebase.perf.trace"]
  )
)
inline fun <T> trace(name: String, block: Trace.() -> T): T = Trace.create(name).trace(block)

/**
 * @suppress
 * @deprecated **Deprecation Notice:** The Kotlin extensions (KTX) APIs have been added to their
 * respective main modules, and the Kotlin extension (KTX) APIs in
 * `com.google.firebase.firebase-perf-ktx` are now deprecated. As early as April 2024, we'll no
 * longer release KTX modules. For details, see the
 * [FAQ about this initiative.](https://firebase.google.com/docs/android/ktx-apis-to-main-modules)
 */
@Deprecated(
  "Use `com.google.firebase.perf.FirebasePerfKtxRegistrar` from the main module instead.",
  ReplaceWith(
    expression = "FirebasePerfKtxRegistrar",
    imports = ["com.google.firebase.Firebase", "com.google.firebase.perf.FirebasePerfKtxRegistrar"]
  )
)
@Keep
class FirebasePerfKtxRegistrar : ComponentRegistrar {
  override fun getComponents(): List<Component<*>> = listOf()
}
