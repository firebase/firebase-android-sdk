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
import androidx.annotation.NonNull;
import com.google.android.gms.common.util.VisibleForTesting;
import java.util.concurrent.TimeUnit;

/** A Timer class provides both wall-clock (epoch) time and high resolution time (nano time). */
public class Timer implements Parcelable {

  /** Wall-clock time or epoch time in microseconds, */
  private long timeInMicros;
  /**
   * High resolution time in nanoseconds. High resolution time should only be used to calculate
   * duration or latency. It is not wall-clock time.
   */
  private long highResTime;

  /**
   * Construct Timer object using System clock. Make it package visible to be only accessible from
   * com.google.firebase.perf.util.Clock.
   */
  public Timer() {
    timeInMicros = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis());
    highResTime = System.nanoTime();
  }

  /**
   * Construct a Timer object with input wall-clock time, assume high resolution time is same as
   * wall-clock time.
   *
   * @param time wall-clock time in microseconds
   */
  @VisibleForTesting
  public Timer(long time) {
    this.timeInMicros = time;
    highResTime = TimeUnit.MICROSECONDS.toNanos(time);
  }

  /**
   * Construct a Timer object with input wall-clock time and high resolution time.
   *
   * @param time wall-clock time in microseconds
   * @param highResTime high resolution time in nanoseconds
   */
  @VisibleForTesting
  public Timer(long time, long highResTime) {
    this.timeInMicros = time;
    this.highResTime = highResTime;
  }

  private Timer(Parcel in) {
    timeInMicros = in.readLong();
    highResTime = in.readLong();
  }

  /** resets the start time */
  public void reset() {
    timeInMicros = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis());
    highResTime = System.nanoTime();
  }

  /** Return wall-clock time in microseconds. */
  public long getMicros() {
    return timeInMicros;
  }

  /**
   * Calculate duration in microseconds using the the high resolution time.
   *
   * <p>The start time is this Timer object, end time is current time.
   *
   * @return duration in microseconds.
   */
  public long getDurationMicros() {
    return TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - this.highResTime);
  }

  /**
   * Calculate duration in microseconds using the the high resolution time. The start time is this
   * Timer object.
   *
   * @param end end Timer object
   * @return duration in microseconds.
   */
  public long getDurationMicros(@NonNull final Timer end) {
    return TimeUnit.NANOSECONDS.toMicros(end.highResTime - this.highResTime);
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
    return timeInMicros + getDurationMicros();
  }

  /**
   * Return high resolution time in microseconds, only useful for testing..
   *
   * @return high resolution time in microseconds.
   */
  @VisibleForTesting
  public long getHighResTime() {
    return TimeUnit.NANOSECONDS.toMicros(highResTime);
  }

  /**
   * Flatten this object into a Parcel. Please refer to
   * https://developer.android.com/reference/android/os/Parcelable.html
   *
   * @param out the Parcel in which the object should be written.
   * @param flags always will be the value 0.
   */
  public void writeToParcel(Parcel out, int flags) {
    out.writeLong(timeInMicros);
    out.writeLong(highResTime);
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
