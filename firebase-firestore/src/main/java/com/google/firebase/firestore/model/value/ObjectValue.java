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

import static com.google.firebase.firestore.model.value.ProtoValues.isType;
import static com.google.firebase.firestore.util.Assert.hardAssert;

import androidx.annotation.Nullable;
import com.google.firebase.firestore.model.FieldPath;
import com.google.firebase.firestore.model.mutation.FieldMask;
import com.google.firestore.v1.MapValue;
import com.google.firestore.v1.Value;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

public class ObjectValue extends FieldValue {
  private static final ObjectValue EMPTY_VALUE =
      new ObjectValue(
          com.google.firestore.v1.Value.newBuilder()
              .setMapValue(com.google.firestore.v1.MapValue.getDefaultInstance())
              .build());

  public static ObjectValue emptyObject() {
    return EMPTY_VALUE;
  }

  ObjectValue(Value value) {
    super(value);
  }

  public static ObjectValue valueOf(Map<String, Value> fieldsMap) {
    return new ObjectValue(
        Value.newBuilder().setMapValue(MapValue.newBuilder().putAllFields(fieldsMap)).build());
  }

  public static Builder newBuilder() {
    return emptyObject().toBuilder();
  }

  /**
   * Returns the value at the given path or null.
   *
   * @param fieldPath the path to search
   * @return The value at the path or if there it doesn't exist.
   */
  public @Nullable FieldValue get(FieldPath fieldPath) {
    Value value = internalValue;

    for (int i = 0; i < fieldPath.length() - 1; ++i) {
      value = value.getMapValue().getFieldsOrDefault(fieldPath.getSegment(i), null);
      if (!isType(value, TYPE_ORDER_OBJECT)) {
        return null;
      }
    }

    value = value.getMapValue().getFieldsOrDefault(fieldPath.getLastSegment(), null);
    return FieldValue.valueOf(value);
  }

  public Map<String, Value> getFieldsMap() {
    return internalValue.getMapValue().getFieldsMap();
  }

  /** Recursively extracts the FieldPaths that are set in this ObjectValue. */
  public FieldMask getFieldMask() {
    return extractFieldMask(internalValue.getMapValue());
  }

  private FieldMask extractFieldMask(MapValue value) {
    Set<FieldPath> fields = new HashSet<>();
    for (Map.Entry<String, Value> entry : value.getFieldsMap().entrySet()) {
      FieldPath currentPath = FieldPath.fromSingleSegment(entry.getKey());
      if (isType(entry.getValue(), TYPE_ORDER_OBJECT)) {
        FieldMask nestedMask = extractFieldMask(entry.getValue().getMapValue());
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

  /** Creates a ObjectValue.Builder instance that is based on the current value. */
  public ObjectValue.Builder toBuilder() {
    return new Builder(this);
  }

  /** An ObjectValue.Builder provides APIs to set and delete fields from an ObjectValue. */
  public static class Builder {

    /** The existing data to mutate. */
    private ObjectValue baseObject;

    /**
     * A list of FieldPath/Value pairs to apply to the base object. `null` values indicate field
     * deletes. MapValues are expanded before they are stored in the overlay map, so that an entry
     * exists for each leaf node.
     */
    private SortedMap<FieldPath, Value> overlayMap;

    Builder(ObjectValue baseObject) {
      this.baseObject = baseObject;
      this.overlayMap = new TreeMap<>();
    }

    /**
     * Sets the field to the provided value.
     *
     * @param path The field path to set.
     * @param value The value to set.
     * @return The current Builder instance.
     */
    public Builder set(FieldPath path, Value value) {
      hardAssert(!path.isEmpty(), "Cannot set field for empty path on ObjectValue");
      removeConflictingOverlays(path);
      setOverlay(path, value);
      return this;
    }

    /**
     * Removes the field at the specified path. If there is no field at the specified path nothing
     * is changed.
     *
     * @param path The field path to remove
     * @return The current Builder instance.
     */
    public Builder delete(FieldPath path) {
      hardAssert(!path.isEmpty(), "Cannot delete field for empty path on ObjectValue");
      removeConflictingOverlays(path);
      setOverlay(path, null);
      return this;
    }

    /** Remove any existing overlays that would be replaced by setting `path` to a new value. */
    private void removeConflictingOverlays(FieldPath path) {
      Iterator<FieldPath> iterator =
          overlayMap.subMap(path, createSuccessor(path)).keySet().iterator();
      while (iterator.hasNext()) {
        iterator.next();
        iterator.remove();
      }
    }

    /**
     * Adds `value` to the overlay map at `path`. MapValues are recursively expanded into one
     * overlay per leaf node.
     */
    private void setOverlay(FieldPath path, @Nullable Value value) {
      if (!isType(value, TYPE_ORDER_OBJECT) || value.getMapValue().getFieldsCount() == 0) {
        overlayMap.put(path, value);
      } else {
        for (Map.Entry<String, Value> entry : value.getMapValue().getFieldsMap().entrySet()) {
          setOverlay(path.append(entry.getKey()), entry.getValue());
        }
      }
    }

    /** Returns an ObjectValue with all mutations applied. */
    public ObjectValue build() {
      if (overlayMap.isEmpty()) {
        return baseObject;
      } else {
        MapValue.Builder result = baseObject.internalValue.getMapValue().toBuilder();
        applyOverlay(FieldPath.EMPTY_PATH, result);
        return new ObjectValue(Value.newBuilder().setMapValue(result).build());
      }
    }

    /**
     * Applies any overlays from `overlayMap` that exist at `currentPath` to the `resultAtPath` map.
     * Overlays are expanded recursively based on their location in the backing ObjectValue's
     * subtree and are processed by nesting level.
     *
     * <p>Example: Overlays { 'a.b.c' : 'foo', 'a.b.d' : 'bar', 'a.e' : 'foobar' }
     *
     * <p>To apply these overlays, the methods first creates a MapValue.Builder for `a`. It then
     * calls applyOverlay() with a current path of `a` and the newly created MapValue.Builder. In
     * its second call, `applyOverlay` assigns `a.b` to a new MapBuilder and `a.e` to 'foobar'. The
     * third call assigns `a.b.c` and `a.b.d` to the MapValue.Builder created in the second step.
     *
     * <p>The overall aim of this method is to minimize conversions between MapValues and their
     * builders.
     *
     * @param currentPath The path at the current nesting level. Can be set toFieldValue.EMPTY_PATH
     *     to represent the root.
     * @param resultAtPath A mutable copy of the existing data at the current nesting level.
     *     Overlays are applied to this argument.
     * @return Whether any modifications were applied (in any part of the subtree under
     *     currentPath).
     */
    private boolean applyOverlay(FieldPath currentPath, MapValue.Builder resultAtPath) {
      // Extract the data that exists at or below the current path. Te extracted subtree is
      // subdivided during each iteration. The iteration stops when the slice becomes empty.
      SortedMap<FieldPath, Value> currentSlice =
          currentPath.isEmpty()
              ? overlayMap
              : overlayMap.subMap(currentPath, createSuccessor(currentPath));

      boolean modified = false;

      while (!currentSlice.isEmpty()) {
        FieldPath fieldPath = currentSlice.firstKey();

        if (fieldPath.length() == currentPath.length() + 1) {
          // The key in the slice is a leaf node. We can apply the value directly.
          String fieldName = fieldPath.getLastSegment();
          Value overlayValue = overlayMap.get(fieldPath);
          if (overlayValue != null) {
            resultAtPath.putFields(fieldName, overlayValue);
            modified = true;
          } else if (resultAtPath.containsFields(fieldName)) {
            resultAtPath.removeFields(fieldName);
            modified = true;
          }
        } else {
          // Since we need a MapValue.Builder at each nesting level (e.g. to create the field for
          // `a.b.c` we need to create a MapValue.Builder for `a` as well as `a.b`), we invoke
          // applyOverlay() recursively with the next nesting level.
          FieldPath nextSliceStart = fieldPath.keepFirst(currentPath.length() + 1);
          @Nullable FieldValue existingValue = baseObject.get(nextSliceStart);
          MapValue.Builder nextSliceBuilder =
              existingValue instanceof ObjectValue
                  // If there is already data at the current path, base our modifications on top
                  // of the existing data.
                  ? ((ObjectValue) existingValue).internalValue.getMapValue().toBuilder()
                  : MapValue.newBuilder();
          modified = applyOverlay(nextSliceStart, nextSliceBuilder) || modified;
          if (modified) {
            // Only apply the result if a field has been modified. This avoids adding an empty
            // map entry for deletes of non-existing fields.
            resultAtPath.putFields(
                nextSliceStart.getLastSegment(),
                Value.newBuilder().setMapValue(nextSliceBuilder).build());
          }
        }

        // Shrink the subtree to contain only values after the current field path. Note that we are
        // still bound by the subtree created at the initial method invocation. The current loop
        // exits when the subtree becomes empty.
        currentSlice = currentSlice.tailMap(createSuccessor(fieldPath));
      }

      return modified;
    }

    /** Create the first field path that is not part of the subtree created by `currentPath`. */
    private FieldPath createSuccessor(FieldPath currentPath) {
      hardAssert(!currentPath.isEmpty(), "Can't create a successor for an empty path");
      return currentPath.popLast().append(currentPath.getLastSegment() + '0');
    }
  }
}
