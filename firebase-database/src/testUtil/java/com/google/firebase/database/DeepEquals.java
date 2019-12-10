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

// Pulled from
// https://code.google.com/p/deep-equals/source/browse/deep-equals/java/com/cedarsoftware/util/DeepEquals.java?r=5

import static org.junit.Assert.fail;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deeply compare two (2) objects. This method will call any overridden equals() methods if they
 * exist. If not, it will then proceed to do a field-by-field comparison, and when a non-primitive
 * field is encountered, recursively continue the deep comparison. When an array is found, it will
 * also ensure that the array contents are deeply equal, not requiring the array instance
 * (container) to be identical. This method will successfully compare object graphs that have cycles
 * (A->B->C->A). There is no need to ever use the Arrays.deepEquals() method as this is a true and
 * more effective super set.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class DeepEquals {
  private static final Map<Class, Boolean> _customEquals = new ConcurrentHashMap<Class, Boolean>();
  private static final Map<Class, Boolean> _customHash = new ConcurrentHashMap<Class, Boolean>();
  private static final Map<Class, Collection<Field>> _reflectedFields =
      new ConcurrentHashMap<Class, Collection<Field>>();

  private static class DualKey {
    private Object key1;
    private Object key2;

    private DualKey() {}

    private DualKey(Object k1, Object k2) {
      key1 = k1;
      key2 = k2;
    }

    @Override
    public boolean equals(Object other) {
      if (other == null) {
        return false;
      }

      if (!(other instanceof DualKey)) {
        return false;
      }

      DualKey that = (DualKey) other;
      return key1 == that.key1 && key2 == that.key2;
    }

    @Override
    public int hashCode() {
      int h1 = key1 != null ? key1.hashCode() : 0;
      int h2 = key2 != null ? key2.hashCode() : 0;
      return h1 + h2;
    }
  }

  public static boolean deepEquals(Object a, Object b) {
    Set visited = new HashSet<DualKey>();
    return deepEquals(a, b, visited);
  }

  public static void assertEquals(Object a, Object b) {
    if (!deepEquals(a, b)) {
      fail("Values different.\nExpected: " + a + "\nActual: " + b);
    }
  }

  private static boolean weakTypeMatch(Object a, Object b) {
    return (a instanceof Map && b instanceof Map) || (a instanceof List && b instanceof List);
  }

  private static boolean deepEquals(Object a, Object b, Set visited) {
    Deque<DualKey> stack = new ArrayDeque<>();
    stack.addFirst(new DualKey(a, b));

    while (!stack.isEmpty()) {
      DualKey dualKey = stack.removeFirst();
      visited.add(dualKey);

      if (dualKey.key1 == null || dualKey.key2 == null) {
        if (dualKey.key1 != dualKey.key2) {
          return false;
        }
        continue;
      }

      if (!dualKey.key1.getClass().equals(dualKey.key2.getClass())
          && !weakTypeMatch(dualKey.key1, dualKey.key2)) {
        return false;
      }

      // Handle all [] types.  In order to be equal, the arrays must be the same
      // length, be of the same type, be in the same order, and all elements within
      // the array must be deeply equivalent.
      if (dualKey.key1.getClass().isArray()) {
        int len = Array.getLength(dualKey.key1);
        if (len != Array.getLength(dualKey.key2)) {
          return false;
        }

        for (int i = 0; i < len; i++) {
          DualKey dk = new DualKey(Array.get(dualKey.key1, i), Array.get(dualKey.key2, i));
          if (!visited.contains(dk)) {
            stack.addFirst(dk);
          }
        }
        continue;
      }

      // Special handle SortedSets because they are fast to compare because their
      // elements must be in the same order to be equivalent Sets.
      if (dualKey.key1 instanceof SortedSet) {
        if (!compareOrdered(dualKey, stack, visited)) {
          return false;
        }
        continue;
      }

      // Handled unordered Sets.  This is an expensive comparison because order cannot
      // be assumed, therefore it runs in O(n^2) when the Sets are the same length.
      if (dualKey.key1 instanceof Set) {
        if (!compareUnordered((Set) dualKey.key1, (Set) dualKey.key2, visited)) {
          return false;
        }
        continue;
      }

      // Check any Collection that is not a Set.  In these cases, element order
      // matters, therefore this comparison is faster than using unordered comparison.
      if (dualKey.key1 instanceof Collection) {
        if (!compareOrdered(dualKey, stack, visited)) {
          return false;
        }
        continue;
      }

      // Compare two SortedMaps.  This takes advantage of the fact that these
      // Maps can be compared in O(N) time due to their ordering.
      if (dualKey.key1 instanceof SortedMap) {
        Map map1 = (Map) dualKey.key1;
        Map map2 = (Map) dualKey.key2;

        if (map1.size() != map2.size()) {
          return false;
        }

        Iterator i1 = map1.entrySet().iterator();
        Iterator i2 = map2.entrySet().iterator();

        while (i1.hasNext()) {
          Map.Entry entry1 = (Map.Entry) i1.next();
          Map.Entry entry2 = (Map.Entry) i2.next();

          DualKey dk = new DualKey(entry1.getKey(), entry2.getKey());
          if (!visited.contains(dk)) { // Keys must match
            stack.addFirst(dk);
          }

          dk = new DualKey(entry1.getValue(), entry2.getValue());
          if (!visited.contains(dk)) { // Values must match
            stack.addFirst(dk);
          }
        }

        continue;
      }

      // Compare two Unordered Maps.  This works in O(N^2) time.
      if (dualKey.key1 instanceof Map) {
        Map<Object, Object> map1 = (Map) dualKey.key1;
        Map<Object, Object> map2 = (Map) dualKey.key2;

        if (map1.size() != map2.size()) {
          return false;
        }

        for (Map.Entry entry1 : map1.entrySet()) {
          Map.Entry saveEntry2 = null;
          for (Map.Entry entry2 :
              map2.entrySet()) { // recurse here (yes, that makes this a Stack-based implementation
            // with partial recursion in the case of Map keys).
            if (deepEquals(entry1.getKey(), entry2.getKey(), visited)) {
              saveEntry2 = entry2;
              break;
            }
          }

          if (saveEntry2 == null) {
            return false;
          }

          // Defer value comparisons to future iterations.
          DualKey dk = new DualKey(entry1.getValue(), saveEntry2.getValue());
          if (!visited.contains(dk)) {
            stack.addFirst(dk);
          }
        }

        continue;
      }

      if (hasCustomEquals(dualKey.key1.getClass())) {
        if (!dualKey.key1.equals(dualKey.key2)) {
          return false;
        }
        continue;
      }

      Collection<Field> fields = getDeepDeclaredFields(dualKey.key1.getClass());

      for (Field field : fields) {
        try {
          DualKey dk = new DualKey(field.get(dualKey.key1), field.get(dualKey.key2));
          if (!visited.contains(dk)) {
            stack.addFirst(dk);
          }
        } catch (Exception e) {
          continue;
        }
      }
    }

    return true;
  }

  /**
   * Compare two Collections that must be same length and in same order.
   *
   * @param dualKey DualKey represents the two Collections to compare
   * @param visited Collection of objects already compared (prevents cycles)
   * @param stack add items to compare to the Stack (Stack versus recursion)
   * @return boolean false if the Collections are not the same length, otherwise place collection
   *     items on Stack to be further compared.
   */
  private static boolean compareOrdered(DualKey dualKey, Deque<DualKey> stack, Collection visited) {
    Collection col1 = (Collection) dualKey.key1;
    Collection col2 = (Collection) dualKey.key2;
    if (col1.size() != col2.size()) {
      return false;
    }

    Iterator i1 = col1.iterator();
    Iterator i2 = col2.iterator();

    while (i1.hasNext()) {
      DualKey dk = new DualKey(i1.next(), i2.next());
      if (!visited.contains(dk)) {
        stack.addFirst(dk);
      }
    }
    return true;
  }

  /**
   * Deeply compare the two sets referenced by dualKey. This method attempts to quickly determine
   * inequality by length, then hash, and finally does a deepEquals on each element if the two Sets
   * passed by the prior tests.
   *
   * @param col1 Collection one
   * @param col2 Collection two
   * @param visited Set containing items that have already been compared, so as to prevent cycles.
   * @return boolean true if the Sets are deeply equals, false otherwise.
   */
  private static boolean compareUnordered(Collection col1, Collection col2, Set visited) {
    if (col1.size() != col2.size()) {
      return false;
    }

    int h1 = deepHashCode(col1);
    int h2 = deepHashCode(col2);
    if (h1 != h2) { // Faster than deep equals compare (O(n^2) comparison skipped when not equal)
      return false;
    }

    List copy = new ArrayList(col2);

    for (Object element1 : col1) {
      int len = copy.size();
      for (int i = 0;
          i < len;
          i++) { // recurse here (yes, that makes this a Stack-based implementation with
        // partial recursion in the case of unordered Sets).
        if (deepEquals(element1, copy.get(i), visited)) {
          copy.remove(i); // Shrink 2nd Set
          break;
        }
      }

      if (len == copy.size()) { // Nothing found, therefore Collections are not equivalent
        return false;
      }
    }

    return true;
  }

  public static boolean hasCustomEquals(Class c) {
    Class origClass = c;
    if (_customEquals.containsKey(c)) {
      return _customEquals.get(c);
    }

    while (!Object.class.equals(c)) {
      try {
        c.getDeclaredMethod("equals", Object.class);
        _customEquals.put(origClass, true);
        return true;
      } catch (Exception ignored) { //
      }
      c = c.getSuperclass();
    }
    _customEquals.put(origClass, false);
    return false;
  }

  public static int deepHashCode(Object obj) {
    Set visited = new HashSet();
    List<Object> stack = new ArrayList<>();
    stack.add(0, obj);
    int hash = 0;

    while (!stack.isEmpty()) {
      obj = stack.remove(0);
      if (obj == null || visited.contains(obj)) {
        continue;
      }

      visited.add(obj);

      if (obj.getClass().isArray()) {
        int len = Array.getLength(obj);
        for (int i = 0; i < len; i++) {
          stack.add(0, Array.get(obj, i));
        }
        continue;
      }

      if (obj instanceof Collection) {
        stack.addAll(0, (Collection) obj);
        continue;
      }

      if (obj instanceof Map) {
        stack.addAll(0, ((Map) obj).keySet());
        stack.addAll(0, ((Map) obj).values());
        continue;
      }

      if (hasCustomHashCode(obj.getClass())) { // A real hashCode() method exists, call it.
        hash += obj.hashCode();
        continue;
      }

      Collection<Field> fields = getDeepDeclaredFields(obj.getClass());
      for (Field field : fields) {
        try {
          stack.add(0, field.get(obj));
        } catch (Exception ignored) { //
        }
      }
    }
    return hash;
  }

  public static boolean hasCustomHashCode(Class c) {
    Class origClass = c;
    if (_customHash.containsKey(c)) {
      return _customHash.get(c);
    }

    while (!Object.class.equals(c)) {
      try {
        c.getDeclaredMethod("hashCode");
        _customHash.put(origClass, true);
        return true;
      } catch (Exception ignored) { //
      }
      c = c.getSuperclass();
    }
    _customHash.put(origClass, false);
    return false;
  }

  /**
   * @param c Class instance
   * @return Collection of only the fields in the passed in class that would need further processing
   *     (reference fields). This makes field traversal on a class faster as it does not need to
   *     continually process known fields like primitives.
   */
  public static Collection<Field> getDeepDeclaredFields(Class c) {
    if (_reflectedFields.containsKey(c)) {
      return _reflectedFields.get(c);
    }
    Collection<Field> fields = new ArrayList<Field>();
    Class curr = c;

    while (curr != null) {
      try {
        Field[] local;
        local = curr.getDeclaredFields();

        for (Field field : local) {
          if (!field.isAccessible()) {
            try {
              field.setAccessible(true);
            } catch (Exception ignored) { //
            }
          }

          int modifiers = field.getModifiers();
          if (!Modifier.isStatic(modifiers)
              && !field.getName().startsWith("this$")
              && !Modifier.isTransient(
                  modifiers)) { // speed up: do not count static fields, not go back up to enclosing
            // object in nested case
            fields.add(field);
          }
        }
      } catch (ThreadDeath t) {
        throw t;
      } catch (Throwable ignored) { //
      }

      curr = curr.getSuperclass();
    }
    _reflectedFields.put(c, fields);
    return fields;
  }
}
