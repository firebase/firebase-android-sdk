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

package com.google.firebase.firestore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.firestore.util.Util;

/** Immutable class representing a {@code GeoPoint} in Cloud Firestore */
public class GeoPoint implements Comparable<GeoPoint> {
  private final double latitude;
  private final double longitude;

  /**
   * Construct a new {@code GeoPoint} using the provided latitude and longitude values.
   *
   * @param latitude The latitude of this {@code GeoPoint} in the range [-90, 90].
   * @param longitude The longitude of this {@code GeoPoint} in the range [-180, 180].
   */
  public GeoPoint(double latitude, double longitude) {
    if (Double.isNaN(latitude) || latitude < -90 || latitude > 90) {
      throw new IllegalArgumentException("Latitude must be in the range of [-90, 90]");
    }
    if (Double.isNaN(longitude) || longitude < -180 || longitude > 180) {
      throw new IllegalArgumentException("Longitude must be in the range of [-180, 180]");
    }
    this.latitude = latitude;
    this.longitude = longitude;
  }

  /** @return The latitude value of this {@code GeoPoint}. */
  public double getLatitude() {
    return latitude;
  }

  /** @return The longitude value of this {@code GeoPoint}. */
  public double getLongitude() {
    return longitude;
  }

  @Override
  public int compareTo(@NonNull GeoPoint other) {
    int comparison = Util.compareDoubles(latitude, other.latitude);
    if (comparison == 0) {
      return Util.compareDoubles(longitude, other.longitude);
    } else {
      return comparison;
    }
  }

  @Override
  @NonNull
  public String toString() {
    return "GeoPoint { latitude=" + latitude + ", longitude=" + longitude + " }";
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (!(o instanceof GeoPoint)) {
      return false;
    }

    GeoPoint geoPoint = (GeoPoint) o;

    // We can do exact comparison here, because we only expect finite numbers for latitude and
    // longitude
    return latitude == geoPoint.latitude && longitude == geoPoint.longitude;
  }

  @Override
  public int hashCode() {
    int result;
    long temp;
    temp = Double.doubleToLongBits(latitude);
    result = (int) (temp ^ (temp >>> 32));
    temp = Double.doubleToLongBits(longitude);
    result = 31 * result + (int) (temp ^ (temp >>> 32));
    return result;
  }
}
