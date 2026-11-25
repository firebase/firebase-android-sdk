// Copyright 2018 Google LLC
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
package com.google.firebase.functions

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.annotations.concurrent.Lightweight
import com.google.firebase.appcheck.AppCheckTokenResult
import com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider
import com.google.firebase.auth.GetTokenResult
import com.google.firebase.auth.internal.InternalAuthProvider
import com.google.firebase.iid.internal.FirebaseInstanceIdInternal
import com.google.firebase.inject.Deferred
import com.google.firebase.inject.Provider
import com.google.firebase.internal.api.FirebaseNoSignedInUserException
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/** A ContextProvider that uses FirebaseAuth to get the token. */
@Singleton
internal class FirebaseContextProvider
@Inject
constructor(
  private val applicationContext: Context,
  private val tokenProvider: Provider<InternalAuthProvider>,
  private val instanceId: Provider<FirebaseInstanceIdInternal>,
  appCheckDeferred: Deferred<InteropAppCheckTokenProvider>,
  @param:Lightweight private val executor: Executor
) : ContextProvider {
  private val TAG = "FirebaseContextProvider"
  private val appCheckRef = AtomicReference<InteropAppCheckTokenProvider>()
  private val isFcmInstallationIdEnabled: Boolean by lazy { readFcmInstallationIdEnabled() }

  init {
    appCheckDeferred.whenAvailable { p: Provider<InteropAppCheckTokenProvider> ->
      val appCheck = p.get()
      appCheckRef.set(appCheck)
      appCheck.addAppCheckTokenListener {
        // Do nothing; we just need to register a listener so that the App Check SDK knows
        // to auto-refresh the token.
      }
    }
  }

  override fun getContext(getLimitedUseAppCheckToken: Boolean): Task<HttpsCallableContext?>? {
    val authToken = getAuthToken()
    val appCheckToken = getAppCheckToken(getLimitedUseAppCheckToken)
    return Tasks.whenAll(authToken, appCheckToken).onSuccessTask(executor) { _ ->
      Tasks.forResult(
        HttpsCallableContext(authToken.result, getInstanceIdToken(), appCheckToken.result)
      )
    }
  }

  /**
   * Returns the legacy FCM registration token for the `Firebase-Instance-ID-Token` header.
   *
   * Returns null if FCM registers with the Firebase installation ID
   * (`firebase_messaging_installation_id_enabled` is true). Getting a legacy token then creates a
   * second registration for the app instance, and FCM rejects sends to the installation ID.
   */
  private fun getInstanceIdToken(): String? {
    if (isFcmInstallationIdEnabled) {
      return null
    }
    return instanceId.get()?.token
  }

  /** Reads the manifest flag that firebase-messaging uses to register with the installation ID. */
  private fun readFcmInstallationIdEnabled(): Boolean {
    return try {
      val packageManager = applicationContext.packageManager ?: return false
      val applicationInfo =
        packageManager.getApplicationInfo(
          applicationContext.packageName,
          PackageManager.GET_META_DATA
        )
      applicationInfo.metaData?.getBoolean(FCM_INSTALLATION_ID_ENABLED_METADATA_KEY) ?: false
    } catch (e: Exception) {
      // NameNotFoundException can't happen for our own package, but PackageManager calls can also
      // throw RuntimeExceptions if the system server fails. Don't fail every call because of that.
      Log.w(TAG, "Couldn't read $FCM_INSTALLATION_ID_ENABLED_METADATA_KEY from the manifest.", e)
      false
    }
  }

  private fun getAuthToken(): Task<String?> {
    val auth = tokenProvider.get() ?: return Tasks.forResult(null)
    return auth.getAccessToken(false).continueWith(executor) { task: Task<GetTokenResult> ->
      var authToken: String? = null
      if (!task.isSuccessful) {
        val exception = task.exception
        if (exception is FirebaseNoSignedInUserException) {
          // Firebase Auth is linked in, but nobody is signed in, which is fine.
        } else {
          throw exception!!
        }
      } else {
        authToken = task.result.token
      }
      authToken
    }
  }

  private fun getAppCheckToken(getLimitedUseAppCheckToken: Boolean): Task<String?> {
    val appCheck = appCheckRef.get() ?: return Tasks.forResult(null)
    val tokenTask =
      if (getLimitedUseAppCheckToken) appCheck.limitedUseToken else appCheck.getToken(false)
    return tokenTask.onSuccessTask(executor) { result: AppCheckTokenResult ->
      if (result.error != null) {
        Log.w(TAG, "Error getting App Check token. Error: " + result.error)
      }
      // Send valid token (success) or placeholder (failure).
      Tasks.forResult(result.token)
    }
  }

  private companion object {
    private const val FCM_INSTALLATION_ID_ENABLED_METADATA_KEY =
      "firebase_messaging_installation_id_enabled"
  }
}
