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
public abstract class LogEvent {

  public abstract long getEventTimeMs();

  @Nullable
  public abstract Integer getEventCode();

  public abstract long getEventUptimeMs();

  @SuppressWarnings("mutable")
  @Nullable
  public abstract byte[] getSourceExtension();

  @SuppressWarnings("mutable")
  @Nullable
  public abstract String getSourceExtensionJsonProto3();

  public abstract long getTimezoneOffsetSeconds();

  @Nullable
  public abstract NetworkConnectionInfo getNetworkConnectionInfo();

  @NonNull
  public static Builder protoBuilder(@NonNull byte[] sourceExtension) {
    return builder().setSourceExtension(sourceExtension);
  }

  @NonNull
  public static Builder jsonBuilder(@NonNull String sourceJsonExtension) {
    return builder().setSourceExtensionJsonProto3(sourceJsonExtension);
  }

  private static Builder builder() {
    return new AutoValue_LogEvent.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    @NonNull
    public abstract Builder setEventTimeMs(long value);

    @NonNull
    public abstract Builder setEventCode(@Nullable Integer value);

    @NonNull
    public abstract Builder setEventUptimeMs(long value);

    @NonNull
    abstract Builder setSourceExtension(@Nullable byte[] value);

    @NonNull
    abstract Builder setSourceExtensionJsonProto3(@Nullable String value);

    @NonNull
    public abstract Builder setTimezoneOffsetSeconds(long value);

    @NonNull
    public abstract Builder setNetworkConnectionInfo(@Nullable NetworkConnectionInfo value);

    @NonNull
    public abstract LogEvent build();
  }
}
