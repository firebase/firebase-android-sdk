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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

public class RBTreeSortedMapTest {

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

    ImmutableSortedMap<String, Integer> map = RBTreeSortedMap.fromMap(data, StringComparator);
    assertEquals(10, map.size());
  }

  @Test
  public void emptyMap() {
    Map<String, Integer> data = new HashMap<>();

    ImmutableSortedMap<String, Integer> map = RBTreeSortedMap.fromMap(data, StringComparator);

    assertEquals(0, map.size());
  }

  @Test
  public void almostEmptyMap() {
    Map<String, Integer> data = new HashMap<>();
    data.put("a", 1);
    data.put("b", null);

    ImmutableSortedMap<String, Integer> map = RBTreeSortedMap.fromMap(data, StringComparator);

    assertEquals(2, map.size());
  }

  @Test
  public void createNode() {
    ImmutableSortedMap<String, Integer> map = new RBTreeSortedMap<>(StringComparator);
    map = map.insert("a", 1);

    RBTreeSortedMap<String, Integer> rbMap = (RBTreeSortedMap<String, Integer>) map;

    assertTrue(rbMap.getRoot().getLeft().isEmpty());
    assertTrue(rbMap.getRoot().getRight().isEmpty());
  }

  @Test
  public void searchForASpecificKey() {
    ImmutableSortedMap<Integer, Integer> map =
        new RBTreeSortedMap<Integer, Integer>(IntComparator).insert(1, 1).insert(2, 2);

    assertEquals(1, (int) map.get(1));
    assertEquals(2, (int) map.get(2));
    assertNull(map.get(3));
  }

  @Test
  public void canInsertNewPairs() {
    ImmutableSortedMap<Integer, Integer> map =
        new RBTreeSortedMap<Integer, Integer>(IntComparator).insert(1, 1).insert(2, 2);

    RBTreeSortedMap<Integer, Integer> rbMap = (RBTreeSortedMap<Integer, Integer>) map;

    assertEquals(2, (int) rbMap.getRoot().getKey());
    assertEquals(1, (int) rbMap.getRoot().getLeft().getKey());
  }

  @Test
  public void removeKeyValuePair() {
    ImmutableSortedMap<Integer, Integer> map =
        new RBTreeSortedMap<Integer, Integer>(IntComparator).insert(1, 1).insert(2, 2);

    map = map.remove(1);
    assertEquals(2, (int) map.get(2));
    assertNull(map.get(1));
  }

  @Test
  public void moreRemovals() {
    ImmutableSortedMap<Integer, Integer> map =
        new RBTreeSortedMap<Integer, Integer>(IntComparator)
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

  // QuickCheck tests

  @Test
  public void iterationIsInOrder() {
    for (Map<Integer, Integer> any : someMaps(integers(), integers())) {
      List<Integer> expectedKeys = new ArrayList<>(any.keySet());
      Collections.sort(expectedKeys);

      ImmutableSortedMap<Integer, Integer> map = RBTreeSortedMap.fromMap(any, IntComparator);
      List<Integer> actualKeys = new ArrayList<>();
      for (Map.Entry<Integer, Integer> entry : map) {
        actualKeys.add(entry.getKey());
      }

      assertEquals(expectedKeys, actualKeys);
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

      ImmutableSortedMap<Integer, Integer> map = RBTreeSortedMap.fromMap(any, IntComparator);
      List<Integer> actualKeys = new ArrayList<>();
      Iterator<Map.Entry<Integer, Integer>> mapIterator = map.iteratorFrom(fromKey);
      while (mapIterator.hasNext()) {
        actualKeys.add(mapIterator.next().getKey());
      }

      assertEquals(expectedKeys, actualKeys);
    }
  }

  @Test
  public void reverseIterationIsInOrder() {
    for (Map<Integer, Integer> any : someMaps(integers(), integers())) {
      List<Integer> expectedKeys = new ArrayList<>(any.keySet());
      Collections.sort(expectedKeys);
      Collections.reverse(expectedKeys);

      ImmutableSortedMap<Integer, Integer> map = RBTreeSortedMap.fromMap(any, IntComparator);
      List<Integer> actualKeys = new ArrayList<>();
      Iterator<Map.Entry<Integer, Integer>> iterator = map.reverseIterator();
      while (iterator.hasNext()) {
        actualKeys.add(iterator.next().getKey());
      }

      assertEquals(expectedKeys, actualKeys);
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

      ImmutableSortedMap<Integer, Integer> map = RBTreeSortedMap.fromMap(any, IntComparator);
      List<Integer> actualKeys = new ArrayList<>();
      Iterator<Map.Entry<Integer, Integer>> mapIterator = map.reverseIteratorFrom(fromKey);
      while (mapIterator.hasNext()) {
        actualKeys.add(mapIterator.next().getKey());
      }

      assertEquals(expectedKeys, actualKeys);
    }
  }

  @Test
  public void predecessorKeyIsCorrect() {
    for (Map<Integer, Integer> any : someMaps(integers(), integers())) {
      ImmutableSortedMap<Integer, Integer> map = RBTreeSortedMap.fromMap(any, IntComparator);
      Integer predecessorKey = null;
      for (Map.Entry<Integer, Integer> entry : map) {
        assertEquals(predecessorKey, map.getPredecessorKey(entry.getKey()));
        predecessorKey = entry.getKey();
      }
    }
  }

  @Test
  public void successorKeyIsCorrect() {
    for (Map<Integer, Integer> any : someMaps(integers(), integers())) {
      ImmutableSortedMap<Integer, Integer> map = RBTreeSortedMap.fromMap(any, IntComparator);
      Integer lastKey = null;
      for (Map.Entry<Integer, Integer> entry : map) {
        if (lastKey != null) {
          assertEquals(entry.getKey(), map.getSuccessorKey(lastKey));
        }
        lastKey = entry.getKey();
      }
      if (lastKey != null) {
        assertEquals(null, map.getSuccessorKey(lastKey));
      }
    }
  }

  @Test
  public void equalsIsCorrect() {
    ImmutableSortedMap<Integer, Integer> map;
    ImmutableSortedMap<Integer, Integer> copy;
    ImmutableSortedMap<Integer, Integer> arraycopy;
    ImmutableSortedMap<Integer, Integer> copyWithDifferentComparator;
    map = new RBTreeSortedMap<>(IntComparator);
    copy = new RBTreeSortedMap<>(IntComparator);
    arraycopy = new ArraySortedMap<>(IntComparator);
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
      arraycopy = arraycopy.insert(key, value);
      copyWithDifferentComparator = copyWithDifferentComparator.insert(key, value);
    }
    Assert.assertTrue(map.equals(copy));
    Assert.assertTrue(map.equals(arraycopy));
    Assert.assertTrue(arraycopy.equals(map));

    Assert.assertFalse(map.equals(copyWithDifferentComparator));
    Assert.assertFalse(map.equals(copy.remove(copy.getMaxKey())));
    Assert.assertFalse(map.equals(copy.insert(copy.getMaxKey() + 1, 1)));
    Assert.assertFalse(map.equals(arraycopy.remove(arraycopy.getMaxKey())));
  }
}
