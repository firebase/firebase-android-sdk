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

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.google.firebase.firestore.testutil.ComparatorTester;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class DocumentKeyTest {
  @Test
  public void testConstructor() {
    ResourcePath path = ResourcePath.fromSegments(asList("rooms", "firestore", "messages", "1"));
    DocumentKey key = DocumentKey.fromPath(path);
    assertEquals(path, key.getPath());
  }

  @Test
  public void testComparison() {
    DocumentKey key1 = DocumentKey.fromSegments(asList("a", "b", "c", "d"));
    DocumentKey key2 = DocumentKey.fromSegments(asList("a", "b", "c", "d"));
    DocumentKey key3 = DocumentKey.fromSegments(asList("x", "y", "z", "w"));

    assertEquals(key1, key2);
    assertNotEquals(key1, key3);

    DocumentKey empty = DocumentKey.fromSegments(Collections.emptyList());
    DocumentKey a = DocumentKey.fromSegments(asList("a", "a"));
    DocumentKey b = DocumentKey.fromSegments(asList("b", "b"));
    DocumentKey ab = DocumentKey.fromSegments(asList("a", "a", "b", "b"));

    new ComparatorTester()
        .addEqualityGroup(empty)
        .addEqualityGroup(a)
        .addEqualityGroup(ab)
        .addEqualityGroup(b)
        .testCompare();
  }

  @Test(expected = Throwable.class)
  public void testUnevenNumberOfSegmentsAreRejected() {
    DocumentKey.fromSegments(Collections.singletonList("a"));
  }
}
