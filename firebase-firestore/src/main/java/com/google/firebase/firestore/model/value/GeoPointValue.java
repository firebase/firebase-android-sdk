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
import com.google.firestore.v1.Value;
import com.google.type.LatLng;

/** A wrapper for geo point values in Firestore. */
public class GeoPointValue extends FieldValue {
  GeoPointValue(Value value) {
    super(value);
  }

  public static GeoPointValue valueOf(GeoPoint geoPoint) {
    return new GeoPointValue(
        Value.newBuilder()
            .setGeoPointValue(
                LatLng.newBuilder()
                    .setLatitude(geoPoint.getLatitude())
                    .setLongitude(geoPoint.getLongitude()))
            .build());
  }
}
