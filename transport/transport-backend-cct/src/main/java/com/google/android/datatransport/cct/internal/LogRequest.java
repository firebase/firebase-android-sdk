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
import com.google.firebase.encoders.annotations.Encodable;
import java.util.List;

@AutoValue
public abstract class LogRequest {

  public abstract long getRequestTimeMs();

  /**
   * Current time since boot in milliseconds, including time spent in sleep, according to the same
   * clock as the one used to set the 'event_uptime_ms' values in the LogEvent protos above.
   */
  public abstract long getRequestUptimeMs();

  /** The ClientInfo at log time. */
  @Nullable
  public abstract ClientInfo getClientInfo();

  @Nullable
  public abstract Integer getLogSource();

  @Nullable
  public abstract String getLogSourceName();

  @Nullable
  @Encodable.Field(name = "logEvent")
  public abstract List<LogEvent> getLogEvents();

  @Nullable
  public abstract QosTier getQosTier();

  @NonNull
  public static Builder builder() {
    return new AutoValue_LogRequest.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    @NonNull
    public abstract Builder setRequestTimeMs(long value);

    @NonNull
    public abstract Builder setRequestUptimeMs(long value);

    @NonNull
    public abstract Builder setClientInfo(@Nullable ClientInfo value);

    @NonNull
    abstract Builder setLogSource(@Nullable Integer value);

    @NonNull
    abstract Builder setLogSourceName(@Nullable String value);

    @NonNull
    public Builder setSource(int value) {
      return setLogSource(value);
    }

    // TODO: enforce this at builder level.
    @NonNull
    public Builder setSource(@NonNull String value) {
      return setLogSourceName(value);
    }

    @NonNull
    public abstract Builder setLogEvents(@Nullable List<LogEvent> value);

    @NonNull
    public abstract Builder setQosTier(@Nullable QosTier value);

    @NonNull
    public abstract LogRequest build();
  }
}
