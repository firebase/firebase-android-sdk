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
import com.google.auto.value.AutoValue;

/** This class represents a set of values describing a FIS Auth Token Result. */
@AutoValue
public abstract class InstallationTokenResult {

  /** A new FIS Auth-Token, created for this firebase installation. */
  @NonNull
  public abstract String getAuthToken();
  /**
   * The amount of time, in milliseconds, before the auth-token expires for this firebase
   * installation.
   */
  public abstract long getTokenExpirationTimestampMillis();

  @NonNull
  public static InstallationTokenResult create(
      @NonNull String authToken, long tokenExpirationTimestampMillis) {
    return new AutoValue_InstallationTokenResult(authToken, tokenExpirationTimestampMillis);
  }
}
