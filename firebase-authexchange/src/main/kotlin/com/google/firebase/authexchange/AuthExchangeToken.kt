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

/** Convenience wrapper around the Auth Exchange token returned from the backend. */
class AuthExchangeToken(val token: String, val expireTimeMillis: Long) {
  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }
    if (other !is AuthExchangeToken) {
      return false
    }

    return token == other.token && expireTimeMillis == other.expireTimeMillis
  }

  override fun hashCode() = 31 * token.hashCode() + expireTimeMillis.hashCode()

  override fun toString() = "AuthExchangeToken{token=$token, expireTimeMillis=$expireTimeMillis}"
}
