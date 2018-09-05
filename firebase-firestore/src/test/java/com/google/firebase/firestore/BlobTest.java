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

import static com.google.firebase.firestore.testutil.TestUtil.blob;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.google.firebase.firestore.testutil.ComparatorTester;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class BlobTest {

  @Test
  public void testEquals() {
    assertEquals(blob(1, 2, 3), blob(1, 2, 3));
    assertNotEquals(blob(1, 2, 3), blob(1, 2));
    assertNotEquals(blob(1, 2, 3), new Object());
  }

  @Test
  public void testComparison() {
    new ComparatorTester()
        .addEqualityGroup(blob(), blob())
        .addEqualityGroup(blob(0), blob(0))
        .addEqualityGroup(blob(0, 1), blob(0, 1))
        .addEqualityGroup(blob(0, 1, 0), blob(0, 1, 0))
        .addEqualityGroup(blob(0, 1, 1), blob(0, 1, 1))
        .addEqualityGroup(blob(0, 255), blob(0, 255))
        .addEqualityGroup(blob(1), blob(1))
        .addEqualityGroup(blob(1, 0), blob(1, 0))
        .addEqualityGroup(blob(1, 255), blob(1, 255))
        .addEqualityGroup(blob(255), blob(255))
        .testCompare();
  }

  @Test
  public void testMutableBytes() {
    byte[] myBytes = {1, 2, 3};
    Blob blob1 = Blob.fromBytes(myBytes);
    Blob blob2 = Blob.fromBytes(myBytes);
    myBytes[0] = 5;
    Blob blob3 = Blob.fromBytes(myBytes);
    assertEquals(blob1, blob2); // Equal because array didn't change
    assertNotEquals(blob1, blob3); // Not equal because array changed
  }
}
