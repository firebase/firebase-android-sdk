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

import static com.google.firebase.firestore.testutil.TestUtil.map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class DocumentSnapshotTest {

  @Test
  public void testEquals() {
    DocumentSnapshot base = TestUtil.documentSnapshot("rooms/foo", map("a", 1), false);
    DocumentSnapshot baseDup = TestUtil.documentSnapshot("rooms/foo", map("a", 1), false);
    DocumentSnapshot noData = TestUtil.documentSnapshot("rooms/foo", null, false);
    DocumentSnapshot noDataDup = TestUtil.documentSnapshot("rooms/foo", null, false);
    DocumentSnapshot differentPath = TestUtil.documentSnapshot("rooms/bar", map("a", 1), false);
    DocumentSnapshot differentData = TestUtil.documentSnapshot("rooms/foo", map("b", 1), false);
    DocumentSnapshot fromCache = TestUtil.documentSnapshot("rooms/foo", map("a", 1), true);

    assertEquals(base, baseDup);
    assertEquals(noData, noDataDup);
    assertNotEquals(base, noData);
    assertNotEquals(noData, base);
    assertNotEquals(base, differentPath);
    assertNotEquals(base, differentData);
    assertNotEquals(base, fromCache);

    // The assertions below that hash codes of different values are not equal is not something that
    // we guarantee. In particular `base` and `differentData` have a hash collision because we
    // don't use data in the hashCode.
    assertEquals(base.hashCode(), baseDup.hashCode());
    assertEquals(noData.hashCode(), noDataDup.hashCode());
    assertNotEquals(base.hashCode(), noData.hashCode());
    assertNotEquals(noData.hashCode(), base.hashCode());
    assertNotEquals(base.hashCode(), differentPath.hashCode());
    assertNotEquals(base.hashCode(), fromCache.hashCode());
  }
}
