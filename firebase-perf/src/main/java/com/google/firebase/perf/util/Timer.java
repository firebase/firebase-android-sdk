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

import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import com.google.android.gms.common.util.VisibleForTesting;

/**
 * A Timer class provides both wall-clock (epoch) time and monotonic time (elapsedRealtime).
 * Milliseconds is the time unit used in private fields and computations, because that's the time
 * unit most widely available in Android APIs. Public methods only return time in microseconds,
 * because Fireperf proto requires microseconds.
 */
public class Timer implements Parcelable {

  /**
   * Wall-clock time or epoch time in microseconds. Do NOT use for duration because wall-clock is
   * not guaranteed to be monotonic: it can be set by the user or the phone network, thus it may
   * jump forwards or backwards unpredictably. {@see SystemClock}
   */
  private long wallClock;
  /**
   * Monotonic time measured in the {@link SystemClock#elapsedRealtime()} timebase. Only used to
   * compute duration between 2 timestamps in the same timebase. It is NOT wall-clock time.
   */
  private long elapsedRealtime;

  /**
   * Returns a new Timer object as if it was stamped at the given elapsedRealtime. Uses current
   * wall-clock as a reference to extrapolate the wall-clock at the given elapsedRealtime.
   *
   * @param elapsedRealtime timestamp in the {@link SystemClock#elapsedRealtime()} timebase
   */
  public static Timer ofElapsedRealtime(long elapsedRealtime) {
    Timer now = new Timer();
    long wallClock = now.wallClock + elapsedRealtime - now.elapsedRealtime;
    return new Timer(wallClock, elapsedRealtime);
  }

  // TODO: make all constructors private, use public static factory methods, per Effective Java
  /** Construct Timer object using System clock. */
  public Timer() {
    this(System.currentTimeMillis(), SystemClock.elapsedRealtime());
  }

  /**
   * Construct a Timer object with input wall-clock time and elapsedRealtime.
   *
   * @param epochTime wall-clock time in milliseconds since epoch
   * @param elapsedRealtime monotonic time in milliseconds in the {@link
   *     SystemClock#elapsedRealtime()} timebase
   */
  @VisibleForTesting
  Timer(long epochTime, long elapsedRealtime) {
    this.wallClock = epochTime;
    this.elapsedRealtime = elapsedRealtime;
  }

  private Timer(Parcel in) {
    this(in.readLong(), in.readLong());
  }

  /** Returns milliseconds duration from this until end. */
  private long durationUntil(Timer end) {
    return end.elapsedRealtime - this.elapsedRealtime;
  }

  /** resets the start time */
  public void reset() {
    // TODO: consider removing this method and make Timer immutable thus fully thread-safe
    wallClock = System.currentTimeMillis();
    elapsedRealtime = SystemClock.elapsedRealtime();
  }

  /** Return wall-clock time in microseconds. */
  public long getMicros() {
    return MILLISECONDS.toMicros(wallClock);
  }

  /**
   * Calculate duration in microseconds using elapsedRealtime.
   *
   * <p>The start time is this Timer object, end time is current time.
   *
   * @return duration in microseconds.
   */
  public long getDurationMicros() {
    return MILLISECONDS.toMicros(durationUntil(new Timer()));
  }

  /**
   * Calculate duration in microseconds using elapsedRealtime. The start time is this Timer object.
   *
   * @param end end Timer object
   * @return duration in microseconds.
   */
  public long getDurationMicros(@NonNull final Timer end) {
    return MILLISECONDS.toMicros(durationUntil(end));
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
    return MILLISECONDS.toMicros(wallClock + durationUntil(new Timer()));
  }

  /**
   * Return elapsedRealtime in microseconds, only useful for testing..
   *
   * @return elapsedRealtime in microseconds.
   */
  @VisibleForTesting
  public long getElapsedRealtimeMicros() {
    return MILLISECONDS.toMicros(elapsedRealtime);
  }

  /**
   * Flatten this object into a Parcel. Please refer to
   * https://developer.android.com/reference/android/os/Parcelable.html
   *
   * @param out the Parcel in which the object should be written.
   * @param flags always will be the value 0.
   */
  public void writeToParcel(Parcel out, int flags) {
    out.writeLong(wallClock);
    out.writeLong(elapsedRealtime);
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
