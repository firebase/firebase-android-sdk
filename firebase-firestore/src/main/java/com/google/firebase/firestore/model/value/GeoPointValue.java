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

package com.google.firebase.firestore.model.value;

import com.google.firebase.firestore.GeoPoint;

/** A wrapper for geo point values in Firestore. */
public class GeoPointValue extends FieldValue {
  private final GeoPoint internalValue;

  private GeoPointValue(GeoPoint geoPoint) {
    super();
    internalValue = geoPoint;
  }

  @Override
  public int typeOrder() {
    return TYPE_ORDER_GEOPOINT;
  }

  @Override
  public GeoPoint value() {
    return internalValue;
  }

  @Override
  public boolean equals(Object o) {
    return (o instanceof GeoPointValue) && internalValue.equals(((GeoPointValue) o).internalValue);
  }

  @Override
  public int hashCode() {
    return internalValue.hashCode();
  }

  @Override
  public int compareTo(FieldValue o) {
    if (o instanceof GeoPointValue) {
      return internalValue.compareTo(((GeoPointValue) o).internalValue);
    } else {
      return defaultCompareTo(o);
    }
  }

  public static GeoPointValue valueOf(GeoPoint geoPoint) {
    return new GeoPointValue(geoPoint);
  }
}
