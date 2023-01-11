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
 * Wrapper around the Auth Exchange response from the backend. This will always contain the
 * [AuthExchangeToken]. For some provider flows (i.e. OIDC), a provider ID token and refresh token
 * may also be returned.
 */
class AuthExchangeResult(
  val authExchangeToken: AuthExchangeToken,
  val providerIdToken: String?,
  val providerRefreshToken: String?
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }
    if (other !is AuthExchangeResult) {
      return false
    }

    return authExchangeToken == other.authExchangeToken &&
      providerIdToken == other.providerIdToken &&
      providerRefreshToken == other.providerRefreshToken
  }

  override fun hashCode(): Int {
    var result = authExchangeToken.hashCode()
    result = 31 * result + (providerIdToken?.hashCode() ?: 0)
    result = 31 * result + (providerRefreshToken?.hashCode() ?: 0)
    return result
  }

  override fun toString() =
    "AuthExchangeResult{authExchangeToken=$authExchangeToken, providerIdToken=$providerIdToken, providerRefreshToken=$providerRefreshToken}"
}
