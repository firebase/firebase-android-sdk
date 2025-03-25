/*
 * Copyright 2024 Google LLC
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
package com.google.firebase.dataconnect.minimaldemo

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.LogLevel
import com.google.firebase.dataconnect.logLevel
import com.google.firebase.dataconnect.minimaldemo.connector.Ctry3q3tp6kzxConnector
import com.google.firebase.dataconnect.minimaldemo.connector.instance
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MyApplication : Application() {

  /**
   * A [CoroutineScope] whose lifetime matches that of this [Application] object.
   *
   * Namely, the scope will be cancelled when [onTerminate] is called.
   *
   * This scope's [Job] is a [SupervisorJob], and, therefore, uncaught exceptions will _not_
   * terminate the application.
   */
  val coroutineScope =
    CoroutineScope(
      SupervisorJob() +
        CoroutineName("MyApplication@${System.identityHashCode(this@MyApplication)}") +
        CoroutineExceptionHandler { context, throwable ->
          val coroutineName = context[CoroutineName]?.name
          Log.w(
            TAG,
            "WARNING: ignoring uncaught exception thrown from coroutine " +
              "named \"$coroutineName\": $throwable " +
              "(error code 8xrn9vvddd)",
            throwable,
          )
        }
    )

  private val initialLogLevel = FirebaseDataConnect.logLevel.value
  private val connectorMutex = Mutex()
  private var connector: Ctry3q3tp6kzxConnector? = null

  override fun onCreate() {
    super.onCreate()

    coroutineScope.launch {
      if (getDataConnectDebugLoggingEnabled()) {
        FirebaseDataConnect.logLevel.value = LogLevel.DEBUG
      }
    }
  }

  suspend fun getConnector(): Ctry3q3tp6kzxConnector {
    connectorMutex.withLock {
      val oldConnector = connector
      if (oldConnector !== null) {
        return oldConnector
      }

      val newConnector = Ctry3q3tp6kzxConnector.instance

      if (getUseDataConnectEmulator()) {
        newConnector.dataConnect.useEmulator()
      }

      connector = newConnector
      return newConnector
    }
  }

  private suspend fun getSharedPreferences(): SharedPreferences =
    withContext(Dispatchers.IO) {
      getSharedPreferences("MyApplicationSharedPreferences", MODE_PRIVATE)
    }

  suspend fun getDataConnectDebugLoggingEnabled(): Boolean =
    getSharedPreferences().all[SharedPrefsKeys.IS_DATA_CONNECT_LOGGING_ENABLED] as? Boolean ?: false

  suspend fun setDataConnectDebugLoggingEnabled(enabled: Boolean) {
    FirebaseDataConnect.logLevel.value = if (enabled) LogLevel.DEBUG else initialLogLevel
    editSharedPreferences { putBoolean(SharedPrefsKeys.IS_DATA_CONNECT_LOGGING_ENABLED, enabled) }
  }

  suspend fun getUseDataConnectEmulator(): Boolean =
    getSharedPreferences().all[SharedPrefsKeys.IS_USE_DATA_CONNECT_EMULATOR] as? Boolean ?: true

  suspend fun setUseDataConnectEmulator(enabled: Boolean) {
    val requiresRestart = getUseDataConnectEmulator() != enabled
    editSharedPreferences { putBoolean(SharedPrefsKeys.IS_USE_DATA_CONNECT_EMULATOR, enabled) }

    if (requiresRestart) {
      connectorMutex.withLock {
        val oldConnector = connector
        connector = null
        oldConnector?.dataConnect?.close()
      }
    }
  }

  private suspend fun editSharedPreferences(block: SharedPreferences.Editor.() -> Unit) {
    val prefs = getSharedPreferences()
    withContext(Dispatchers.IO) {
      val editor = prefs.edit()
      block(editor)
      if (!editor.commit()) {
        Log.w(
          TAG,
          "WARNING: failed to save changes to SharedPreferences; " +
            "ignoring the failure (error code wzy99s7jmy)",
        )
      }
    }
  }

  override fun onTerminate() {
    coroutineScope.cancel("MyApplication.onTerminate() called")
    super.onTerminate()
  }

  private object SharedPrefsKeys {
    const val IS_DATA_CONNECT_LOGGING_ENABLED = "isDataConnectDebugLoggingEnabled"
    const val IS_USE_DATA_CONNECT_EMULATOR = "useDataConnectEmulator"
  }

  companion object {
    private const val TAG = "MyApplication"
  }
}
