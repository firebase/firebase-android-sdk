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
import com.google.firebase.dataconnect.core.Globals.toScrubbedAccessToken
import com.google.firebase.dataconnect.core.LoggerGlobals.debug
import com.google.firebase.internal.InternalTokenResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.tasks.await

internal class DataConnectAuth(
  deferredAuthProvider: com.google.firebase.inject.Deferred<InternalAuthProvider>,
  parentCoroutineScope: CoroutineScope,
  blockingDispatcher: CoroutineDispatcher,
  logger: Logger,
) :
  DataConnectCredentialsTokenManager<InternalAuthProvider, IdTokenListener>(
    deferredProvider = deferredAuthProvider,
    parentCoroutineScope = parentCoroutineScope,
    blockingDispatcher = blockingDispatcher,
    logger = logger,
  ) {
  override fun newTokenListener(): IdTokenListener = IdTokenListenerImpl(logger)

  @DeferredApi
  override fun addTokenListener(provider: InternalAuthProvider, listener: IdTokenListener) =
    provider.addIdTokenListener(listener)

  override fun removeTokenListener(provider: InternalAuthProvider, listener: IdTokenListener) =
    provider.removeIdTokenListener(listener)

  override suspend fun getToken(provider: InternalAuthProvider, forceRefresh: Boolean) =
    provider.getAccessToken(forceRefresh).await().let { GetTokenResult(it.token) }

  private class IdTokenListenerImpl(private val logger: Logger) : IdTokenListener {
    override fun onIdTokenChanged(tokenResult: InternalTokenResult) {
      logger.debug { "onIdTokenChanged(token=${tokenResult.token?.toScrubbedAccessToken()})" }
    }
  }
}
