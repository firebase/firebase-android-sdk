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

package com.google.firebase.installations.remote;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.auto.value.AutoValue;

/**
 * This class represents a set of values describing a FIS Auth Token Result.
 *
 * @hide
 */
@AutoValue
public abstract class TokenResult {

  public enum ResponseCode {
    // Returned on success
    OK,
    // Auth token cannot be generated for this FID in the request. Because it is not
    // registered/found on the FIS server. Recreate a new fid to fetch a valid auth token.
    BAD_CONFIG,
    // Refresh token in this request in not accepted by the FIS server. Either it has been blocked
    // or changed. Recreate a new fid to fetch a valid auth token.
    AUTH_ERROR,
  }

  /** A new FIS Auth-Token, created for this Firebase Installation. */
  @Nullable
  public abstract String getToken();
  /** The timestamp, before the auth-token expires for this Firebase Installation. */
  @NonNull
  public abstract long getTokenExpirationTimestamp();

  @Nullable
  public abstract ResponseCode getResponseCode();

  @NonNull
  public abstract Builder toBuilder();

  /** Returns a default Builder object to create an InstallationResponse object */
  @NonNull
  public static TokenResult.Builder builder() {
    return new AutoValue_TokenResult.Builder().setTokenExpirationTimestamp(0);
  }

  @AutoValue.Builder
  public abstract static class Builder {
    @NonNull
    public abstract Builder setToken(@NonNull String value);

    @NonNull
    public abstract Builder setTokenExpirationTimestamp(long value);

    @NonNull
    public abstract Builder setResponseCode(@NonNull ResponseCode value);

    @NonNull
    public abstract TokenResult build();
  }
}
