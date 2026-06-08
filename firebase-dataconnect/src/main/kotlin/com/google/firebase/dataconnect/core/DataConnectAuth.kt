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

package com.google.firebase.dataconnect.core

import com.google.firebase.annotations.DeferredApi
import com.google.firebase.auth.internal.IdTokenListener
import com.google.firebase.auth.internal.InternalAuthProvider
import com.google.firebase.dataconnect.core.DataConnectAuth.GetAuthTokenResult
import com.google.firebase.dataconnect.core.Globals.toScrubbedAccessToken
import com.google.firebase.dataconnect.util.IdStringGenerator
import com.google.firebase.internal.InternalTokenResult
import java.lang.ref.WeakReference
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.tasks.await

internal class DataConnectAuth(
  deferredAuthProvider: com.google.firebase.inject.Deferred<InternalAuthProvider>,
  idStringGenerator: IdStringGenerator,
  parentCoroutineScope: CoroutineScope,
  blockingDispatcher: CoroutineDispatcher,
  logger: Logger,
) :
  DataConnectCredentialsTokenManager<InternalAuthProvider, GetAuthTokenResult>(
    deferredProvider = deferredAuthProvider,
    idStringGenerator = idStringGenerator,
    parentCoroutineScope = parentCoroutineScope,
    blockingDispatcher = blockingDispatcher,
    logger = logger,
  ) {

  @Suppress("LeakingThis") private val weakThis = WeakReference(this)

  private val idTokenListener = IdTokenListenerImpl(weakThis)

  @DeferredApi
  override fun addTokenListener(provider: InternalAuthProvider) {
    provider.addIdTokenListener(idTokenListener)
  }

  override fun removeTokenListener(provider: InternalAuthProvider) {
    provider.removeIdTokenListener(idTokenListener)
  }

  override suspend fun getToken(provider: InternalAuthProvider, forceRefresh: Boolean) =
    provider.getAccessToken(forceRefresh).await().let {
      GetAuthTokenResult(it.token, it.getAuthUid())
    }

  override fun onClose() {
    weakThis.clear()
  }

  @JvmInline
  value class AuthUid(val string: String) {
    override fun toString() = "AuthUid($string)"
  }

  data class GetAuthTokenResult(override val token: String?, val authUid: AuthUid?) :
    GetTokenResult {
    override fun toString() =
      "GetAuthTokenResult(authUid=$authUid, token=${token?.toScrubbedAccessToken()})"
  }

  private class IdTokenListenerImpl(
    private val dataConnectAuthRef: WeakReference<DataConnectAuth>
  ) : IdTokenListener {
    override fun onIdTokenChanged(tokenResult: InternalTokenResult) {
      dataConnectAuthRef.get()?.onTokenChanged()
    }
  }

  private companion object {

    // The "sub" claim is documented to be "a non-empty string and must be the uid of the user or
    // device". See http://goo.gle/4oGjEQt for the relevant Firebase documentation.
    fun com.google.firebase.auth.GetTokenResult.getAuthUid(): AuthUid? {
      val sub = claims["sub"] as? String
      return if (sub === null) null else AuthUid(sub)
    }
  }
}
