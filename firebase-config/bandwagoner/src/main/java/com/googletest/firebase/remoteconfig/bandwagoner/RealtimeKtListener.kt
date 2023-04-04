package com.googletest.firebase.remoteconfig.bandwagoner

import android.util.Log
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.configUpdates
import com.google.firebase.remoteconfig.ktx.remoteConfig
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.future.future
import java.util.concurrent.CompletableFuture

class RealtimeKtListener {
    companion object {
        private val TAG = "RealtimeListener"

        fun listenForUpdatesAsync(): CompletableFuture<Unit> =
            GlobalScope.future { listenForUpdates() }

        private suspend fun listenForUpdates() {
            Firebase.remoteConfig.configUpdates
                .catch { exception ->
                    Log.w(TAG, "Error listening for updates!", exception)
                }
                .collect { configUpdate ->
                    if (configUpdate.updatedKeys.contains("welcome_message")) {
                        Firebase.remoteConfig.activate()
                    }
                }
        }


    }
}