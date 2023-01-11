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

/**
 * Provides a functional interface through which to obtain a new [AuthExchangeToken] when the
 * current token is at or near expiration, or a new token is explicitly requested.
 */
fun interface TokenRefreshHandler {
  /**
   * Callback that is invoked whenever the current [AuthExchangeToken] is at or near expiration, or
   * a new token is explicitly requested. Developers should implement this callback to request a new
   * token from an identity provider and then exchange it for a new [AuthExchangeToken] using one of
   * the exchange methods.
   */
  suspend fun refreshToken(authExchange: FirebaseAuthExchange): AuthExchangeToken
}
