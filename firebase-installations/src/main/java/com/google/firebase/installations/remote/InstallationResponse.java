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

/** @hide */
@AutoValue
public abstract class InstallationResponse {

  public enum ResponseCode {
    // Returned on success
    OK,
    // The request is invalid. Do not try again without fixing the request. Usually means
    // a bad or misconfigured API Key or project is being used.
    BAD_CONFIG,
  }

  @Nullable
  public abstract String getUri();

  @Nullable
  public abstract String getFid();

  @Nullable
  public abstract String getRefreshToken();

  @Nullable
  public abstract TokenResult getAuthToken();

  @Nullable
  public abstract ResponseCode getResponseCode();

  @NonNull
  public abstract Builder toBuilder();

  /** Returns a default Builder object to create an InstallationResponse object */
  @NonNull
  public static InstallationResponse.Builder builder() {
    return new AutoValue_InstallationResponse.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    @NonNull
    public abstract Builder setUri(@NonNull String value);

    @NonNull
    public abstract Builder setFid(@NonNull String value);

    @NonNull
    public abstract Builder setRefreshToken(@NonNull String value);

    @NonNull
    public abstract Builder setAuthToken(@NonNull TokenResult value);

    @NonNull
    public abstract Builder setResponseCode(@NonNull ResponseCode value);

    @NonNull
    public abstract InstallationResponse build();
  }
}
