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

package com.google.firebase.crashlytics.ktx

import androidx.annotation.Keep
import com.google.firebase.FirebaseApp
import com.google.firebase.components.Component
import com.google.firebase.components.ComponentRegistrar
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.ktx.Firebase
import com.google.firebase.platforminfo.LibraryVersionComponent

/** Returns the [FirebaseCrashlytics] instance of the default [FirebaseApp]. */
val Firebase.crashlytics: FirebaseCrashlytics
    get() = FirebaseCrashlytics.getInstance()

fun FirebaseCrashlytics.setCustomKeys(vararg pairs: Pair<String, Any>) {
    for ((key, value) in pairs) {
        when (value) {
            is Boolean -> setCustomKey(key, value)
            is Double -> setCustomKey(key, value)
            is Float -> setCustomKey(key, value)
            is Int -> setCustomKey(key, value)
            is Long -> setCustomKey(key, value)
            is String -> setCustomKey(key, value)
            else -> {
                val valueType = value::class.java.componentType!!.canonicalName
                throw IllegalArgumentException("Illegal value type $valueType for key \"$key\"")
            }
        }
    }
}

internal const val LIBRARY_NAME: String = "fire-cls-ktx"

/** @suppress */
@Keep
class FirebaseCrashlyticsRegistrar : ComponentRegistrar {
    override fun getComponents(): List<Component<*>> =
            listOf(LibraryVersionComponent.create(LIBRARY_NAME, BuildConfig.VERSION_NAME))
}
