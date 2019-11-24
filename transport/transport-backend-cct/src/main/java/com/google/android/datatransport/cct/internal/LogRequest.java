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

import androidx.annotation.Nullable;
import com.google.auto.value.AutoValue;
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

  public abstract int getLogSource();

  @Nullable
  public abstract String getLogSourceName();

  @Nullable
  public abstract List<LogEvent> getLogEvents();

  static Builder builder() {
    return new AutoValue_LogRequest.Builder().setLogSource(Integer.MIN_VALUE);
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder setRequestTimeMs(long value);

    abstract Builder setRequestUptimeMs(long value);

    abstract Builder setClientInfo(ClientInfo value);

    abstract Builder setLogSource(int value);

    abstract Builder setLogSourceName(String value);

    abstract Builder setLogEvents(List<LogEvent> value);

    abstract LogRequest build();
  }
}
