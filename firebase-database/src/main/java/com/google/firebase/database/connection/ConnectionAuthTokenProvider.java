// Copyright 2018 Google LLC
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

package com.google.firebase.database.connection;

public interface ConnectionAuthTokenProvider {

  interface GetTokenCallback {
    /**
     * Called if the getToken operation completed successfully. Token may be null if there is no
     * auth state currently.
     */
    void onSuccess(String token);

    /**
     * Called if the getToken operation fails.
     *
     * <p>TODO: Figure out sane way to plumb errors through.
     */
    void onError(String error);
  }

  /**
   * Gets the token that should currently be used for authenticated requests.
   *
   * @param forceRefresh Pass true to get a new, up-to-date token rather than a (potentially
   *     expired) cached token.
   * @param callback Callback to be notified after operation completes.
   */
  public void getToken(boolean forceRefresh, GetTokenCallback callback);
}
