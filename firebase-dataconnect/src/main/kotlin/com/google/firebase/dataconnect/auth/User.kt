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
 * Simple wrapper around a nullable UID. Mostly exists to make code more readable and for use as a
 * key in maps (since keys cannot be null).
 */
public abstract class User {
  public open val uid: String? = null

  public open fun isAuthenticated(): Boolean {
    return false
  }

  public companion object {
    internal val UNAUTHENTICATED: User = UserImpl(null)
  }
}

internal fun User(uid: String?): User = UserImpl(uid)

internal class UserImpl(override val uid: String?) : User() {

  override fun isAuthenticated(): Boolean {
    return uid != null
  }

  override fun hashCode() = Objects.hash(uid)

  override fun equals(other: Any?) = other is UserImpl && other.uid == uid

  override fun toString() = "UserImpl(uid=$uid)"
}
