// Copyright 2019 Google LLC
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

package com.google.firebase.installations;

import androidx.annotation.NonNull;

/** A set of values describing a FIS Auth Token Result. */
public class InstallationTokenResult {

  /** A new FIS Auth-Token, created for this firebase installation. */
  private final String authToken;
  /**
   * The amount of time, in milliseconds, before the auth-token expires for this firebase
   * installation.
   */
  private final long tokenExpirationTimestampMillis;

  public InstallationTokenResult(@NonNull String authToken, long tokenExpirationTimestampMillis) {
    this.authToken = authToken;
    this.tokenExpirationTimestampMillis = tokenExpirationTimestampMillis;
  }

  @NonNull
  public String getAuthToken() {
    return authToken;
  }

  public long getTokenExpirationTimestampMillis() {
    return tokenExpirationTimestampMillis;
  }
}
