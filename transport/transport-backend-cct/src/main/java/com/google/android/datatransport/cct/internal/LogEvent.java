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

@AutoValue
public abstract class LogEvent {

  public abstract long getEventTimeMs();

  public abstract int getEventCode();

  public abstract long getEventUptimeMs();

  @SuppressWarnings("mutable")
  @Nullable
  public abstract byte[] getSourceExtension();

  @SuppressWarnings("mutable")
  @Nullable
  public abstract byte[] getSourceExtensionJsonProto3Bytes();

  public abstract long getTimezoneOffsetSeconds();

  @Nullable
  public abstract NetworkConnectionInfo getNetworkConnectionInfo();

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder setEventTimeMs(long value);

    abstract Builder setEventCode(int value);

    abstract Builder setEventUptimeMs(long value);

    abstract Builder setSourceExtension(byte[] value);

    abstract Builder setSourceExtensionJsonProto3Bytes(byte[] value);

    abstract Builder setTimezoneOffsetSeconds(long value);

    abstract Builder setNetworkConnectionInfo(NetworkConnectionInfo value);

    abstract LogEvent build();
  }
}
