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
import androidx.annotation.VisibleForTesting;

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
import com.google.firebase.firestore.remote.RemoteSerializer;
import com.google.firestore.v1.ArrayValue;
import com.google.firestore.v1.MapValue;
import com.google.firestore.v1.Value;
import com.google.protobuf.ByteString;
import com.google.protobuf.NullValue;
import com.google.type.LatLng;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** A JSON serializer to deserialize Firestore Bundles. */
/* package= */ class BundleSerializer {

  private final RemoteSerializer remoteSerializer;

  public BundleSerializer(RemoteSerializer remoteSerializer) {
    this.remoteSerializer = remoteSerializer;
  }

  public NamedQuery fromNamedQuery(String json) throws JSONException {
    JSONObject JSONObject = new JSONObject(json);
    String name = JSONObject.getString("name");
    Query query = decodeQuery(JSONObject.getJSONObject("bundledQuery"));
    SnapshotVersion readTime = decodeSnapshotVersion(JSONObject.getJSONObject("readTime"));
    return new NamedQuery(name, query, readTime);
  }

  public BundleMetadata fromBundleMetadata(String json) throws JSONException {
    JSONObject jsonObject = new JSONObject(json);
    String bundleId = jsonObject.getString("id");
    int version = jsonObject.getInt("version");
    SnapshotVersion createTime = decodeSnapshotVersion(jsonObject.getJSONObject("createTime"));
    int totalDocuments = jsonObject.getInt("totalDocuments");
    long totalBytes = jsonObject.getLong("totalBytes");
    return new BundleMetadata(bundleId, version, createTime, totalDocuments, totalBytes);
  }

  public BundledDocumentMetadata fromBundledDocumentMetadata(String json) throws JSONException {
    JSONObject jsonObject = new JSONObject(json);
    DocumentKey key = DocumentKey.fromName(jsonObject.getString("name"));
    SnapshotVersion readTime = decodeSnapshotVersion(jsonObject.getJSONObject("readTime"));
    boolean exists = jsonObject.optBoolean("exists", false);
    JSONArray queriesJson = jsonObject.optJSONArray("queries");
    List<String> queries = new ArrayList<>();
    if (queriesJson != null) {
      for (int i = 0; i < queriesJson.length(); ++i) {
        queries.add(queriesJson.getString(i));
      }
    }
    return new BundledDocumentMetadata(key, readTime, exists, queries);
  }

  @VisibleForTesting
  Document fromDocument(String json) throws JSONException {
    JSONObject jsonDocument = new JSONObject(json);
    String name = jsonDocument.getString("name");
    DocumentKey key = DocumentKey.fromName(name);
    SnapshotVersion updateTime = decodeSnapshotVersion(jsonDocument.getJSONObject("updateTime"));

    Value.Builder value = Value.newBuilder();
    decodeMapValue(value, jsonDocument.getJSONObject("fields"));

    return new Document(
        key,
        updateTime,
        ObjectValue.fromMap(value.getMapValue().getFieldsMap()),
        Document.DocumentState.SYNCED);
  }

  private SnapshotVersion decodeSnapshotVersion(JSONObject json) {
    long seconds = json.optLong("seconds", 0);
    int nanos = json.optInt("nanos", 0);
    return new SnapshotVersion(new Timestamp(seconds, nanos));
  }

  private Query decodeQuery(JSONObject bundledQueryJson) throws JSONException {
    JSONObject structuredQuery = bundledQueryJson.getJSONObject("structuredQuery");

    ResourcePath parent = decodeParent(bundledQueryJson.getString("parent"));
    verifyNoSelect(structuredQuery);

    JSONArray from = structuredQuery.getJSONArray("from");
    verifyCollectionSelector(from);
    JSONObject collectionSelector = from.getJSONObject(0);
    boolean allDescendants = collectionSelector.optBoolean("allDescendants", false);
    @Nullable String collectionGroup = null;
    if (allDescendants) {
      collectionGroup = collectionSelector.getString("collectionId");
    } else {
      parent = parent.append(collectionSelector.getString("collectionId"));
    }

    List<Filter> filters = decodeWhere(structuredQuery.optJSONObject("where"));
    List<OrderBy> orderBys = decodeOrderBy(structuredQuery.optJSONArray("orderBy"));
    @Nullable Bound startAt = decodeBound(structuredQuery.optJSONObject("startAt"));
    @Nullable Bound endAt = decodeBound(structuredQuery.optJSONObject("endAt"));

    verifyNoOffset(structuredQuery);
    int limit = decodeLimit(structuredQuery);
    Query.LimitType limitType = decodeLimitType(bundledQueryJson);

    return new Query(parent, collectionGroup, filters, orderBys, limit, limitType, startAt, endAt);
  }

  private int decodeLimit(JSONObject structuredQuery) {
    return structuredQuery.optInt("limit", -1);
  }

  private Bound decodeBound(@Nullable JSONObject bound) throws JSONException {
    if (bound != null) {
      List<Value> cursor = new ArrayList<>();
      boolean before = bound.optBoolean("before", false);

      JSONArray values = bound.optJSONArray("values");
      if (values != null) {
        for (int i = 0; i < values.length(); ++i) {
          cursor.add(decodeValue(values.getJSONObject(i)));
        }
      }

      return new Bound(cursor, before);
    }

    return null;
  }

  private List<OrderBy> decodeOrderBy(@Nullable JSONArray orderBys) throws JSONException {
    List<OrderBy> result = new ArrayList<>();

    if (orderBys != null) {
      for (int i = 0; i < orderBys.length(); ++i) {
        JSONObject orderBy = orderBys.getJSONObject(i);
        FieldPath fieldPath = decodeFieldReference(orderBy.getJSONObject("field"));
        String directionString = orderBy.optString("direction", "ASCENDING");
        OrderBy.Direction direction =
            directionString.equals("ASCENDING")
                ? OrderBy.Direction.ASCENDING
                : OrderBy.Direction.DESCENDING;
        result.add(OrderBy.getInstance(direction, fieldPath));
      }
    }

    return result;
  }

  private List<Filter> decodeWhere(@Nullable JSONObject where) throws JSONException {
    List<Filter> result = new ArrayList<>();
    if (where != null) {
      decodeFilter(result, where);
    }
    return result;
  }

  private void decodeFilter(List<Filter> result, JSONObject structuredQuery) throws JSONException {
    if (structuredQuery.has("compositeFilter")) {
      decodeCompositeFilter(result, structuredQuery.getJSONObject("compositeFilter"));
    } else if (structuredQuery.has("fieldFilter")) {
      decodeFieldFilter(result, structuredQuery.getJSONObject("fieldFilter"));
    } else if (structuredQuery.has("unaryFilter")) {
      decodeUnaryFilter(result, structuredQuery.getJSONObject("unaryFilter"));
    }
  }

  private void decodeCompositeFilter(List<Filter> result, JSONObject compositeFilter)
      throws JSONException {
    JSONArray filters = compositeFilter.optJSONArray("filters");
    if (filters != null) {
      for (int i = 0; i < filters.length(); ++i) {
        decodeFilter(result, filters.getJSONObject(i));
      }
    }
  }

  private void decodeFieldFilter(List<Filter> result, JSONObject fieldFilter) throws JSONException {
    FieldPath fieldPath = decodeFieldReference(fieldFilter.getJSONObject("field"));
    FieldFilter.Operator filterOperator = decodeFieldFilterOperator(fieldFilter.getString("op"));
    result.add(
        FieldFilter.create(
            fieldPath, filterOperator, decodeValue(fieldFilter.getJSONObject("value"))));
  }

  private Value decodeValue(JSONObject value) throws JSONException {
    Value.Builder builder = Value.newBuilder();

    if (value.has("nullValue")) {
      builder.setNullValue(NullValue.NULL_VALUE);
    } else if (value.has("booleanValue")) {
      builder.setBooleanValue(value.optBoolean("booleanValue", false));
    } else if (value.has("integerValue")) {
      builder.setIntegerValue(value.optLong("integerValue", 0));
    } else if (value.has("doubleValue")) {
      builder.setDoubleValue(value.optDouble("doubleValue", 0.0));
    } else if (value.has("timestampValue")) {
      decodeTimestamp(builder, value.getJSONObject("timestampValue"));
    } else if (value.has("stringValue")) {
      builder.setStringValue(value.optString("stringValue", ""));
    } else if (value.has("bytesValue")) {
      builder.setBytesValue(
          ByteString.copyFrom(Base64.decode(value.getString("bytesValue"), Base64.DEFAULT)));
    } else if (value.has("referenceValue")) {
      builder.setReferenceValue(value.getString("referenceValue"));
    } else if (value.has("geoPointValue")) {
      decodeGeoPoint(builder, value.getJSONObject("geoPointValue"));
    } else if (value.has("arrayValue")) {
      decodeArrayValue(builder, value.getJSONObject("arrayValue").optJSONArray("values"));
    } else if (value.has("mapValue")) {
      decodeMapValue(builder, value.getJSONObject("mapValue").optJSONObject("fields"));
    } else {
      throw new IllegalArgumentException("Unexpected value type: " + value);
    }

    return builder.build();
  }

  private void decodeArrayValue(Value.Builder builder, @Nullable JSONArray values)
      throws JSONException {
    ArrayValue.Builder arrayBuilder = ArrayValue.newBuilder();
    if (values != null) {
      for (int i = 0; i < values.length(); ++i) {
        arrayBuilder.addValues(decodeValue(values.getJSONObject(i)));
      }
    }
    builder.setArrayValue(arrayBuilder);
  }

  private void decodeMapValue(Value.Builder builder, @Nullable JSONObject map)
      throws JSONException {
    MapValue.Builder mapBuilder = MapValue.newBuilder();
    if (map != null) {
      for (Iterator<String> it = map.keys(); it.hasNext(); ) {
        String key = it.next();
        mapBuilder.putFields(key, decodeValue(map.getJSONObject(key)));
      }
    }
    builder.setMapValue(mapBuilder);
  }

  private void decodeGeoPoint(Value.Builder builder, JSONObject geoPoint) {
    builder.setGeoPointValue(
        LatLng.newBuilder()
            .setLatitude(geoPoint.optDouble("latitude", 0.0))
            .setLongitude(geoPoint.optDouble("longitude", 0.0)));
  }

  private void decodeTimestamp(Value.Builder builder, JSONObject timestamp) {
    builder.setTimestampValue(
        com.google.protobuf.Timestamp.newBuilder()
            .setSeconds(timestamp.optLong("seconds", 0))
            .setNanos(timestamp.optInt("nanos", 0)));
  }

  private FieldFilter.Operator decodeFieldFilterOperator(String operator) {
    return FieldFilter.Operator.valueOf(operator);
  }

  private void decodeUnaryFilter(List<Filter> result, JSONObject unaryFilter) throws JSONException {
    FieldPath fieldPath = decodeFieldReference(unaryFilter.getJSONObject("field"));
    String operator = unaryFilter.getString("op");

    switch (operator) {
      case "IS_NAN":
        result.add(FieldFilter.create(fieldPath, Filter.Operator.EQUAL, Values.NAN_VALUE));
        break;
      case "IS_NULL":
        result.add(FieldFilter.create(fieldPath, Filter.Operator.EQUAL, Values.NULL_VALUE));
        break;
      case "IS_NOT_NAN":
        result.add(FieldFilter.create(fieldPath, Filter.Operator.NOT_EQUAL, Values.NAN_VALUE));
        break;
      case "IS_NOT_NULL":
        result.add(FieldFilter.create(fieldPath, Filter.Operator.NOT_EQUAL, Values.NULL_VALUE));
        break;
      default:
        throw new IllegalArgumentException("Unexpected unary filter: " + operator);
    }
  }

  private FieldPath decodeFieldReference(JSONObject fieldReference) throws JSONException {
    return FieldPath.fromServerFormat(fieldReference.getString("fieldPath"));
  }

  private Query.LimitType decodeLimitType(JSONObject bundledQueryJson) {
    String limitType = bundledQueryJson.optString("limitType", "FIRST");
    return limitType.equals("FIRST")
        ? Query.LimitType.LIMIT_TO_FIRST
        : Query.LimitType.LIMIT_TO_LAST;
  }

  private ResourcePath decodeParent(String parent) {
    return remoteSerializer.decodeQueryPath(parent);
  }

  private void verifyCollectionSelector(JSONArray from) {
    if (from.length() != 1) {
      throw new IllegalArgumentException(
          "Only queries with a single 'from' clause are supported by the Android SDK");
    }
  }

  private void verifyNoOffset(JSONObject structuredQuery) {
    if (structuredQuery.has("offset")) {
      throw new IllegalArgumentException(
          "Queries with offsets are not supported by the Android SDK");
    }
  }

  private void verifyNoSelect(JSONObject structuredQuery) {
    if (structuredQuery.has("select")) {
      throw new IllegalArgumentException(
          "Queries with 'select' statements are not supported by the Android SDK");
    }
  }
}
