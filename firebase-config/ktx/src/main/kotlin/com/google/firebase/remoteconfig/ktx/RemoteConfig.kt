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

package com.google.firebase.remoteconfig.ktx

import androidx.annotation.Keep
import com.google.firebase.FirebaseApp
import com.google.firebase.components.Component
import com.google.firebase.components.ComponentRegistrar
import com.google.firebase.ktx.Firebase
import com.google.firebase.platforminfo.LibraryVersionComponent
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue

/** Returns the [FirebaseRemoteConfig] instance of the default [FirebaseApp]. */
val Firebase.remoteConfig: FirebaseRemoteConfig
    get() = FirebaseRemoteConfig.getInstance()

/** Returns the [FirebaseRemoteConfig] instance of a given [FirebaseApp]. */
fun Firebase.remoteConfig(app: FirebaseApp): FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance(app)

/** See [FirebaseRemoteConfig#getValue] */
operator fun FirebaseRemoteConfig.get(key: String): FirebaseRemoteConfigValue {
    return this.getValue(key)
}

fun remoteConfigSettings(init: FirebaseRemoteConfigSettings.Builder.() -> Unit): FirebaseRemoteConfigSettings {
    val builder = FirebaseRemoteConfigSettings.Builder()
    builder.init()
    return builder.build()
}

internal const val LIBRARY_NAME: String = "fire-cfg-ktx"

/** @suppress */
@Keep
class FirebaseRemoteConfigKtxRegistrar : ComponentRegistrar {
    override fun getComponents(): List<Component<*>> =
            listOf(LibraryVersionComponent.create(LIBRARY_NAME, BuildConfig.VERSION_NAME))
}
