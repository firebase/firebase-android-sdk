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

package com.google.firebase.perf.metrics;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Counter helps to log occurrences of a specific event within a {@link Trace}
 *
 * @hide
 */
public class Counter implements Parcelable {

  private final String name;
  private final AtomicLong count;

  /**
   * Creates a Counter with given name.
   *
   * @param name Name of the counter.
   */
  public Counter(@NonNull String name) {
    this.name = name;
    count = new AtomicLong(0);
  }

  private Counter(Parcel in) {
    name = in.readString();
    count = new AtomicLong(in.readLong());
  }

  /**
   * Increments this counter.
   *
   * @param counts A number by which the counter is incremented.
   */
  public void increment(long counts) {
    count.addAndGet(counts);
  }

  /**
   * Get name of this counter.
   *
   * @return Name of this counter.
   */
  @NonNull
  String getName() {
    return name;
  }

  /**
   * Get current count on this counter.
   *
   * @return Current count of this counter.
   */
  long getCount() {
    return count.get();
  }

  /**
   * Set a new count for the counter.
   *
   * @param newCount The new count for the counter.
   */
  void setCount(long newCount) {
    count.set(newCount);
  }

  /**
   * Flatten this object into a Parcel. Please refer to
   * https://developer.android.com/reference/android/os/Parcelable.html
   *
   * @param out the Parcel in which the object should be written.
   * @param flags always will be the value 0.
   */
  public void writeToParcel(Parcel out, int flags) {
    out.writeString(name);
    out.writeLong(count.get());
  }

  /**
   * A public static CREATOR field that implements the Parcelable.Creator interface, generates
   * instances of your Parcelable class from a Parcel. Please refer to
   * https://developer.android.com/reference/android/os/Parcelable.html
   */
  public static final Parcelable.Creator<Counter> CREATOR =
      new Parcelable.Creator<Counter>() {
        public Counter createFromParcel(Parcel in) {
          return new Counter(in);
        }

        public Counter[] newArray(int size) {
          return new Counter[size];
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
