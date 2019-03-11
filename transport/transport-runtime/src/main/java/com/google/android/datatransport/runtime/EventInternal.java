// Copyright 2018 Google LLC
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

package com.google.android.datatransport.runtime;

import com.google.android.datatransport.Priority;
import com.google.auto.value.AutoValue;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@AutoValue
public abstract class EventInternal {
  // send source
  public abstract String getTransportName();

  public abstract byte[] getPayload();

  public abstract Priority getPriority();

  public abstract long getEventMillis();

  public abstract long getUptimeMillis();

  protected abstract Map<String, String> getAutoMetadata();

  public final Map<String, String> getMetadata() {
    return Collections.unmodifiableMap(getAutoMetadata());
  }

  public Builder toBuilder() {
    return new AutoValue_EventInternal.Builder()
        .setTransportName(getTransportName())
        .setPayload(getPayload())
        .setPriority(getPriority())
        .setEventMillis(getEventMillis())
        .setUptimeMillis(getUptimeMillis())
        .setAutoMetadata(new HashMap<>(getAutoMetadata()));
  }

  public static Builder builder() {
    return new AutoValue_EventInternal.Builder().setAutoMetadata(new HashMap<>());
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setTransportName(String value);

    public abstract Builder setPayload(byte[] value);

    public abstract Builder setPriority(Priority value);

    public abstract Builder setEventMillis(long value);

    public abstract Builder setUptimeMillis(long value);

    protected abstract Builder setAutoMetadata(Map<String, String> metadata);

    protected abstract Map<String, String> getAutoMetadata();

    public final Builder addMetadata(String key, String value) {
      getAutoMetadata().put(key, value);
      return this;
    }

    public abstract EventInternal build();
  }
}
