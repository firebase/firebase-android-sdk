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

package com.google.firebase;

import static com.google.firebase.firestore.util.Preconditions.checkArgument;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;
import java.util.Date;

/**
 * A Timestamp represents a point in time independent of any time zone or calendar, represented as
 * seconds and fractions of seconds at nanosecond resolution in UTC Epoch time. It is encoded using
 * the Proleptic Gregorian Calendar which extends the Gregorian calendar backwards to year one. It
 * is encoded assuming all minutes are 60 seconds long, i.e. leap seconds are "smeared" so that no
 * leap second table is needed for interpretation. Range is from 0001-01-01T00:00:00Z to
 * 9999-12-31T23:59:59.999999999Z. By restricting to that range, we ensure that we can convert to
 * and from RFC 3339 date strings.
 *
 * @see <a href="
 *     https://github.com/google/protobuf/blob/master/src/google/protobuf/timestamp.proto">The
 *     reference timestamp definition</a>
 */
public final class Timestamp implements Comparable<Timestamp>, Parcelable {

  @NonNull
  public static final Parcelable.Creator<Timestamp> CREATOR =
      new Parcelable.Creator<Timestamp>() {
        @Override
        public Timestamp createFromParcel(Parcel source) {
          return new Timestamp(source);
        }

        @Override
        public Timestamp[] newArray(int size) {
          return new Timestamp[size];
        }
      };

  /**
   * Creates a new timestamp.
   *
   * @param seconds represents seconds of UTC time since Unix epoch 1970-01-01T00:00:00Z. Must be
   *     from 0001-01-01T00:00:00Z to 9999-12-31T23:59:59Z inclusive.
   * @param nanoseconds represents non-negative fractions of a second at nanosecond resolution.
   *     Negative second values with fractions must still have non-negative nanoseconds values that
   *     count forward in time. Must be from 0 to 999,999,999 inclusive.
   */
  public Timestamp(long seconds, int nanoseconds) {
    validateRange(seconds, nanoseconds);
    this.seconds = seconds;
    this.nanoseconds = nanoseconds;
  }

  protected Timestamp(@NonNull Parcel in) {
    this.seconds = in.readLong();
    this.nanoseconds = in.readInt();
  }

  /** Creates a new timestamp from the given date. */
  public Timestamp(@NonNull Date date) {
    long millis = date.getTime();
    long seconds = millis / 1000;
    int nanoseconds = (int) (millis % 1000) * 1000000;
    if (nanoseconds < 0) {
      seconds -= 1;
      nanoseconds += 1000000000;
    }
    validateRange(seconds, nanoseconds);
    this.seconds = seconds;
    this.nanoseconds = nanoseconds;
  }

  /** Creates a new timestamp with the current date, with millisecond precision. */
  @NonNull
  public static Timestamp now() {
    return new Timestamp(new Date());
  }

  /** Returns the seconds part of the timestamp. */
  public long getSeconds() {
    return seconds;
  }

  /** Returns the sub-second part of the timestamp, in nanoseconds. */
  public int getNanoseconds() {
    return nanoseconds;
  }

  /** Returns a new Date corresponding to this timestamp. This may lose precision. */
  @NonNull
  public Date toDate() {
    return new Date(seconds * 1000 + (nanoseconds / 1000000));
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(@NonNull Parcel dest, int flags) {
    dest.writeLong(this.seconds);
    dest.writeInt(this.nanoseconds);
  }

  @Override
  public int compareTo(@NonNull Timestamp other) {
    if (seconds == other.seconds) {
      return Integer.signum(nanoseconds - other.nanoseconds);
    }
    return Long.signum(seconds - other.seconds);
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof Timestamp)) {
      return false;
    }
    return compareTo((Timestamp) other) == 0;
  }

  @Override
  public int hashCode() {
    int prime = 37;
    int result = prime * (int) seconds;
    result = prime * result + (int) (seconds >> 32);
    result = prime * result + nanoseconds;
    return result;
  }

  @Override
  public String toString() {
    return "Timestamp(seconds=" + seconds + ", nanoseconds=" + nanoseconds + ")";
  }

  private static void validateRange(long seconds, int nanoseconds) {
    checkArgument(nanoseconds >= 0, "Timestamp nanoseconds out of range: %s", nanoseconds);
    checkArgument(nanoseconds < 1e9, "Timestamp nanoseconds out of range: %s", nanoseconds);
    // Midnight at the beginning of 1/1/1 is the earliest supported timestamp.
    checkArgument(seconds >= -62135596800L, "Timestamp seconds out of range: %s", seconds);
    // This will break in the year 10,000.
    checkArgument(seconds < 253402300800L, "Timestamp seconds out of range: %s", seconds);
  }

  private final long seconds;

  private final int nanoseconds;
}
