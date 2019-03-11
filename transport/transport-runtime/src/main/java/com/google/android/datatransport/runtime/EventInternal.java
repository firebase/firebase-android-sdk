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
