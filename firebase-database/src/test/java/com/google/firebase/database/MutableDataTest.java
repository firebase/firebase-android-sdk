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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.firebase.database.core.Path;
import com.google.firebase.database.snapshot.Node;
import com.google.firebase.database.snapshot.NodeUtilities;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@org.junit.runner.RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class MutableDataTest {

  private MutableData dataFor(Object data) {
    Node node = NodeUtilities.NodeFromJSON(data);
    return new MutableData(node);
  }

  private MutableData dataForPath(Object data, String path) {
    Node node = NodeUtilities.NodeFromJSON(null);
    MutableData root = new MutableData(node);
    MutableData result = root.child(path.toString());
    result.setValue(data);
    return result;
  }

  @Test
  public void basicIterationWorks() {
    MutableData snap1 = dataFor(null);

    assertFalse(snap1.hasChildren());
    assertFalse(snap1.getChildren().iterator().hasNext());

    MutableData snap2 = dataFor(1L);
    assertFalse(snap2.hasChildren());
    assertFalse(snap2.getChildren().iterator().hasNext());

    MutableData snap3 = dataFor(new MapBuilder().put("a", 1L).put("b", 2L).build());
    assertTrue(snap3.hasChildren());
    Iterator<MutableData> iter = snap3.getChildren().iterator();
    assertTrue(iter.hasNext());

    String[] children = new String[] {null, null};
    int i = 0;
    for (MutableData child : snap3.getChildren()) {
      children[i] = child.getKey();
      i++;
    }
    assertArrayEquals(children, new String[] {"a", "b"});
  }

  @Test
  public void iterationWorksAlphaPriorities() {
    Map<String, Object> obj =
        new MapBuilder()
            .put("a", new MapBuilder().put(".value", 1).put(".priority", "first").build())
            .put("z", new MapBuilder().put(".value", 26).put(".priority", "second").build())
            .put("m", new MapBuilder().put(".value", 13).put(".priority", "third").build())
            .put("n", new MapBuilder().put(".value", 14).put(".priority", "fourth").build())
            .put("c", new MapBuilder().put(".value", 3).put(".priority", "fifth").build())
            .put("b", new MapBuilder().put(".value", 2).put(".priority", "sixth").build())
            .put("e", new MapBuilder().put(".value", 5).put(".priority", "seventh").build())
            .build();

    MutableData data = dataFor(obj);
    List<String> names = new ArrayList<String>();
    List<Long> values = new ArrayList<Long>();
    List<String> priorities = new ArrayList<String>();
    for (MutableData child : data.getChildren()) {
      names.add(child.getKey());
      values.add((Long) child.getValue());
      priorities.add((String) child.getPriority());
    }

    List<String> expectedNames = new ArrayList<String>();
    expectedNames.addAll(Arrays.asList("c", "a", "n", "z", "e", "b", "m"));
    List<Long> expectedValues = new ArrayList<Long>();
    expectedValues.addAll(Arrays.asList(3L, 1L, 14L, 26L, 5L, 2L, 13L));
    List<String> expectedPriorities = new ArrayList<String>();
    expectedPriorities.addAll(
        Arrays.asList("fifth", "first", "fourth", "second", "seventh", "sixth", "third"));

    DeepEquals.assertEquals(expectedNames, names);
    DeepEquals.assertEquals(expectedValues, values);
    DeepEquals.assertEquals(expectedPriorities, priorities);
  }

  @Test
  public void writingData() {
    MutableData data = dataFor(new HashMap<String, Object>());
    data.setValue(new MapBuilder().put("a", 1).put("b", 2).build());

    assertTrue(data.hasChildren());
    assertEquals(2, data.getChildrenCount());
    assertTrue(data.hasChild("a"));

    MutableData childData = data.child("b");
    assertEquals(2L, childData.getValue());
    childData.setValue(3);

    Map<String, Object> expected = new MapBuilder().put("a", 1L).put("b", 3L).build();
    DeepEquals.assertEquals(expected, data.getValue());

    int count = 0;
    for (MutableData unused : data.getChildren()) {
      count++;
      if (count == 1) {
        data.child("c").setValue(4);
      }
    }

    // Should not iterate children added in a loop
    assertEquals(2, count);
    assertEquals(3, data.getChildrenCount());
    assertEquals(4L, data.child("c").getValue());
  }

  @Test
  public void mutableDataNavigation() {
    MutableData data = dataFor(new MapBuilder().put("a", 1).put("b", 2).build());
    assertNull(data.getKey());

    MutableData childData = data.child("b");
    assertEquals("b", childData.getKey());

    childData = data.child("c");
    assertEquals("c", childData.getKey());
    assertNull(childData.getValue());
    childData.setValue(new MapBuilder().put("d", 4).build());

    Map<String, Object> expected =
        new MapBuilder()
            .put("a", 1L)
            .put("b", 2L)
            .put("c", new MapBuilder().put("d", 4L).build())
            .build();
    DeepEquals.assertEquals(expected, data.getValue());
  }

  @Test
  public void handlesPriorities() {
    MutableData data = dataFor(new MapBuilder().put("a", 1).put("b", 2).build());

    assertNull(data.getPriority());
    data.setPriority("foo");
    assertEquals("foo", data.getPriority());
    data.setValue(3);
    assertNull(data.getPriority());
    data.setPriority(4);
    data.setValue(null);
    assertNull(data.getPriority());
  }

  @Test
  public void validatesPaths() {
    final int maxPathLengthBytes = 768;
    final int maxPathDepth = 32;
    final String fire = new String(Character.toChars(128293));
    final String base = new String(Character.toChars(26594));

    List<String> goodKeys =
        Arrays.asList(
            UnitTestHelpers.repeatedString("k", maxPathLengthBytes - 1),
            UnitTestHelpers.repeatedString(fire, maxPathLengthBytes / 4 - 1),
            UnitTestHelpers.repeatedString(base, maxPathLengthBytes / 3 - 1),
            UnitTestHelpers.repeatedString("key/", maxPathDepth - 1) + "key");

    List<String> badKeys =
        Arrays.asList(
            UnitTestHelpers.repeatedString("k", maxPathLengthBytes),
            UnitTestHelpers.repeatedString(fire, maxPathLengthBytes / 4),
            UnitTestHelpers.repeatedString(base, maxPathLengthBytes / 3),
            UnitTestHelpers.repeatedString("key/", maxPathDepth) + "key");

    // Large but legal paths should all work for manipulating MutableData.
    for (String key : goodKeys) {
      Path path = new Path(key);
      MutableData data = dataFor(UnitTestHelpers.buildObjFromPath(path, "test_value"));
      assertEquals("test_value", UnitTestHelpers.applyPath(data.getValue(), path));

      data = dataForPath("scalar_value", key);
      assertEquals("scalar_value", data.getValue());
    }

    for (String key : badKeys) {
      Path path = new Path(key);
      try {
        dataFor(UnitTestHelpers.buildObjFromPath(path, "test_value"));
        fail("Invalid path did not throw exception.");
      } catch (DatabaseException e) {
        // expected
      }

      try {
        dataForPath("scalar_value", key);
        fail("Invalid path did not throw exception.");
      } catch (DatabaseException e) {
        // expected
      }
    }
  }
}
