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

import com.google.firebase.dataconnect.util.Listener

/** A CredentialsProvider has a method to fetch an authorization token. */
public interface CredentialsProvider<ListenerType> {
  /**
   * Requests token for the current user. Use {@link #invalidateToken} to force-refresh the token.
   *
   * @return A Task that will be completed with the current token.
   */
  public suspend fun getToken(): String?

  /**
   * Marks the last retrieved token as invalid, making the next {@link #getToken} request force
   * refresh the token.
   */
  public suspend fun invalidateToken()

  /**
   * Sets the listener to be notified of credential changes (sign-in / sign-out, token changes). It
   * is immediately called once with the initial user.
   */
  public suspend fun setChangeListener(changeListener: Listener<ListenerType>)

  /** Removes the listener set with {@link #setChangeListener}. */
  public suspend fun removeChangeListener()
}
