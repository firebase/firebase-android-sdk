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

package com.google.firebase.database;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.firebase.database.snapshot.IndexedNode;
import com.google.firebase.database.snapshot.Node;
import com.google.firebase.database.snapshot.NodeUtilities;
import java.util.HashMap;
import java.util.Iterator;
import org.junit.Before;
import org.junit.Test;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@org.junit.runner.RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class DataSnapshotTest {

  @Before
  public void setUp() {
    UnitTestHelpers.ensureAppInitialized();
  }

  private DataSnapshot snapFor(Object data, DatabaseReference ref) {
    Node node = NodeUtilities.NodeFromJSON(data);
    return new DataSnapshot(ref, IndexedNode.from(node));
  }

  @Test
  public void basicIterationWorks() {
    DatabaseReference ref = UnitTestHelpers.getRandomNode();
    DataSnapshot snap1 = snapFor(null, ref);

    assertFalse(snap1.hasChildren());
    assertFalse(snap1.getChildren().iterator().hasNext());

    DataSnapshot snap2 = snapFor(1L, ref);
    assertFalse(snap2.hasChildren());
    assertFalse(snap2.getChildren().iterator().hasNext());

    DataSnapshot snap3 = snapFor(new MapBuilder().put("a", 1L).put("b", 2L).build(), ref);
    assertTrue(snap3.hasChildren());
    Iterator<DataSnapshot> iter = snap3.getChildren().iterator();
    assertTrue(iter.hasNext());

    String[] children = new String[] {null, null};
    int i = 0;
    for (DataSnapshot child : snap3.getChildren()) {
      children[i] = child.getKey();
      i++;
    }
    assertArrayEquals(children, new String[] {"a", "b"});
  }

  @Test
  @SuppressWarnings("rawtypes")
  public void existsWorks() {
    DatabaseReference ref = UnitTestHelpers.getRandomNode();
    DataSnapshot snap;

    snap = snapFor(new HashMap(), ref);
    assertFalse(snap.exists());

    snap = snapFor(new MapBuilder().put(".priority", 1).build(), ref);
    assertFalse(snap.exists());

    snap = snapFor(null, ref);
    assertFalse(snap.exists());

    snap = snapFor(true, ref);
    assertTrue(snap.exists());

    snap = snapFor(5, ref);
    assertTrue(snap.exists());

    snap = snapFor(new MapBuilder().put("x", 5).build(), ref);
    assertTrue(snap.exists());
  }
}
