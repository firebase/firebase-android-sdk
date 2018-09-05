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

/*
 * Copyright 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.database.collection;

import static net.java.quickcheck.generator.CombinedGeneratorsIterables.someMaps;
import static net.java.quickcheck.generator.PrimitiveGenerators.booleans;
import static net.java.quickcheck.generator.PrimitiveGenerators.fixedValues;
import static net.java.quickcheck.generator.PrimitiveGenerators.integers;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

public class ArraySortedMapTest {

  private static Comparator<Integer> IntComparator =
      StandardComparator.getComparator(Integer.class);

  private static Comparator<String> StringComparator =
      StandardComparator.getComparator(String.class);

  @Test
  public void basicImmutableSortedMapBuilding() {
    Map<String, Integer> data = new HashMap<>();
    data.put("a", 1);
    data.put("b", 2);
    data.put("c", 3);
    data.put("d", 4);
    data.put("e", 5);
    data.put("f", 6);
    data.put("g", 7);
    data.put("h", 8);
    data.put("i", 9);
    data.put("j", 10);

    ImmutableSortedMap<String, Integer> map = ArraySortedMap.fromMap(data, StringComparator);
    assertEquals(10, map.size());
  }

  @Test
  public void emptyMap() {
    Map<String, Integer> data = new HashMap<>();

    ImmutableSortedMap<String, Integer> map = ArraySortedMap.fromMap(data, StringComparator);

    assertEquals(0, map.size());
    assertEquals(true, map.isEmpty());
  }

  @Test
  public void almostEmptyMap() {
    Map<String, Integer> data = new HashMap<>();
    data.put("a", 1);
    data.put("b", null);

    ImmutableSortedMap<String, Integer> map = ArraySortedMap.fromMap(data, StringComparator);

    assertEquals(2, map.size());
    assertEquals(false, map.isEmpty());
  }

  @Test
  public void searchForASpecificKey() {
    ImmutableSortedMap<Integer, Integer> map =
        new ArraySortedMap<Integer, Integer>(IntComparator).insert(1, 1).insert(2, 2);

    assertEquals(1, (int) map.get(1));
    assertEquals(2, (int) map.get(2));
    assertNull(map.get(3));
  }

  @Test
  public void removeKeyValuePair() {
    ImmutableSortedMap<Integer, Integer> map =
        new ArraySortedMap<Integer, Integer>(IntComparator).insert(1, 1).insert(2, 2);

    map = map.remove(1);
    assertEquals(2, (int) map.get(2));
    assertNull(map.get(1));
  }

  @Test
  public void moreRemovals() {
    ImmutableSortedMap<Integer, Integer> map =
        new ArraySortedMap<Integer, Integer>(IntComparator)
            .insert(1, 1)
            .insert(50, 50)
            .insert(3, 3)
            .insert(4, 4)
            .insert(7, 7)
            .insert(9, 9)
            .insert(20, 20)
            .insert(18, 18)
            .insert(2, 2)
            .insert(71, 71)
            .insert(42, 42)
            .insert(88, 88);

    map = map.remove(7).remove(3).remove(1);
    assertNull(map.get(7));
    assertNull(map.get(3));
    assertNull(map.get(1));
    assertEquals(50, (int) map.get(50));
  }

  @Test
  public void canReplaceExistingItem() {
    ImmutableSortedMap<Integer, Integer> map = new ArraySortedMap<>(IntComparator);

    map = map.insert(1, 1).insert(1, 2);

    assertEquals(2, (int) map.get(1));
  }

  @Test
  public void replacesExistingKey() {
    ImmutableSortedMap<String, Integer> map = new ArraySortedMap<>(StringComparator);

    String keyOne = new String("1");
    String keyTwo = new String("2");
    map = map.insert(keyOne, 1).insert(keyTwo, 2);

    assertNotSame(keyOne, map.getMaxKey());
    assertSame(keyTwo, map.getMaxKey());
  }

  @Test
  public void replaceExactKeyYieldsSameMap() {
    ImmutableSortedMap<String, Integer> map = new ArraySortedMap<>(StringComparator);

    String key = "1";
    Integer value = 1;
    map = map.insert(key, value);
    Assert.assertSame(map, map.insert(key, value));
  }

  @Test
  public void removingNonExistentKeyYieldsSameMap() {
    ImmutableSortedMap<String, Integer> map = new ArraySortedMap<>(StringComparator);
    map = map.insert("key", 1);
    Assert.assertSame(map, map.remove("no-key"));
  }

  @Test
  public void predecessorKeyThrowsExceptionIfKeyIsNotPresent() {
    ImmutableSortedMap<String, Integer> map = new ArraySortedMap<>(StringComparator);
    map = map.insert("key", 1);
    Assert.assertEquals(null, map.getPredecessorKey("key"));
    try {
      map.getPredecessorKey("no-key");
      fail("Didn't throw exception");
    } catch (IllegalArgumentException e) { //
    }
  }

  // QuickCheck Tests

  @Test
  public void sizeIsCorrect() {
    for (Map<Integer, Integer> any : someMaps(integers(), integers())) {
      Assert.assertEquals(any.size(), ArraySortedMap.fromMap(any, IntComparator).size());
    }
  }

  @Test
  public void addWorks() {
    for (Map<Integer, Integer> any : someMaps(integers(), integers())) {
      ImmutableSortedMap<Integer, Integer> map = new ArraySortedMap<>(IntComparator);
      for (Map.Entry<Integer, Integer> entry : any.entrySet()) {
        map = map.insert(entry.getKey(), entry.getValue());
      }
      for (Map.Entry<Integer, Integer> entry : any.entrySet()) {
        Assert.assertEquals(entry.getValue(), map.get(entry.getKey()));
      }
    }
  }

  @Test
  public void removeWorks() {
    for (Map<Integer, Integer> any : someMaps(integers(), integers())) {
      ImmutableSortedMap<Integer, Integer> map = ArraySortedMap.fromMap(any, IntComparator);
      for (Map.Entry<Integer, Integer> entry : any.entrySet()) {
        map = map.remove(entry.getKey());
      }
      Assert.assertEquals(0, map.size());
    }
  }

  @Test
  public void iterationIsInOrder() {
    for (Map<Integer, Integer> any : someMaps(integers(), integers())) {
      List<Integer> expectedKeys = new ArrayList<>(any.keySet());
      Collections.sort(expectedKeys);

      ImmutableSortedMap<Integer, Integer> map = ArraySortedMap.fromMap(any, IntComparator);
      List<Integer> actualKeys = new ArrayList<>();
      for (Map.Entry<Integer, Integer> entry : map) {
        actualKeys.add(entry.getKey());
      }

      Assert.assertEquals(expectedKeys, actualKeys);
    }
  }

  @Test
  public void iterationFromKeyIsInOrder() {
    for (Map<Integer, Integer> any : someMaps(integers(), integers())) {
      List<Integer> expectedKeys = new ArrayList<>(any.keySet());
      Integer fromKey =
          (expectedKeys.isEmpty() || booleans().next()) ? integers().next() : expectedKeys.get(0);
      Collections.sort(expectedKeys);

      Iterator<Integer> iterator = expectedKeys.iterator();
      while (iterator.hasNext()) {
        Integer next = iterator.next();
        if (next.compareTo(fromKey) < 0) {
          iterator.remove();
        }
      }

      ImmutableSortedMap<Integer, Integer> map = ArraySortedMap.fromMap(any, IntComparator);
      List<Integer> actualKeys = new ArrayList<>();
      Iterator<Map.Entry<Integer, Integer>> mapIterator = map.iteratorFrom(fromKey);
      while (mapIterator.hasNext()) {
        actualKeys.add(mapIterator.next().getKey());
      }

      Assert.assertEquals(expectedKeys, actualKeys);
    }
  }

  @Test
  public void reverseIterationIsInOrder() {
    for (Map<Integer, Integer> any : someMaps(integers(), integers())) {
      List<Integer> expectedKeys = new ArrayList<>(any.keySet());
      Collections.sort(expectedKeys);
      Collections.reverse(expectedKeys);

      ImmutableSortedMap<Integer, Integer> map = ArraySortedMap.fromMap(any, IntComparator);
      List<Integer> actualKeys = new ArrayList<>();
      Iterator<Map.Entry<Integer, Integer>> iterator = map.reverseIterator();
      while (iterator.hasNext()) {
        actualKeys.add(iterator.next().getKey());
      }

      Assert.assertEquals(expectedKeys, actualKeys);
    }
  }

  @Test
  public void reverseIterationFromKeyIsInOrder() {
    for (Map<Integer, Integer> any : someMaps(integers(), integers())) {
      List<Integer> expectedKeys = new ArrayList<>(any.keySet());
      Integer fromKey =
          (expectedKeys.isEmpty() || booleans().next()) ? integers().next() : expectedKeys.get(0);
      Collections.sort(expectedKeys);
      Collections.reverse(expectedKeys);

      Iterator<Integer> iterator = expectedKeys.iterator();
      while (iterator.hasNext()) {
        Integer next = iterator.next();
        if (next.compareTo(fromKey) > 0) {
          iterator.remove();
        }
      }

      ImmutableSortedMap<Integer, Integer> map = ArraySortedMap.fromMap(any, IntComparator);
      List<Integer> actualKeys = new ArrayList<>();
      Iterator<Map.Entry<Integer, Integer>> mapIterator = map.reverseIteratorFrom(fromKey);
      while (mapIterator.hasNext()) {
        actualKeys.add(mapIterator.next().getKey());
      }

      Assert.assertEquals(expectedKeys, actualKeys);
    }
  }

  @Test
  public void predecessorKeyIsCorrect() {
    for (Map<Integer, Integer> any : someMaps(integers(), integers())) {
      ImmutableSortedMap<Integer, Integer> map = ArraySortedMap.fromMap(any, IntComparator);
      Integer predecessorKey = null;
      for (Map.Entry<Integer, Integer> entry : map) {
        Assert.assertEquals(predecessorKey, map.getPredecessorKey(entry.getKey()));
        predecessorKey = entry.getKey();
      }
    }
  }

  @Test
  public void successorKeyIsCorrect() {
    for (Map<Integer, Integer> any : someMaps(integers(), integers())) {
      ImmutableSortedMap<Integer, Integer> map = ArraySortedMap.fromMap(any, IntComparator);
      Integer lastKey = null;
      for (Map.Entry<Integer, Integer> entry : map) {
        if (lastKey != null) {
          Assert.assertEquals(entry.getKey(), map.getSuccessorKey(lastKey));
        }
        lastKey = entry.getKey();
      }
      if (lastKey != null) {
        Assert.assertEquals(null, map.getSuccessorKey(lastKey));
      }
    }
  }

  @Test
  public void addAboveLimitYieldsRBTree() {
    Map<Integer, Integer> any =
        someMaps(integers(), integers(), fixedValues(100)).iterator().next();
    ImmutableSortedMap<Integer, Integer> map = new ArraySortedMap<>(IntComparator);
    for (Map.Entry<Integer, Integer> entry : any.entrySet()) {
      map = map.insert(entry.getKey(), entry.getValue());
    }
    Assert.assertEquals(RBTreeSortedMap.class, map.getClass());
    for (Map.Entry<Integer, Integer> entry : any.entrySet()) {
      Assert.assertEquals(entry.getValue(), map.get(entry.getKey()));
    }
  }

  @Test
  public void equalsIsCorrect() {
    ImmutableSortedMap<Integer, Integer> map;
    ImmutableSortedMap<Integer, Integer> copy;
    ImmutableSortedMap<Integer, Integer> rbcopy;
    ImmutableSortedMap<Integer, Integer> copyWithDifferentComparator;
    map = new ArraySortedMap<>(IntComparator);
    copy = new ArraySortedMap<>(IntComparator);
    rbcopy = new RBTreeSortedMap<>(IntComparator);
    copyWithDifferentComparator =
        new ArraySortedMap<>(
            new Comparator<Integer>() {
              @Override
              public int compare(Integer o1, Integer o2) {
                return o1.compareTo(o2);
              }
            });

    int size = ArraySortedMap.Builder.ARRAY_TO_RB_TREE_SIZE_THRESHOLD - 1;
    Map<Integer, Integer> any =
        someMaps(integers(), integers(), fixedValues(size)).iterator().next();
    for (Map.Entry<Integer, Integer> entry : any.entrySet()) {
      Integer key = entry.getKey();
      Integer value = entry.getValue();
      map = map.insert(key, value);
      copy = copy.insert(key, value);
      rbcopy = rbcopy.insert(key, value);
      copyWithDifferentComparator = copyWithDifferentComparator.insert(key, value);
    }
    Assert.assertTrue(map.equals(copy));
    Assert.assertTrue(map.equals(rbcopy));
    Assert.assertTrue(rbcopy.equals(map));

    Assert.assertFalse(map.equals(copyWithDifferentComparator));
    Assert.assertFalse(map.equals(copy.remove(copy.getMaxKey())));
    Assert.assertFalse(map.equals(copy.insert(copy.getMaxKey() + 1, 1)));
    Assert.assertFalse(map.equals(rbcopy.remove(rbcopy.getMaxKey())));
  }

  // @Test
  public void perf() {
    ImmutableSortedMap<Integer, Integer> map = new ArraySortedMap<>(IntComparator);

    for (int j = 0; j < 5; j++) {
      final long startTime = System.currentTimeMillis();
      for (int i = 0; i < 50000; i++) {
        map = map.insert(i, i);
      }

      for (int i = 0; i < 50000; i++) {
        map = map.remove(i);
      }
      System.out.println("Elapsed: " + (System.currentTimeMillis() - startTime));
    }
  }
}
