// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.perf.util;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

/**
 * A Timer class provides both wall-clock (epoch) time and monotonic time (elapsedRealtime).
 * Timestamps are captured with millisecond-precision, because that's the time unit most widely
 * available in Android APIs. However, private fields and public method returns are in microseconds
 * due to Fireperf proto requirements.
 */
public class Timer implements Parcelable {

  /**
   * Wall-clock time or epoch time in microseconds. Do NOT use for duration because wall-clock is
   * not guaranteed to be monotonic: it can be set by the user or the phone network, thus it may
   * jump forwards or backwards unpredictably. {@see SystemClock}
   */
  private long wallClockMicros;
  /**
   * Monotonic time measured in the {@link SystemClock#elapsedRealtime()} timebase. Only used to
   * compute duration between 2 timestamps in the same timebase. It is NOT wall-clock time.
   */
  private long elapsedRealtimeMicros;

  /**
   * Returns a new Timer object as if it was stamped at the given elapsedRealtime. Uses current
   * wall-clock as a reference to extrapolate the wall-clock at the given elapsedRealtime.
   *
   * @param elapsedRealtimeMillis timestamp in the {@link SystemClock#elapsedRealtime()} timebase
   */
  public static Timer ofElapsedRealtime(final long elapsedRealtimeMillis) {
    long elapsedRealtimeMicros = MILLISECONDS.toMicros(elapsedRealtimeMillis);
    long wallClockMicros = wallClockMicros() + (elapsedRealtimeMicros - elapsedRealtimeMicros());
    return new Timer(wallClockMicros, elapsedRealtimeMicros);
  }

  /**
   * Helper to get current wall-clock time from system API.
   *
   * @return wall-clock time in microseconds.
   */
  private static long wallClockMicros() {
    return MILLISECONDS.toMicros(System.currentTimeMillis());
  }

  /**
   * Helper to get current {@link SystemClock#elapsedRealtime()} from system API.
   *
   * @return wall-clock time in microseconds.
   */
  private static long elapsedRealtimeMicros() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      return NANOSECONDS.toMicros(SystemClock.elapsedRealtimeNanos());
    }
    return MILLISECONDS.toMicros(SystemClock.elapsedRealtime());
  }

  // TODO: make all constructors private, use public static factory methods, per Effective Java
  /** Construct Timer object using System clock. */
  public Timer() {
    this(wallClockMicros(), elapsedRealtimeMicros());
  }

  /**
   * Construct a Timer object with input wall-clock time and elapsedRealtime.
   *
   * @param epochMicros wall-clock time in milliseconds since epoch
   * @param elapsedRealtimeMicros monotonic time in microseconds in the {@link
   *     SystemClock#elapsedRealtime()} timebase
   */
  @VisibleForTesting
  Timer(long epochMicros, long elapsedRealtimeMicros) {
    this.wallClockMicros = epochMicros;
    this.elapsedRealtimeMicros = elapsedRealtimeMicros;
  }

  /**
   * TEST-ONLY constructor that sets both wall-clock time and elapsedRealtime to the same input
   * value. Do NOT use this for any real logic because this is mixing 2 different time-bases.
   *
   * @param testTime value to set both wall-clock and elapsedRealtime to for testing purposes
   */
  @VisibleForTesting
  public Timer(long testTime) {
    this(testTime, testTime);
  }

  private Timer(Parcel in) {
    this(in.readLong(), in.readLong());
  }

  /** resets the start time */
  public void reset() {
    // TODO: consider removing this method and make Timer immutable thus fully thread-safe
    wallClockMicros = wallClockMicros();
    elapsedRealtimeMicros = elapsedRealtimeMicros();
  }

  /** Return wall-clock time in microseconds. */
  public long getMicros() {
    return wallClockMicros;
  }

  /**
   * Calculate duration in microseconds using elapsedRealtime.
   *
   * <p>The start time is this Timer object, end time is current time.
   *
   * @return duration in microseconds.
   */
  public long getDurationMicros() {
    return getDurationMicros(new Timer());
  }

  /**
   * Calculate duration in microseconds using elapsedRealtime. The start time is this Timer object.
   *
   * @param end end Timer object
   * @return duration in microseconds.
   */
  public long getDurationMicros(@NonNull final Timer end) {
    return end.elapsedRealtimeMicros - this.elapsedRealtimeMicros;
  }

  /**
   * Calculates the current wall clock off the existing wall clock time. The reason this is better
   * instead of just doing System.getCurrentTimeMillis is that the device time could've changed
   * which would result in an out of order time which can cause problems downstream.
   *
   * @return The system time in microseconds that's calibrated to the exact instant when this object
   *     was created.
   */
  public long getCurrentTimestampMicros() {
    return wallClockMicros + getDurationMicros();
  }

  /**
   * Flatten this object into a Parcel. Please refer to
   * https://developer.android.com/reference/android/os/Parcelable.html
   *
   * @param out the Parcel in which the object should be written.
   * @param flags always will be the value 0.
   */
  public void writeToParcel(Parcel out, int flags) {
    out.writeLong(wallClockMicros);
    out.writeLong(elapsedRealtimeMicros);
  }

  /**
   * A public static CREATOR field that implements the Parcelable.Creator interface, generates
   * instances of your Parcelable class from a Parcel. Please refer to
   * https://developer.android.com/reference/android/os/Parcelable.html
   */
  public static final Parcelable.Creator<Timer> CREATOR =
      new Parcelable.Creator<Timer>() {
        public Timer createFromParcel(Parcel in) {
          return new Timer(in);
        }

        public Timer[] newArray(int size) {
          return new Timer[size];
        }
      };

  /**
   * Describes the kinds of special objects contained in this Parcelable's marshalled
   * representation. Please refer to
   * https://developer.android.com/reference/android/os/Parcelable.html
   *
   * @return always returns 0.
   */
  public int describeContents() {
    return 0;
  }
}
