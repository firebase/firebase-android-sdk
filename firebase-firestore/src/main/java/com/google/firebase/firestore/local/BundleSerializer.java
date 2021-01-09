// Copyright 2021 Google LLC
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

package com.google.firebase.firestore.local;

import android.util.Base64;
import androidx.annotation.Nullable;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.core.Bound;
import com.google.firebase.firestore.core.FieldFilter;
import com.google.firebase.firestore.core.Filter;
import com.google.firebase.firestore.core.OrderBy;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldPath;
import com.google.firebase.firestore.model.ObjectValue;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.model.Values;
import com.google.firestore.v1.ArrayValue;
import com.google.firestore.v1.MapValue;
import com.google.firestore.v1.Value;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.protobuf.ByteString;
import com.google.protobuf.NullValue;
import com.google.type.LatLng;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/* package= */ class BundleSerializer {
  Gson gson;

  BundleSerializer() {
    GsonBuilder gsonBuilder = new GsonBuilder();
    gsonBuilder.registerTypeAdapter(SnapshotVersion.class, new SnapshotVersionSerializer());
    gsonBuilder.registerTypeAdapter(Query.class, new QuerySerializer());
    gsonBuilder.registerTypeAdapter(Document.class, new DocumentSerializer());
    gson = gsonBuilder.create();
  }

  public NamedQuery fromNamedQuery(String json) {
    return gson.fromJson(json, NamedQuery.class);
  }

  public BundleMetadata fromBundleMetadata(String json) {
    return gson.fromJson(json, BundleMetadata.class);
  }

  public BundledDocumentMetadata fromBundledDocumentMetadata(String json) {
    return gson.fromJson(json, BundledDocumentMetadata.class);
  }

  public Document fromDocument(String json) {
    return gson.fromJson(json, Document.class);
  }

  private SnapshotVersion decodeSnapshotVersion(JsonElement json) {
    JsonObject jsonTimestamp = json.getAsJsonObject();
    long seconds = jsonTimestamp.get("seconds").getAsLong();
    int nanos = jsonTimestamp.get("nanos").getAsInt();
    return new SnapshotVersion(new Timestamp(seconds, nanos));
  }

  private int decodeLimit(JsonObject structuredQuery) {
    JsonElement limitElement = structuredQuery.get("limit");
    return limitElement.getAsInt();
  }

  private Bound decodeBound(JsonObject structuredQuery) {
    List<Value> cursor = new ArrayList<>();

    JsonElement values = structuredQuery.get("values");
    if (values != null) {
      for (JsonElement value : values.getAsJsonArray()) {
        cursor.add(decodeValue(value.getAsJsonObject()));
      }
    }

    JsonElement before = structuredQuery.get("before");

    return new Bound(cursor, before != null && before.getAsBoolean());
  }

  private List<OrderBy> decodeOrderBy(JsonObject structuredQuery) {
    List<OrderBy> result = new ArrayList<>();

    JsonElement orderByElements = structuredQuery.get("orderBy");

    if (orderByElements != null) {
      for (JsonElement orderByElement : orderByElements.getAsJsonArray()) {
        JsonObject orderBy = orderByElements.getAsJsonObject();
        FieldPath fieldPath = decodeFieldReference(orderBy.get("field"));
        JsonElement directionElement = orderBy.get("direction");
        OrderBy.Direction direction =
            directionElement != null && directionElement.getAsString().equals("DESCENDING")
                ? OrderBy.Direction.DESCENDING
                : OrderBy.Direction.ASCENDING;
        result.add(new OrderBy(direction, fieldPath));
      }
    }

    return result;
  }

  private List<Filter> decodeWhere(JsonObject structuredQuery) {
    List<Filter> result = new ArrayList<>();

    JsonElement whereElement = structuredQuery.get("where");
    if (whereElement != null) {
      decodeFilter(result, structuredQuery);
    }

    return result;
  }

  private void decodeFilter(List<Filter> result, JsonObject structuredQuery) {
    if (structuredQuery.has("compositeFilter")) {
      decodeCompositeFilter(result, structuredQuery.getAsJsonObject("compositeFilter"));
    } else if (structuredQuery.has("fieldFilter")) {
      decodeFieldFilter(result, structuredQuery.getAsJsonObject("fieldFilter"));
    } else if (structuredQuery.has("unaryFilter")) {
      decodeUnaryFilter(result, structuredQuery.getAsJsonObject("unaryFilter"));
    }
  }

  private void decodeCompositeFilter(List<Filter> result, JsonObject compositeFilter) {

    JsonElement filterElements = compositeFilter.get("filter");
    if (filterElements != null) {
      for (JsonElement filterElement : filterElements.getAsJsonArray()) {
        decodeFilter(result, filterElement.getAsJsonObject());
      }
    }
  }

  private void decodeFieldFilter(List<Filter> result, JsonObject fieldFilter) {
    FieldPath fieldPath = decodeFieldReference(fieldFilter.get("field"));
    FieldFilter.Operator filterOperator =
        decodeFieldFilterOperator(fieldFilter.get("op").getAsString());
    result.add(
        FieldFilter.create(
            fieldPath, filterOperator, decodeValue(fieldFilter.getAsJsonObject("value"))));
  }

  private Value decodeValue(JsonObject value) {
    Value.Builder builder = Value.newBuilder();

    if (value.has("nullValue")) {
      builder.setNullValue(NullValue.NULL_VALUE);
    } else if (value.has("booleanValue")) {
      builder.setBooleanValue(value.get("booleanValue").getAsBoolean());
    } else if (value.has("integerValue")) {
      builder.setIntegerValue(value.get("integerValue").getAsLong());
    } else if (value.has("doubleValue")) {
      builder.setDoubleValue(value.get("doubleValue").getAsDouble());
    } else if (value.has("timestampValue")) {
      decodeTimestamp(builder, value.getAsJsonObject("timestampObject"));
    } else if (value.has("stringValue")) {
      builder.setStringValue(value.get("stringValue").getAsString());
    } else if (value.has("bytesValue")) {
      builder.setBytesValue(
          ByteString.copyFrom(
              Base64.decode(value.get("bytesValue").getAsString(), Base64.DEFAULT)));
    } else if (value.has("referenceValue")) {
      builder.setReferenceValue(value.get("referenceValue").getAsString());
    } else if (value.has("geoPointValue")) {
      decodeGeoPoint(builder, value.getAsJsonObject("geoPointValue"));
    } else if (value.has("arrayValue")) {
      decodeArrayValue(builder, value.getAsJsonObject("arrayValue"));
    } else if (value.has("mapValue")) {
      decodeMapValue(builder, value.getAsJsonObject("mapValue"));
    } else {
      throw new JsonParseException("Unexpected value type: " + value);
    }

    return builder.build();
  }

  private void decodeArrayValue(Value.Builder builder, JsonObject array) {
    ArrayValue.Builder arrayBuilder = ArrayValue.newBuilder();

    JsonElement values = array.get("values");
    if (values != null) {
      for (JsonElement value : values.getAsJsonArray()) {
        arrayBuilder.addValues(decodeValue(value.getAsJsonObject()));
      }
    }

    builder.setArrayValue(arrayBuilder);
  }

  private void decodeMapValue(Value.Builder builder, JsonObject map) {
    MapValue.Builder mapBuilder = MapValue.newBuilder();

    JsonElement fields = map.get("fields");
    if (fields != null) {
      for (Map.Entry<String, JsonElement> field : fields.getAsJsonObject().entrySet()) {
        mapBuilder.putFields(field.getKey(), decodeValue(field.getValue().getAsJsonObject()));
      }
    }

    builder.setMapValue(mapBuilder);
  }

  private void decodeGeoPoint(Value.Builder builder, JsonObject geoPoint) {
    builder.setGeoPointValue(
        LatLng.newBuilder()
            .setLatitude(geoPoint.get("latitude").getAsDouble())
            .setLatitude(geoPoint.get("longitude").getAsDouble()));
  }

  private void decodeTimestamp(Value.Builder builder, JsonObject timestamp) {
    builder.setTimestampValue(
        com.google.protobuf.Timestamp.newBuilder()
            .setSeconds(timestamp.get("seconds").getAsLong())
            .setNanos(timestamp.get("nanos").getAsInt()));
  }

  private FieldFilter.Operator decodeFieldFilterOperator(String operator) {
    return FieldFilter.Operator.valueOf(operator);
  }

  private void decodeUnaryFilter(List<Filter> result, JsonObject unaryFilter) {
    FieldPath fieldPath = decodeFieldReference(unaryFilter.get("field"));
    String operator = unaryFilter.get("op").getAsString();

    switch (operator) {
      case "IS_NAN":
        result.add(FieldFilter.create(fieldPath, Filter.Operator.EQUAL, Values.NAN_VALUE));
      case "IS_NULL":
        result.add(FieldFilter.create(fieldPath, Filter.Operator.EQUAL, Values.NULL_VALUE));
      case "IS_NOT_NAN":
        result.add(FieldFilter.create(fieldPath, Filter.Operator.NOT_EQUAL, Values.NAN_VALUE));
      case "IS_NOT_NULL":
        result.add(FieldFilter.create(fieldPath, Filter.Operator.NOT_EQUAL, Values.NULL_VALUE));
      default:
        throw new JsonParseException("Unexpected unary filter: " + operator);
    }
  }

  private FieldPath decodeFieldReference(JsonElement fieldReference) {
    return FieldPath.fromServerFormat(
        fieldReference.getAsJsonObject().get("fieldPath").getAsString());
  }

  private Query.LimitType decodeLimitType(JsonObject bundledQueryJson) {
    JsonElement limitTypElement = bundledQueryJson.get("limitType");
    return limitTypElement != null && limitTypElement.getAsString().equals("LAST")
        ? Query.LimitType.LIMIT_TO_LAST
        : Query.LimitType.LIMIT_TO_FIRST;
  }

  private ResourcePath decodeParent(JsonObject bundledQueryJson) {
    String parent = bundledQueryJson.get("parent").getAsString();
    return ResourcePath.fromString(parent);
  }

  private class DocumentSerializer implements JsonDeserializer<Document> {
    public Document deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
        throws JsonParseException {

      JsonObject jsonDocument = json.getAsJsonObject();
      String name = jsonDocument.get("name").getAsString();
      DocumentKey key = DocumentKey.fromName(name);

      SnapshotVersion updateTime = decodeSnapshotVersion(jsonDocument.get("updateTime"));

      Value.Builder value = Value.newBuilder();
      decodeMapValue(value, jsonDocument.getAsJsonObject("fields"));

      return new Document(
          key,
          updateTime,
          ObjectValue.fromMap(value.getMapValue().getFieldsMap()),
          Document.DocumentState.SYNCED);
    }
  }

  private class SnapshotVersionSerializer implements JsonDeserializer<SnapshotVersion> {
    public SnapshotVersion deserialize(
        JsonElement json, Type typeOfT, JsonDeserializationContext context)
        throws JsonParseException {
      return decodeSnapshotVersion(json);
    }
  }

  private class QuerySerializer implements JsonDeserializer<Query> {
    public Query deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
        throws JsonParseException {
      JsonObject bundledQueryJson = json.getAsJsonObject();
      JsonObject structuredQuery = bundledQueryJson.get("structuredQuery").getAsJsonObject();

      ResourcePath parent = decodeParent(bundledQueryJson);
      verifyNoSelect(structuredQuery);

      JsonElement fromElements = structuredQuery.get("from");
      JsonArray from = fromElements.getAsJsonArray();
      verifyCollectionSelector(from);
      JsonObject collectionSelector = from.get(0).getAsJsonObject();
      JsonElement allDescendants = collectionSelector.get("allDescendants");
      @Nullable String collectionGroup = null;
      if (allDescendants != null && allDescendants.getAsBoolean()) {
        collectionGroup = collectionSelector.get("collectionId").getAsString();
      } else {
        parent.append(collectionSelector.get("collectionId").getAsString());
      }

      List<Filter> filters = decodeWhere(structuredQuery);
      List<OrderBy> orderBys = decodeOrderBy(structuredQuery);
      @Nullable Bound startAt = decodeBound(structuredQuery);
      @Nullable Bound endAt = decodeBound(structuredQuery);
      verifyNoOffset(structuredQuery);
      int limit = decodeLimit(structuredQuery);
      Query.LimitType limitType = decodeLimitType(bundledQueryJson);

      return new Query(
          parent, collectionGroup, filters, orderBys, limit, limitType, startAt, endAt);
    }

    private void verifyCollectionSelector(JsonArray from) {
      if (from.size() != 1) {
        throw new JsonParseException(
            "Only queries with a single 'from' clause are supported by the Android SDK");
      }
    }

    private void verifyNoOffset(JsonObject structuredQuery) {
      if (structuredQuery.get("offset") != null) {
        throw new JsonParseException("Queries with offset are not supported by the Android SDK");
      }
    }

    private void verifyNoSelect(JsonObject structuredQuery) {
      if (structuredQuery.get("select") != null) {
        throw new JsonParseException(
            "Queries with 'select' statements are not supported by the Android SDK");
      }
    }
  }
}
