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

package com.google.android.datatransport.cct;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.datatransport.runtime.Destination;
import com.google.auto.value.AutoValue;
import java.io.UnsupportedEncodingException;

@AutoValue
abstract class LegacyFlgDestination implements Destination {
  static final String DESTINATION_NAME = "lflg";
  static final String DEFAULT_API_KEY =
      CctBackendFactory.mergeStrings("AzSCki82AwsLzKd5O8zo", "IayckHiZRO1EFl1aGoK");

  @NonNull
  public abstract String getAPIKey();

  /** Returns a new builder for {@link LegacyFlgDestination}. */
  public static Builder builder() {
    return new AutoValue_LegacyFlgDestination.Builder().setAPIKey(DEFAULT_API_KEY);
  }

  @Nullable
  @Override
  public String getName() {
    return DESTINATION_NAME;
  }

  @Nullable
  @Override
  public byte[] getExtras() {
    return encodeString(getAPIKey());
  }

  @Nullable
  static byte[] encodeString(@NonNull String s) {
    try {
      return s.getBytes("UTF-8");
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    return null;
  }

  @Nullable
  private static String decodeByteArray(@NonNull byte[] a) {
    try {
      return new String(a, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    return null;
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setAPIKey(@NonNull String apiKey);

    public Builder withDefaultAPIKey() {
      return setAPIKey(DEFAULT_API_KEY);
    }

    public abstract LegacyFlgDestination build();

    Builder fromExtras(@NonNull byte[] extras) {
      String apiKey = decodeByteArray(extras);

      if (apiKey != null) {
        return setAPIKey(apiKey);
      }

      return null;
    }
  }
}
