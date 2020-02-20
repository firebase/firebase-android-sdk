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

package com.google.android.datatransport.cct.internal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class ClientInfo {
  public enum ClientType {
    UNKNOWN(0),
    ANDROID_FIREBASE(23);

    private final int value;

    ClientType(int value) {
      this.value = value;
    }
  }

  /** The client type for this client. One of the enum values defined above. */
  @Nullable
  public abstract ClientType getClientType();

  @Nullable
  public abstract AndroidClientInfo getAndroidClientInfo();

  @NonNull
  public static Builder builder() {
    return new AutoValue_ClientInfo.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    @NonNull
    public abstract Builder setClientType(@Nullable ClientType value);

    @NonNull
    public abstract Builder setAndroidClientInfo(@Nullable AndroidClientInfo value);

    @NonNull
    public abstract ClientInfo build();
  }
}
