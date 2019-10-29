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

import androidx.annotation.Nullable;
import com.google.auto.value.AutoValue;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@AutoValue
public abstract class EventInternal {
  public abstract String getTransportName();

  @Nullable
  public abstract Integer getCode();

  public abstract EncodedPayload getEncodedPayload();

  @Deprecated
  public byte[] getPayload() {
    return getEncodedPayload().getBytes();
  }

  public abstract long getEventMillis();

  public abstract long getUptimeMillis();

  protected abstract Map<String, String> getAutoMetadata();

  public final Map<String, String> getMetadata() {
    return Collections.unmodifiableMap(getAutoMetadata());
  }

  public final String getOrDefault(String key, String defaultValue) {
    String value = getAutoMetadata().get(key);
    return value == null ? defaultValue : value;
  }

  public final int getInteger(String key) {
    String value = getAutoMetadata().get(key);
    return value == null ? 0 : Integer.valueOf(value);
  }

  public final long getLong(String key) {
    String value = getAutoMetadata().get(key);
    return value == null ? 0L : Long.valueOf(value);
  }

  public final String get(String key) {
    String value = getAutoMetadata().get(key);
    return value == null ? "" : value;
  }

  public Builder toBuilder() {
    return new AutoValue_EventInternal.Builder()
        .setTransportName(getTransportName())
        .setCode(getCode())
        .setEncodedPayload(getEncodedPayload())
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

    public abstract Builder setCode(Integer value);

    public abstract Builder setEncodedPayload(EncodedPayload value);

    public abstract Builder setEventMillis(long value);

    public abstract Builder setUptimeMillis(long value);

    protected abstract Builder setAutoMetadata(Map<String, String> metadata);

    protected abstract Map<String, String> getAutoMetadata();

    public final Builder addMetadata(String key, String value) {
      getAutoMetadata().put(key, value);
      return this;
    }

    public final Builder addMetadata(String key, long value) {
      getAutoMetadata().put(key, String.valueOf(value));
      return this;
    }

    public final Builder addMetadata(String key, int value) {
      getAutoMetadata().put(key, String.valueOf(value));
      return this;
    }

    public abstract EventInternal build();
  }
}
