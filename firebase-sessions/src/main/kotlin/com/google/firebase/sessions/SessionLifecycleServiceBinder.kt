/*
 * Copyright 2023 Google LLC
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

package com.google.firebase.sessions

import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Messenger
import android.util.Log
import com.google.firebase.FirebaseApp

/** Interface for binding with the [SessionLifecycleService]. */
internal fun interface SessionLifecycleServiceBinder {
  /**
   * Binds the given client callback [Messenger] to the [SessionLifecycleService]. The given
   * callback will be used to relay session updates to this client.
   */
  fun bindToService(callback: Messenger, serviceConnection: ServiceConnection)
}

internal class SessionLifecycleServiceBinderImpl(private val firebaseApp: FirebaseApp) :
  SessionLifecycleServiceBinder {

  override fun bindToService(callback: Messenger, serviceConnection: ServiceConnection) {
    val appContext = firebaseApp.applicationContext.applicationContext
    Intent(appContext, SessionLifecycleService::class.java).also { intent ->
      Log.d(TAG, "Binding service to application.")
      // This is necessary for the onBind() to be called by each process
      intent.action = android.os.Process.myPid().toString()
      intent.putExtra(SessionLifecycleService.CLIENT_CALLBACK_MESSENGER, callback)

      val isServiceBound =
        try {
          appContext.bindService(
            intent,
            serviceConnection,
            Context.BIND_IMPORTANT or Context.BIND_AUTO_CREATE,
          )
        } catch (ex: SecurityException) {
          Log.w(TAG, "Failed to bind session lifecycle service to application.", ex)
          false
        }
      if (!isServiceBound) {
        unbindServiceSafely(appContext, serviceConnection)
        Log.i(TAG, "Session lifecycle service binding failed.")
      }
    }
  }

  private fun unbindServiceSafely(appContext: Context, serviceConnection: ServiceConnection) =
    try {
      appContext.unbindService(serviceConnection)
    } catch (ex: IllegalArgumentException) {
      Log.w(TAG, "Session lifecycle service binding failed.", ex)
    }

  private companion object {
    const val TAG = "LifecycleServiceBinder"
  }
}
