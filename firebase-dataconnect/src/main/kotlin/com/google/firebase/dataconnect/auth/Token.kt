// Copyright 2023 Google LLC
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
package com.google.firebase.dataconnect.auth

import java.util.*

/**
 * The current User and the authentication token provided by the underlying authentication
 * mechanism. This is the result of calling {@link CredentialsProvider#getToken}.
 */
internal interface Token {
  /** The actual raw token. */
  val value: String?
  /** The user which the token is associated. */
  val user: User
}

internal fun Token(value: String?, user: User): Token = TokenImpl(value, user)

internal class TokenImpl(override val value: String?, override val user: User) : Token {

  override fun hashCode() = Objects.hash(value, user)

  override fun equals(other: Any?) =
    other is TokenImpl && other.value == value && other.user == user

  override fun toString() = "TokenImpl(value=$value, user=$user)"
}
