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
import com.google.firebase.platforminfo.LibraryVersionComponent

/** Returns the [FirebasePerformance] instance of the default [FirebaseApp]. */
val Firebase.performance: FirebasePerformance
    get() = FirebasePerformance.getInstance()

/** Measures the time it takes to run the [block] wrapped by calls to [start] and [stop] using [HttpMetric]. */
inline fun HttpMetric.trace(block: HttpMetric.() -> Unit) {
    start()
    try {
        block()
    } finally {
        stop()
    }
}

/** Measures the time it takes to run the [block] wrapped by calls to [start] and [stop] using [Trace]. */
inline fun <T> Trace.trace(block: Trace.() -> T): T {
    start()
    try {
        return block()
    } finally {
        stop()
    }
}

internal const val LIBRARY_NAME: String = "fire-perf-ktx"

/** @suppress */
@Keep
class FirebasePerfKtxRegistrar : ComponentRegistrar {
    override fun getComponents(): List<Component<*>> =
            listOf(LibraryVersionComponent.create(LIBRARY_NAME, BuildConfig.VERSION_NAME))
}
