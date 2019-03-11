package com.google.android.datatransport;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class Event<T> {
  public abstract T getPayload();

  public abstract Priority getPriority();

  public static <T> Event<T> ofData(T payload, Priority priority) {
    return new AutoValue_Event<>(payload, priority);
  }

  public static <T> Event<T> ofTelemetry(T value) {
    return new AutoValue_Event<>(value, Priority.DEFAULT);
  }
}
