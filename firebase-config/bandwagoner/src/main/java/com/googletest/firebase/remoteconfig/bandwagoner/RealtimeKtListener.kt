/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.googletest.firebase.remoteconfig.bandwagoner

import android.util.Log
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.configUpdates
import com.google.firebase.remoteconfig.ktx.remoteConfig
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.future.future

class RealtimeKtListener {
  companion object {
    private val TAG = "RealtimeListener"

    fun listenForUpdatesAsync(): CompletableFuture<Unit> = GlobalScope.future { listenForUpdates() }

    private suspend fun listenForUpdates() {
      Firebase.remoteConfig.configUpdates
        .catch { exception -> Log.w(TAG, "Error listening for updates!", exception) }
        .collect { configUpdate ->
          if (configUpdate.updatedKeys.contains("welcome_message")) {
            Firebase.remoteConfig.activate()
          }
        }
    }
  }
}
