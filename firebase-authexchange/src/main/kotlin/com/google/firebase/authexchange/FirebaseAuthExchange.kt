// Copyright 2022 Google LLC
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

package com.google.firebase.authexchange

import com.google.firebase.FirebaseApp
import com.google.firebase.authexchange.interop.InternalAuthExchangeListener
import com.google.firebase.authexchange.interop.InternalAuthExchangeProvider
import com.google.firebase.authexchange.interop.ListenerRegistration
import com.google.firebase.ktx.Firebase
import com.google.firebase.ktx.app

/** Returns the [FirebaseAuthExchange] instance of the default [FirebaseApp]. */
val Firebase.authExchange: FirebaseAuthExchange
  get() = FirebaseAuthExchange.instance

/** Returns the [FirebaseAuthExchange] instance of the given [FirebaseApp]. */
fun Firebase.authExchange(app: FirebaseApp): FirebaseAuthExchange =
  FirebaseAuthExchange.getInstance(app)

/** The entry point of the Firebase Auth Exchange SDK. */
class FirebaseAuthExchange : InternalAuthExchangeProvider {
  companion object {
    /**
     * Gets the default instance of [FirebaseAuthExchange], which is associated with the default
     * [FirebaseApp] instance.
     */
    @JvmStatic
    val instance: FirebaseAuthExchange
      get() = getInstance(Firebase.app)

    /**
     * Gets the instance of [FirebaseAuthExchange] that is associated with the given [FirebaseApp]
     * instance.
     */
    @JvmStatic
    fun getInstance(app: FirebaseApp): FirebaseAuthExchange =
      app.get(FirebaseAuthExchange::class.java)
  }

  /** Registers a [TokenRefreshHandler] to obtain new [AuthExchangeToken]s. */
  fun setTokenRefreshHandler(handler: TokenRefreshHandler) {
    TODO("Not yet implemented")
  }

  /**
   * Returns the stored [AuthExchangeToken] if it is valid, otherwise a new token is fetched from
   * the backend. If [forceRefresh] is true, then a new token is fetched regardless of the validity
   * of the stored token.
   *
   * In order for a new token to be successfully fetched, a [TokenRefreshHandler] must be
   * registered.
   */
  suspend fun getToken(forceRefresh: Boolean): AuthExchangeToken {
    TODO("Not yet implemented")
  }

  /** Clears the stored [AuthExchangeToken] and [TokenRefreshHandler], if one is set. */
  suspend fun clearState() {
    TODO("Not yet implemented")
  }

  /**
   * Exchanges a custom token for an [AuthExchangeToken] and updates the [FirebaseAuthExchange]
   * instance with the [AuthExchangeToken].
   */
  suspend fun updateWithCustomToken(token: String): AuthExchangeResult {
    TODO("Not yet implemented")
  }

  /**
   * Exchanges a Firebase Installations token for an [AuthExchangeToken] and updates the
   * [FirebaseAuthExchange] instance with the [AuthExchangeToken].
   */
  suspend fun updateWithInstallationsToken(): AuthExchangeResult {
    TODO("Not yet implemented")
  }

  /**
   * Exchanges an OIDC token for an [AuthExchangeToken] and updates the [FirebaseAuthExchange]
   * instance with the [AuthExchangeToken].
   */
  suspend fun updateWithOidcToken(token: String): AuthExchangeResult {
    TODO("Not yet implemented")
  }

  // Interop methods

  override suspend fun getTokenInternal(forceRefresh: Boolean): String {
    TODO("Not yet implemented")
  }

  override fun addInternalAuthExchangeListener(
    listener: InternalAuthExchangeListener
  ): ListenerRegistration {
    TODO("Not yet implemented")
  }
}
