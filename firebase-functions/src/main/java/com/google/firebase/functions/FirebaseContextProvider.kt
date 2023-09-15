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

/** A ContextProvider that uses FirebaseAuth to get the token.  */
@Singleton
internal class FirebaseContextProvider @Inject constructor(
        private val tokenProvider: Provider<InternalAuthProvider>,
        private val instanceId: Provider<FirebaseInstanceIdInternal>,
        appCheckDeferred: Deferred<InteropAppCheckTokenProvider>,
        @param:Lightweight private val executor: Executor) : ContextProvider {
  private val TAG = "FirebaseContextProvider"
  private val appCheckRef = AtomicReference<InteropAppCheckTokenProvider>()

  init {
    appCheckDeferred.whenAvailable { p: Provider<InteropAppCheckTokenProvider> ->
      val appCheck = p.get()
      appCheckRef.set(appCheck)
      appCheck.addAppCheckTokenListener { unused: AppCheckTokenResult? -> }
    }
  }

  override fun getContext(limitedUseAppCheckToken: Boolean): Task<HttpsCallableContext?>? {
    val authToken = authToken
    val appCheckToken = getAppCheckToken(limitedUseAppCheckToken)
    return Tasks.whenAll(authToken, appCheckToken)
            .onSuccessTask(
                    executor
            ) { v: Void? ->
              Tasks.forResult(
                      HttpsCallableContext(
                              authToken.result,
                              instanceId.get().token,
                              appCheckToken.result))
            }
  }

  private val authToken: Task<String?>
    private get() {
      val auth = tokenProvider.get()
              ?: return Tasks.forResult(null)
      return auth.getAccessToken(false)
              .continueWith(
                      executor
              ) { task: Task<GetTokenResult> ->
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

  private fun getAppCheckToken(limitedUseAppCheckToken: Boolean): Task<String?> {
    val appCheck = appCheckRef.get()
            ?: return Tasks.forResult(null)
    val tokenTask = if (limitedUseAppCheckToken) appCheck.limitedUseToken else appCheck.getToken(false)
    return tokenTask.onSuccessTask(
            executor
    ) { result: AppCheckTokenResult ->
      if (result.error != null) {
        // If there was an error getting the App Check token, do NOT send the placeholder
        // token. Only valid App Check tokens should be sent to the functions backend.
        Log.w(TAG, "Error getting App Check token. Error: " + result.error)
        return@onSuccessTask Tasks.forResult<String?>(null)
      }
      Tasks.forResult(result.token)
    }
  }
}