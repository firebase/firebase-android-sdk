package com.google.firebase.time;

import com.google.auto.value.AutoValue;
import java.util.concurrent.TimeUnit;

@AutoValue
public abstract class Instant {
  public abstract long getMicros();

  public abstract long getNanos();

  public boolean isValid() {
    return getMicros() >= 0 && getNanos() >= 0;
  }

  public static Instant now() {
    return create(TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis()), System.nanoTime());
  }

  private static Instant create(long micros, long nanos) {
    return new AutoValue_Instant(micros, nanos);
  }

  public static Instant NEVER = create(-1, -1);
}
