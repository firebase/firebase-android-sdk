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

import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import com.google.android.gms.common.util.VisibleForTesting;
import java.util.concurrent.TimeUnit;

/** A Timer class provides both wall-clock (epoch) time and monotonic time (elapsedRealtime). */
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
  private long elapsedRealtimeNanos;

  /**
   * Construct Timer object using System clock. Make it package visible to be only accessible from
   * com.google.firebase.perf.util.Clock.
   */
  public Timer() {
    wallClockMicros = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis());
    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos();
  }

  /**
   * Returns a new Timer object with input elapsedRealtime. Uses current wall-clock as a reference
   * to extrapolate the wall-clock at the time of the input elapsedRealtime.
   *
   * @param elapsedRealtimeNanos timestamp in the {@link SystemClock#elapsedRealtime()} timebase
   */
  public static Timer of(long elapsedRealtimeNanos) {
    Timer now = new Timer();
    long wallClock =
        now.wallClockMicros
            + TimeUnit.NANOSECONDS.toMicros(elapsedRealtimeNanos - now.elapsedRealtimeNanos);
    return new Timer(wallClock, elapsedRealtimeNanos);
  }

  /**
   * Construct a Timer object with input wall-clock time and high resolution time.
   *
   * @param time wall-clock time in microseconds
   * @param elapsedRealtimeNanos high resolution time in nanoseconds
   */
  @VisibleForTesting
  Timer(long time, long elapsedRealtimeNanos) {
    this.wallClockMicros = time;
    this.elapsedRealtimeNanos = elapsedRealtimeNanos;
  }

  private Timer(Parcel in) {
    wallClockMicros = in.readLong();
    elapsedRealtimeNanos = in.readLong();
  }

  /** resets the start time */
  public void reset() {
    wallClockMicros = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis());
    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos();
  }

  /** Return wall-clock time in microseconds. */
  public long getMicros() {
    return wallClockMicros;
  }

  /**
   * Calculate duration in microseconds using the the high resolution time.
   *
   * <p>The start time is this Timer object, end time is current time.
   *
   * @return duration in microseconds.
   */
  public long getDurationMicros() {
    return TimeUnit.NANOSECONDS.toMicros(
        SystemClock.elapsedRealtimeNanos() - this.elapsedRealtimeNanos);
  }

  /**
   * Calculate duration in microseconds using the the high resolution time. The start time is this
   * Timer object.
   *
   * @param end end Timer object
   * @return duration in microseconds.
   */
  public long getDurationMicros(@NonNull final Timer end) {
    return TimeUnit.NANOSECONDS.toMicros(end.elapsedRealtimeNanos - this.elapsedRealtimeNanos);
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
   * Return high resolution time in microseconds, only useful for testing..
   *
   * @return high resolution time in microseconds.
   */
  @VisibleForTesting
  public long getElapsedRealtimeMicros() {
    return TimeUnit.NANOSECONDS.toMicros(elapsedRealtimeNanos);
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
    out.writeLong(elapsedRealtimeNanos);
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
