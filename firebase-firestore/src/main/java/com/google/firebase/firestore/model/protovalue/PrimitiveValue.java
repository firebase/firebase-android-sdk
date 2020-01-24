// Copyright 2020 Google LLC
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

package com.google.firebase.firestore.model.protovalue;

import static com.google.firebase.firestore.util.Assert.fail;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Blob;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.ProtoValues;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.model.value.FieldValue;
import com.google.firebase.firestore.model.value.ServerTimestampValue;
import com.google.firebase.firestore.util.Assert;
import com.google.firestore.v1.ArrayValue;
import com.google.firestore.v1.Value;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PrimitiveValue extends FieldValue {
  protected final Value internalValue;

  public PrimitiveValue(Value value) {
    this.internalValue = value;
  }

  @Override
  public int typeOrder() {
    return ProtoValues.typeOrder(internalValue);
  }

  @Nullable
  @Override
  public Object value() {
    return convertValue(internalValue);
  }

  @Nullable
  private Object convertValue(Value value) {
    switch (value.getValueTypeCase()) {
      case NULL_VALUE:
        return null;
      case BOOLEAN_VALUE:
        return value.getBooleanValue();
      case INTEGER_VALUE:
        return value.getIntegerValue();
      case DOUBLE_VALUE:
        return value.getDoubleValue();
      case TIMESTAMP_VALUE:
        return new Timestamp(
            value.getTimestampValue().getSeconds(), value.getTimestampValue().getNanos());
      case STRING_VALUE:
        return value.getStringValue();
      case BYTES_VALUE:
        return Blob.fromByteString(value.getBytesValue());
      case REFERENCE_VALUE:
        return convertReference(value.getReferenceValue());
      case GEO_POINT_VALUE:
        return new GeoPoint(
            value.getGeoPointValue().getLatitude(), value.getGeoPointValue().getLongitude());
      case ARRAY_VALUE:
        return convertArray(value.getArrayValue());
      case MAP_VALUE:
        return convertMap(value.getMapValue());
      default:
        throw fail("Unknown value type: " + value.getValueTypeCase());
    }
  }

  private Object convertReference(String value) {
    // TODO(mrschmidt): Move `value()` and `convertValue()` to DocumentSnapshot, which would
    // allow us to validate that the resource name points to the current project.
    ResourcePath resourceName = ResourcePath.fromString(value);
    Assert.hardAssert(
        resourceName.length() > 4 && resourceName.getSegment(4).equals("documents"),
        "Tried to deserialize invalid key %s",
        resourceName);
    return DocumentKey.fromPath(resourceName.popFirst(5));
  }

  private List<Object> convertArray(ArrayValue arrayValue) {
    ArrayList<Object> result = new ArrayList<>(arrayValue.getValuesCount());
    for (Value v : arrayValue.getValuesList()) {
      result.add(convertValue(v));
    }
    return result;
  }

  private Map<String, Object> convertMap(com.google.firestore.v1.MapValue mapValue) {
    Map<String, Object> result = new HashMap<>();
    for (Map.Entry<String, Value> entry : mapValue.getFieldsMap().entrySet()) {
      result.put(entry.getKey(), convertValue(entry.getValue()));
    }
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o instanceof PrimitiveValue) {
      PrimitiveValue value = (PrimitiveValue) o;
      return ProtoValues.equals(this.internalValue, value.internalValue);
    }

    return false;
  }

  @Override
  public int hashCode() {
    return internalValue.hashCode();
  }

  @Override
  public int compareTo(@NonNull FieldValue other) {
    if (other instanceof PrimitiveValue) {
      return ProtoValues.compare(this.internalValue, ((PrimitiveValue) other).internalValue);
    } else if (ProtoValues.isType(this.internalValue, TYPE_ORDER_TIMESTAMP)
        && other instanceof ServerTimestampValue) {
      // TODO(mrschmidt): Handle timestamps directly in PrimitiveValue
      return -1;
    } else {
      return defaultCompareTo(other);
    }
  }

  public Value toProto() {
    return internalValue;
  }
}
