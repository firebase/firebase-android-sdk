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

package com.google.firebase.firestore.model;

import static com.google.firebase.firestore.testutil.TestUtil.field;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.google.firebase.firestore.testutil.ComparatorTester;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class FieldPathTest {
  @Test
  public void testConstructor() {
    FieldPath path = field("rooms.Eros.messages");
    assertEquals(3, path.length());
  }

  @Test
  public void testIndexing() {
    FieldPath path = field("rooms.Eros.messages");
    assertEquals("rooms", path.getFirstSegment());
    assertEquals("rooms", path.getSegment(0));

    assertEquals("Eros", path.getSegment(1));

    assertEquals("messages", path.getLastSegment());
    assertEquals("messages", path.getSegment(2));
  }

  @Test
  public void testWithoutFirstSegment() {
    FieldPath path = field("rooms.Eros.messages");
    FieldPath same = field("rooms.Eros.messages");
    FieldPath second = field("Eros.messages");
    FieldPath third = field("messages");
    FieldPath empty = FieldPath.EMPTY_PATH;

    assertEquals(second, path.popFirst());
    assertEquals(third, path.popFirst().popFirst());
    assertEquals(empty, path.popFirst().popFirst().popFirst());
    assertEquals(path, same);
  }

  @Test
  public void testWithoutLastSegment() {
    FieldPath path = field("rooms.Eros.messages");
    FieldPath same = field("rooms.Eros.messages");
    FieldPath second = field("rooms.Eros");
    FieldPath third = field("rooms");
    FieldPath empty = FieldPath.EMPTY_PATH;

    assertEquals(second, path.popLast());
    assertEquals(third, path.popLast().popLast());
    assertEquals(empty, path.popLast().popLast().popLast());
    assertEquals(path, same);
  }

  @Test
  public void testAppend() {
    FieldPath path = field("rooms");
    FieldPath rooms = field("rooms");
    FieldPath roomsEros = field("rooms.Eros");
    FieldPath roomsEros1 = field("rooms.Eros.1");

    assertEquals(roomsEros, path.append("Eros"));
    assertEquals(roomsEros1, path.append("Eros").append("1"));
    assertEquals(rooms, path);

    FieldPath sub = field("rooms.eros.1").popLast();
    FieldPath appended = sub.append("2");
    assertEquals(appended, field("rooms.eros.2"));
  }

  @Test
  public void testPathComparison() {
    FieldPath path1 = field("a.b.c");
    FieldPath path2 = field("a.b.c");
    FieldPath path3 = field("x.y.z");
    assertEquals(path1, path2);
    assertNotEquals(path2, path3);

    FieldPath empty = FieldPath.EMPTY_PATH;
    FieldPath a = field("a");
    FieldPath b = field("b");
    FieldPath ab = field("a.b");
    new ComparatorTester()
        .addEqualityGroup(empty)
        .addEqualityGroup(a)
        .addEqualityGroup(ab)
        .addEqualityGroup(b)
        .testCompare();
  }

  @Test
  public void testIsPrefixOf() {
    FieldPath empty = FieldPath.EMPTY_PATH;
    FieldPath a = field("a");
    FieldPath ab = field("a.b");
    FieldPath abc = field("a.b.c");
    FieldPath b = field("b");
    FieldPath ba = field("b.a");

    assertTrue(empty.isPrefixOf(a));
    assertTrue(empty.isPrefixOf(ab));
    assertTrue(empty.isPrefixOf(abc));
    assertTrue(empty.isPrefixOf(empty));
    assertTrue(empty.isPrefixOf(b));
    assertTrue(empty.isPrefixOf(ba));

    assertTrue(a.isPrefixOf(a));
    assertTrue(a.isPrefixOf(ab));
    assertTrue(a.isPrefixOf(abc));
    assertFalse(a.isPrefixOf(empty));
    assertFalse(a.isPrefixOf(b));
    assertFalse(a.isPrefixOf(ba));

    assertFalse(ab.isPrefixOf(a));
    assertTrue(ab.isPrefixOf(ab));
    assertTrue(ab.isPrefixOf(abc));
    assertFalse(ab.isPrefixOf(empty));
    assertFalse(ab.isPrefixOf(b));
    assertFalse(ab.isPrefixOf(ba));

    assertFalse(abc.isPrefixOf(a));
    assertFalse(abc.isPrefixOf(ab));
    assertTrue(abc.isPrefixOf(abc));
    assertFalse(abc.isPrefixOf(empty));
    assertFalse(abc.isPrefixOf(b));
    assertFalse(abc.isPrefixOf(ba));
  }

  void assertRoundTrip(String input, int numElements) {
    FieldPath path = FieldPath.fromServerFormat(input);
    assertEquals(numElements, path.length());
    assertEquals(input, path.toString());
  }

  @Test
  public void testServerFormat() {
    assertRoundTrip("foo", 1);
    assertRoundTrip("foo.bar", 2);
    assertRoundTrip("foo.bar.baz", 3);
    assertRoundTrip("`.foo\\\\`.`.foo`", 2);
    assertRoundTrip("foo.`\\``.bar", 3);
  }

  @Test
  public void testCanonicalStringOfSubstring() {
    FieldPath path = field("foo.bar.baz");
    assertEquals("foo.bar.baz", path.toString());

    assertEquals("bar.baz", path.popFirst().toString());
    assertEquals("foo.bar", path.popLast().toString());

    assertEquals("bar", path.popFirst().popLast().toString());
    assertEquals("bar", path.popLast().popFirst().toString());
  }
}
