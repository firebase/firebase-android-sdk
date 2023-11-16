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

package com.google.firebase.remoteconfig

import androidx.annotation.Keep
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.components.Component
import com.google.firebase.components.ComponentRegistrar
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Returns the [FirebaseRemoteConfig] instance of the default [FirebaseApp]. */
val Firebase.remoteConfig: FirebaseRemoteConfig
  get() = FirebaseRemoteConfig.getInstance()

/** Returns the [FirebaseRemoteConfig] instance of a given [FirebaseApp]. */
fun Firebase.remoteConfig(app: FirebaseApp): FirebaseRemoteConfig =
  FirebaseRemoteConfig.getInstance(app)

/** See [FirebaseRemoteConfig#getValue] */
operator fun FirebaseRemoteConfig.get(key: String): FirebaseRemoteConfigValue {
  return this.getValue(key)
}

fun remoteConfigSettings(
  init: FirebaseRemoteConfigSettings.Builder.() -> Unit
): FirebaseRemoteConfigSettings {
  val builder = FirebaseRemoteConfigSettings.Builder()
  builder.init()
  return builder.build()
}

/**
 * Starts listening for config updates from the Remote Config backend and emits [ConfigUpdate]s via
 * a [Flow]. See [FirebaseRemoteConfig.addOnConfigUpdateListener] for more information.
 *
 * - When the returned flow starts being collected, an [ConfigUpdateListener] will be attached.
 * - When the flow completes, the listener will be removed. If there are no attached listeners, the
 * connection to the Remote Config backend will be closed.
 */
val FirebaseRemoteConfig.configUpdates
  get() = callbackFlow {
    val registration =
      addOnConfigUpdateListener(
        object : ConfigUpdateListener {
          override fun onUpdate(configUpdate: ConfigUpdate) {
            schedule { trySendBlocking(configUpdate) }
          }

          override fun onError(error: FirebaseRemoteConfigException) {
            cancel(message = "Error listening for config updates.", cause = error)
          }
        }
      )
    awaitClose { registration.remove() }
  }

/** @suppress */
@Keep
class FirebaseRemoteConfigKtxRegistrar : ComponentRegistrar {
  override fun getComponents(): List<Component<*>> = listOf()
}
