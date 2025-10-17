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
import com.google.firebase.appcheck.AppCheckTokenResult
import com.google.firebase.appcheck.interop.AppCheckTokenListener
import com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider
import com.google.firebase.dataconnect.core.DataConnectAppCheck.GetAppCheckTokenResult
import com.google.firebase.dataconnect.core.Globals.toScrubbedAccessToken
import com.google.firebase.dataconnect.core.LoggerGlobals.debug
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.tasks.await

internal class DataConnectAppCheck(
  deferredAppCheckTokenProvider: com.google.firebase.inject.Deferred<InteropAppCheckTokenProvider>,
  parentCoroutineScope: CoroutineScope,
  blockingDispatcher: CoroutineDispatcher,
  logger: Logger,
) :
  DataConnectCredentialsTokenManager<InteropAppCheckTokenProvider, GetAppCheckTokenResult>(
    deferredProvider = deferredAppCheckTokenProvider,
    parentCoroutineScope = parentCoroutineScope,
    blockingDispatcher = blockingDispatcher,
    logger = logger,
  ) {
  private val appCheckTokenListener = AppCheckTokenListenerImpl(logger)

  @DeferredApi
  override fun registerProvider(provider: InteropAppCheckTokenProvider) =
    provider.addAppCheckTokenListener(appCheckTokenListener)

  override fun unregisterProvider(provider: InteropAppCheckTokenProvider) =
    provider.removeAppCheckTokenListener(appCheckTokenListener)

  override suspend fun getToken(provider: InteropAppCheckTokenProvider, forceRefresh: Boolean) =
    provider.getToken(forceRefresh).await().let { GetAppCheckTokenResult(it.token) }

  data class GetAppCheckTokenResult(override val token: String?) : GetTokenResult

  private class AppCheckTokenListenerImpl(private val logger: Logger) : AppCheckTokenListener {
    override fun onAppCheckTokenChanged(tokenResult: AppCheckTokenResult) {
      logger.debug { "onAppCheckTokenChanged(token=${tokenResult.token.toScrubbedAccessToken()})" }
    }
  }
}
