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
import com.google.firebase.dataconnect.core.LoggerGlobals.debug
import com.google.firebase.internal.InternalTokenResult
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.tasks.await

internal class DataConnectAuth(
  deferredAuthProvider: com.google.firebase.inject.Deferred<InternalAuthProvider>,
  parentCoroutineScope: CoroutineScope,
  blockingDispatcher: CoroutineDispatcher,
  logger: Logger,
) :
  DataConnectCredentialsTokenManager<InternalAuthProvider, GetAuthTokenResult>(
    deferredProvider = deferredAuthProvider,
    parentCoroutineScope = parentCoroutineScope,
    blockingDispatcher = blockingDispatcher,
    logger = logger,
  ) {

  private data class ProviderIdTokenListenerPair(
    val provider: InternalAuthProvider,
    val idTokenListener: IdTokenListenerImpl,
  )

  private val providersLock = ReentrantLock()
  private val providers = mutableListOf<ProviderIdTokenListenerPair>()

  /**
   * The Firebase Auth UID of the current user, or `null` if Firebase Auth is not (yet) available or
   * if there is no logged-in user.
   */
  val authUid: String?
    get() = providersLock.withLock { providers.lastOrNull()?.provider?.uid }

  @DeferredApi
  override fun registerProvider(provider: InternalAuthProvider) {
    val idTokenListener = IdTokenListenerImpl(logger)
    provider.addIdTokenListener(idTokenListener)
    providersLock.withLock { providers.add(ProviderIdTokenListenerPair(provider, idTokenListener)) }
  }

  override fun unregisterProvider(provider: InternalAuthProvider) {
    val idTokenListener =
      providersLock.withLock {
        val index = providers.indexOfLast { it.provider === provider }
        if (index < 0) null else providers.removeAt(index).idTokenListener
      }
    idTokenListener?.let { provider.removeIdTokenListener(idTokenListener) }
  }

  override suspend fun getToken(provider: InternalAuthProvider, forceRefresh: Boolean) =
    provider.getAccessToken(forceRefresh).await().let {
      GetAuthTokenResult(it.token, it.getAuthUids())
    }

  data class GetAuthTokenResult(override val token: String?, val authUids: Set<String>) :
    GetTokenResult

  private class IdTokenListenerImpl(private val logger: Logger) : IdTokenListener {
    override fun onIdTokenChanged(tokenResult: InternalTokenResult) {
      logger.debug { "onIdTokenChanged(token=${tokenResult.token?.toScrubbedAccessToken()})" }
    }
  }

  private companion object {

    val authUidClaimNames = listOf("user_id", "sub")

    fun com.google.firebase.auth.GetTokenResult.getAuthUids(): Set<String> = buildSet {
      authUidClaimNames.forEach { claimName ->
        claims[claimName]?.let { claimValue ->
          if (claimValue is String) {
            add(claimValue)
          }
        }
      }
    }
  }
}
