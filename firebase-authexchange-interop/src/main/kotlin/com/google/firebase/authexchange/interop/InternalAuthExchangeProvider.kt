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

package com.google.firebase.authexchange.interop

/**
 * Firebase Auth Exchange SDK interop interface.
 *
 * @suppress
 */
interface InternalAuthExchangeProvider {

  /**
   * Returns the stored Auth Exchange token if it is valid, otherwise a new token is fetched from
   * the backend. If [forceRefresh] is true, then a new token is fetched regardless of the validity
   * of the stored token.
   *
   * This method is an interop method and intended for use only by other Firebase SDKs.
   */
  suspend fun getTokenInternal(forceRefresh: Boolean): String

  /**
   * Adds a listener that is notified when there are changes to the Auth Exchange token. Returns a
   * [ListenerRegistration] that can be used to remove the listener.
   *
   * This method is an interop method and intended for use only by other Firebase SDKs.
   */
  fun addInternalAuthExchangeListener(listener: InternalAuthExchangeListener): ListenerRegistration
}
