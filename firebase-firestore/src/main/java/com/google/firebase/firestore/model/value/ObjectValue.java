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

package com.google.firebase.firestore.model.value;

import static com.google.firebase.firestore.util.Assert.hardAssert;

import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.firestore.model.FieldPath;
import com.google.firebase.firestore.model.mutation.FieldMask;
import com.google.firebase.firestore.util.Util;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.annotation.Nullable;

/** A structured object value stored in Firestore. */
public class ObjectValue extends FieldValue {
  private static final ObjectValue EMPTY_INSTANCE =
      new ObjectValue(ImmutableSortedMap.Builder.emptyMap(Util.<String>comparator()));

  public static ObjectValue fromMap(Map<String, FieldValue> value) {
    return fromImmutableMap(ImmutableSortedMap.Builder.fromMap(value, Util.comparator()));
  }

  public static ObjectValue fromImmutableMap(ImmutableSortedMap<String, FieldValue> value) {
    if (value.isEmpty()) {
      return EMPTY_INSTANCE;
    } else {
      return new ObjectValue(value);
    }
  }

  public static ObjectValue emptyObject() {
    return EMPTY_INSTANCE;
  }

  private final ImmutableSortedMap<String, FieldValue> internalValue;

  private ObjectValue(ImmutableSortedMap<String, FieldValue> value) {
    internalValue = value;
  }

  @Override
  public int typeOrder() {
    return TYPE_ORDER_OBJECT;
  }

  /** Recursively extracts the FieldPaths that are set in this ObjectValue. */
  public FieldMask getFieldMask() {
    Set<FieldPath> fields = new HashSet<>();
    for (Map.Entry<String, FieldValue> entry : internalValue) {
      FieldPath currentPath = FieldPath.fromSingleSegment(entry.getKey());
      FieldValue value = entry.getValue();
      if (value instanceof ObjectValue) {
        FieldMask nestedMask = ((ObjectValue) value).getFieldMask();
        Set<FieldPath> nestedFields = nestedMask.getMask();
        if (nestedFields.isEmpty()) {
          // Preserve the empty map by adding it to the FieldMask.
          fields.add(currentPath);
        } else {
          // For nested and non-empty ObjectValues, add the FieldPath of the leaf nodes.
          for (FieldPath nestedPath : nestedFields) {
            fields.add(currentPath.append(nestedPath));
          }
        }
      } else {
        fields.add(currentPath);
      }
    }
    return FieldMask.fromSet(fields);
  }

  /** Recursively converts the Map into the value that users will see in document snapshots. */
  @Override
  public Map<String, Object> value() {
    Map<String, Object> res = new HashMap<>();
    for (Map.Entry<String, FieldValue> entry : internalValue) {
      res.put(entry.getKey(), entry.getValue().value());
    }
    return res;
  }

  public ImmutableSortedMap<String, FieldValue> getInternalValue() {
    return internalValue;
  }

  @Override
  public String toString() {
    return internalValue.toString();
  }

  @Override
  public int hashCode() {
    return internalValue.hashCode();
  }

  @Override
  public int compareTo(FieldValue o) {
    if (o instanceof ObjectValue) {
      ObjectValue other = (ObjectValue) o;
      Iterator<Entry<String, FieldValue>> iterator1 = internalValue.iterator();
      Iterator<Entry<String, FieldValue>> iterator2 = other.internalValue.iterator();
      while (iterator1.hasNext() && iterator2.hasNext()) {
        Entry<String, FieldValue> entry1 = iterator1.next();
        Entry<String, FieldValue> entry2 = iterator2.next();
        int keyCompare = entry1.getKey().compareTo(entry2.getKey());
        if (keyCompare != 0) {
          return keyCompare;
        }
        int valueCompare = entry1.getValue().compareTo(entry2.getValue());
        if (valueCompare != 0) {
          return valueCompare;
        }
      }

      // Only equal if both iterators are exhausted.
      return Util.compareBooleans(iterator1.hasNext(), iterator2.hasNext());
    } else {
      return defaultCompareTo(o);
    }
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof ObjectValue
        && internalValue.equals(((ObjectValue) other).internalValue);
  }

  /**
   * Returns a new ObjectValue with the field at the named path set to value.
   *
   * @param path The field path to set.
   * @param value The value to set.
   * @return A new ObjectValue with the field set.
   */
  public ObjectValue set(FieldPath path, FieldValue value) {
    hardAssert(!path.isEmpty(), "Cannot set field for empty path on ObjectValue");
    String childName = path.getFirstSegment();
    if (path.length() == 1) {
      return setChild(childName, value);
    } else {
      FieldValue child = internalValue.get(childName);
      ObjectValue obj;
      if (child instanceof ObjectValue) {
        obj = (ObjectValue) child;
      } else {
        obj = emptyObject();
      }
      ObjectValue newChild = obj.set(path.popFirst(), value);
      return setChild(childName, newChild);
    }
  }

  /**
   * Returns an ObjectValue with the field path deleted. If there is no field at the specified path
   * nothing is changed.
   *
   * @param path The field path to remove
   * @return An ObjectValue with the field path removed.
   */
  public ObjectValue delete(FieldPath path) {
    hardAssert(!path.isEmpty(), "Cannot delete field for empty path on ObjectValue");
    String childName = path.getFirstSegment();
    if (path.length() == 1) {
      return fromImmutableMap(internalValue.remove(childName));
    } else {
      FieldValue child = internalValue.get(childName);
      if (child instanceof ObjectValue) {
        ObjectValue newChild = ((ObjectValue) child).delete(path.popFirst());
        return setChild(childName, newChild);
      } else {
        // Don't actually change a primitive value to an object for a delete.
        return this;
      }
    }
  }

  /**
   * Returns the value at the given path or null.
   *
   * @param fieldPath the path to search
   * @return The value at the path or if there it doesn't exist.
   */
  public @Nullable FieldValue get(FieldPath fieldPath) {
    FieldValue current = this;
    for (int i = 0; i < fieldPath.length(); i++) {
      if (!(current instanceof ObjectValue)) {
        return null;
      }
      current = ((ObjectValue) current).internalValue.get(fieldPath.getSegment(i));
    }
    return current;
  }

  private ObjectValue setChild(String childName, FieldValue value) {
    return fromImmutableMap(internalValue.insert(childName, value));
  }
}
