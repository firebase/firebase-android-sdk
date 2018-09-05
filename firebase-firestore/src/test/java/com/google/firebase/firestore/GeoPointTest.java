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

import static com.google.firebase.firestore.testutil.Assert.assertThrows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.google.firebase.firestore.testutil.ComparatorTester;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class GeoPointTest {

  private static GeoPoint gp(double latitude, double longitude) {
    return new GeoPoint(latitude, longitude);
  }

  @Test
  public void testEquals() {
    GeoPoint foo = gp(1.23, 4.56);
    GeoPoint fooDup = gp(1.23, 4.56);
    GeoPoint differentLatitude = gp(0, 4.56);
    GeoPoint differentLongitude = gp(1.23, 0);
    assertEquals(foo, fooDup);
    assertNotEquals(foo, differentLatitude);
    assertNotEquals(foo, differentLongitude);

    assertEquals(foo.hashCode(), fooDup.hashCode());
    assertNotEquals(foo.hashCode(), differentLatitude.hashCode());
    assertNotEquals(foo.hashCode(), differentLongitude.hashCode());
  }

  @Test
  public void testComparison() {
    new ComparatorTester()
        .addEqualityGroup(gp(-90, -180), gp(-90, -180))
        .addEqualityGroup(gp(-90, 0), gp(-90, 0))
        .addEqualityGroup(gp(-90, 180), gp(-90, 180))
        .addEqualityGroup(gp(-89, -180), gp(-89, -180))
        .addEqualityGroup(gp(-89, 0), gp(-89, 0))
        .addEqualityGroup(gp(-89, 180), gp(-89, 180))
        .addEqualityGroup(gp(0, -180), gp(0, -180))
        .addEqualityGroup(gp(0, 0), gp(0, 0))
        .addEqualityGroup(gp(0, 180), gp(0, 180))
        .addEqualityGroup(gp(89, -180), gp(89, -180))
        .addEqualityGroup(gp(89, 0), gp(89, 0))
        .addEqualityGroup(gp(89, 180), gp(89, 180))
        .addEqualityGroup(gp(90, -180), gp(90, -180))
        .addEqualityGroup(gp(90, 0), gp(90, 0))
        .addEqualityGroup(gp(90, 180), gp(90, 180))
        .testCompare();
  }

  @Test
  public void testThrows() {
    assertThrows(IllegalArgumentException.class, () -> new GeoPoint(Double.NaN, 0));
    assertThrows(IllegalArgumentException.class, () -> new GeoPoint(Double.NEGATIVE_INFINITY, 0));
    assertThrows(IllegalArgumentException.class, () -> new GeoPoint(Double.POSITIVE_INFINITY, 0));
    assertThrows(IllegalArgumentException.class, () -> new GeoPoint(-90.1, 0));
    assertThrows(IllegalArgumentException.class, () -> new GeoPoint(90.1, 0));
    assertThrows(IllegalArgumentException.class, () -> new GeoPoint(0, Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> new GeoPoint(0, Double.NEGATIVE_INFINITY));
    assertThrows(IllegalArgumentException.class, () -> new GeoPoint(0, Double.POSITIVE_INFINITY));
    assertThrows(IllegalArgumentException.class, () -> new GeoPoint(0, -180.1));
    assertThrows(IllegalArgumentException.class, () -> new GeoPoint(0, 180.1));
  }
}
