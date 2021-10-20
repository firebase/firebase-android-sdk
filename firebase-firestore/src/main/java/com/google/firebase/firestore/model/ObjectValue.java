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

import static com.google.firebase.firestore.util.Assert.hardAssert;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.firestore.model.mutation.FieldMask;
import com.google.firestore.v1.MapValue;
import com.google.firestore.v1.Value;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** A structured object value stored in Firestore. */
public final class ObjectValue implements Cloneable {
  /**
   * The immutable Value proto for this object. Local mutations are stored in `overlayMap` and only
   * applied when {@link #buildProto()} is invoked.
   */
  private Value partialValue;

  /**
   * A nested map that contains the accumulated changes that haven't yet been applied to {@link
   * #partialValue}. Values can either be {@link Value} protos, {@code Map<String, Object>} values
   * (to represent additional nesting) or {@code null} (to represent field deletes).
   */
  private final Map<String, Object> overlayMap = new HashMap<>();

  public static ObjectValue fromMap(Map<String, Value> value) {
    return new ObjectValue(
        Value.newBuilder().setMapValue(MapValue.newBuilder().putAllFields(value)).build());
  }

  public ObjectValue(Value value) {
    hardAssert(
        value.getValueTypeCase() == Value.ValueTypeCase.MAP_VALUE,
        "ObjectValues should be backed by a MapValue");
    hardAssert(
        !ServerTimestamps.isServerTimestamp(value),
        "ServerTimestamps should not be used as an ObjectValue");
    this.partialValue = value;
  }

  public ObjectValue() {
    this(Value.newBuilder().setMapValue(MapValue.getDefaultInstance()).build());
  }

  public Map<String, Value> getFieldsMap() {
    return buildProto().getMapValue().getFieldsMap();
  }

  /** Recursively extracts the FieldPaths that are set in this ObjectValue. */
  public FieldMask getFieldMask() {
    return extractFieldMask(buildProto().getMapValue());
  }

  private FieldMask extractFieldMask(MapValue value) {
    Set<FieldPath> fields = new HashSet<>();
    for (Map.Entry<String, Value> entry : value.getFieldsMap().entrySet()) {
      FieldPath currentPath = FieldPath.fromSingleSegment(entry.getKey());
      if (Values.isMapValue(entry.getValue())) {
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

  /**
   * Returns the value at the given path or null.
   *
   * @param fieldPath the path to search
   * @return The value at the path or null if it doesn't exist.
   */
  public @Nullable Value get(FieldPath fieldPath) {
    return extractNestedValue(buildProto(), fieldPath);
  }

  @Nullable
  private Value extractNestedValue(Value value, FieldPath fieldPath) {
    if (fieldPath.isEmpty()) {
      return value;
    } else {
      for (int i = 0; i < fieldPath.length() - 1; ++i) {
        value = value.getMapValue().getFieldsOrDefault(fieldPath.getSegment(i), null);
        if (!Values.isMapValue(value)) {
          return null;
        }
      }
      return value.getMapValue().getFieldsOrDefault(fieldPath.getLastSegment(), null);
    }
  }

  /**
   * Returns the Protobuf that backs this ObjectValue.
   *
   * <p>This method applies any outstanding modifications and memoizes the result. Further
   * invocations are based on this memoized result.
   */
  private Value buildProto() {
    synchronized (overlayMap) {
      MapValue mergedResult = applyOverlay(FieldPath.EMPTY_PATH, overlayMap);
      if (mergedResult != null) {
        partialValue = Value.newBuilder().setMapValue(mergedResult).build();
        overlayMap.clear();
      }
    }
    return partialValue;
  }

  /**
   * Removes the field at the specified path. If there is no field at the specified path nothing is
   * changed.
   *
   * @param path The field path to remove
   */
  public void delete(FieldPath path) {
    hardAssert(!path.isEmpty(), "Cannot delete field for empty path on ObjectValue");
    setOverlay(path, null);
  }

  /**
   * Sets the field to the provided value.
   *
   * @param path The field path to set.
   * @param value The value to set.
   */
  public void set(FieldPath path, Value value) {
    hardAssert(!path.isEmpty(), "Cannot set field for empty path on ObjectValue");
    setOverlay(path, value);
  }

  public void setAll(Map<FieldPath, Value> data) {
    for (Map.Entry<FieldPath, Value> entry : data.entrySet()) {
      FieldPath path = entry.getKey();
      if (entry.getValue() == null) {
        delete(path);
      } else {
        set(path, entry.getValue());
      }
    }
  }

  /**
   * Adds {@code value} to the overlay map at {@code path}. Creates nested map entries if needed.
   */
  private void setOverlay(FieldPath path, @Nullable Value value) {
    Map<String, Object> currentLevel = overlayMap;

    for (int i = 0; i < path.length() - 1; ++i) {
      String currentSegment = path.getSegment(i);
      Object currentValue = currentLevel.get(currentSegment);

      if (currentValue instanceof Map) {
        // Re-use a previously created map
        currentLevel = (Map<String, Object>) currentValue;
      } else if (currentValue instanceof Value
          && ((Value) currentValue).getValueTypeCase() == Value.ValueTypeCase.MAP_VALUE) {
        // Convert the existing Protobuf MapValue into a Java map
        Map<String, Object> nextLevel =
            new HashMap<>(((Value) currentValue).getMapValue().getFieldsMap());
        currentLevel.put(currentSegment, nextLevel);
        currentLevel = nextLevel;
      } else {
        // Create an empty hash map to represent the current nesting level
        Map<String, Object> nextLevel = new HashMap<>();
        currentLevel.put(currentSegment, nextLevel);
        currentLevel = nextLevel;
      }
    }

    currentLevel.put(path.getLastSegment(), value);
  }

  /**
   * Applies any overlays from {@code currentOverlays} that exist at `currentPath` and returns the
   * merged data at {@code currentPath} (or {@code null} if there were no changes).
   *
   * @param currentPath The path at the current nesting level. Can be set to {@code
   *     FieldValue.EMPTY_PATH} to represent the root.
   * @param currentOverlays The overlays at the current nesting level in the same format as {@code
   *     overlayMap}.
   * @return The merged data at `currentPath` or null if no modifications were applied.
   */
  private @Nullable MapValue applyOverlay(
      FieldPath currentPath, Map<String, Object> currentOverlays) {
    boolean modified = false;

    @Nullable Value existingValue = extractNestedValue(partialValue, currentPath);
    MapValue.Builder resultAtPath =
        Values.isMapValue(existingValue)
            // If there is already data at the current path, base our modifications on top
            // of the existing data.
            ? existingValue.getMapValue().toBuilder()
            : MapValue.newBuilder();

    for (Map.Entry<String, Object> entry : currentOverlays.entrySet()) {
      String pathSegment = entry.getKey();
      Object value = entry.getValue();

      if (value instanceof Map) {
        @Nullable
        MapValue nested =
            applyOverlay(currentPath.append(pathSegment), (Map<String, Object>) value);
        if (nested != null) {
          resultAtPath.putFields(pathSegment, Value.newBuilder().setMapValue(nested).build());
          modified = true;
        }
      } else if (value instanceof Value) {
        resultAtPath.putFields(pathSegment, (Value) value);
        modified = true;
      } else if (resultAtPath.containsFields(pathSegment)) {
        hardAssert(value == null, "Expected entry to be a Map, a Value or null");
        resultAtPath.removeFields(pathSegment);
        modified = true;
      }
    }

    return modified ? resultAtPath.build() : null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    } else if (o instanceof ObjectValue) {
      return Values.equals(buildProto(), ((ObjectValue) o).buildProto());
    }
    return false;
  }

  @Override
  public int hashCode() {
    return buildProto().hashCode();
  }

  @Override
  @NonNull
  public String toString() {
    return "ObjectValue{" + "internalValue=" + Values.canonicalId(buildProto()) + '}';
  }

  @NonNull
  public ObjectValue clone() {
    return new ObjectValue(buildProto());
  }
}
