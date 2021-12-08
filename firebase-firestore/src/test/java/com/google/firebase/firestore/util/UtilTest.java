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

package com.google.firebase.firestore.util;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.firestore.util.Util.trimMap;
import static org.junit.Assert.assertEquals;

import com.google.firebase.firestore.testutil.TestUtil;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class UtilTest {

  @Test
  public void testToDebugString() {
    assertEquals("", Util.toDebugString(ByteString.EMPTY));
    assertEquals("00ff", Util.toDebugString(TestUtil.byteString(0, 0xFF)));
    assertEquals("1f3b", Util.toDebugString(TestUtil.byteString(0x1F, 0x3B)));
    assertEquals(
        "000102030405060708090a0b0c0d0e0f",
        Util.toDebugString(
            TestUtil.byteString(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0xA, 0xB, 0xC, 0xD, 0xE, 0xF)));
  }

  @Test
  public void testDiffCollectionsWithMissingElement() {
    List<String> before = Arrays.asList("a", "b", "c");
    List<String> after = Arrays.asList("a", "b");
    validateDiffCollection(before, after);
  }

  @Test
  public void testDiffCollectionsWithAddedElement() {
    List<String> before = Arrays.asList("a", "b");
    List<String> after = Arrays.asList("a", "b", "c");
    validateDiffCollection(before, after);
  }

  @Test
  public void testDiffCollectionsWithoutOrdering() {
    List<String> before = Arrays.asList("b", "a");
    List<String> after = Arrays.asList("a", "b");
    validateDiffCollection(before, after);
  }

  @Test
  public void testDiffCollectionsWithEmptyLists() {
    validateDiffCollection(Collections.singletonList("a"), Collections.emptyList());
    validateDiffCollection(Collections.emptyList(), Collections.singletonList("a"));
    validateDiffCollection(Collections.emptyList(), Collections.emptyList());
  }


  @Test
  public void testTrimMap() {
    Map<Integer, Integer> data = new HashMap<>();
    data.put(1, 1);
    data.put(3, 3);
    data.put(2, 2);
    data = trimMap(data, 2, Integer::compare);
    assertThat(data).containsExactly(1, 1, 2,2);
  }

  private void validateDiffCollection(List<String> before, List<String> after) {
    List<String> result = new ArrayList<>(before);
    Util.diffCollections(before, after, String::compareTo, result::add, result::remove);
    assertThat(result).containsExactlyElementsIn(after);
  }
}
