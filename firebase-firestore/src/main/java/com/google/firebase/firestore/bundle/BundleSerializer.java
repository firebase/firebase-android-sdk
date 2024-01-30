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

package com.google.firebase.firestore.bundle;

import android.util.Base64;
import androidx.annotation.Nullable;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.core.Bound;
import com.google.firebase.firestore.core.FieldFilter;
import com.google.firebase.firestore.core.Filter;
import com.google.firebase.firestore.core.OrderBy;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldPath;
import com.google.firebase.firestore.model.MutableDocument;
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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** A JSON serializer to deserialize Firestore Bundles. */
public class BundleSerializer {

  private static final long MILLIS_PER_SECOND = 1000;

  private final SimpleDateFormat timestampFormat;
  private final RemoteSerializer remoteSerializer;

  public BundleSerializer(RemoteSerializer remoteSerializer) {
    this.remoteSerializer = remoteSerializer;

    timestampFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH);
    GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
    // We use Proleptic Gregorian Calendar (specifically, Gregorian calendar extends backwards to
    // year one) for timestamp formatting.
    calendar.setGregorianChange(new Date(Long.MIN_VALUE));
    timestampFormat.setCalendar(calendar);
  }

  public NamedQuery decodeNamedQuery(JSONObject namedQuery) throws JSONException {
    String name = namedQuery.getString("name");
    BundledQuery bundledQuery = decodeBundledQuery(namedQuery.getJSONObject("bundledQuery"));
    SnapshotVersion readTime = decodeSnapshotVersion(namedQuery.get("readTime"));
    return new NamedQuery(name, bundledQuery, readTime);
  }

  public BundleMetadata decodeBundleMetadata(JSONObject bundleMetadata) throws JSONException {
    String bundleId = bundleMetadata.getString("id");
    int version = bundleMetadata.getInt("version");
    SnapshotVersion createTime = decodeSnapshotVersion(bundleMetadata.get("createTime"));
    int totalDocuments = bundleMetadata.getInt("totalDocuments");
    long totalBytes = bundleMetadata.getLong("totalBytes");
    return new BundleMetadata(bundleId, version, createTime, totalDocuments, totalBytes);
  }

  public BundledDocumentMetadata decodeBundledDocumentMetadata(JSONObject bundledDocumentMetadata)
      throws JSONException {
    DocumentKey key = DocumentKey.fromPath(decodeName(bundledDocumentMetadata.getString("name")));
    SnapshotVersion readTime = decodeSnapshotVersion(bundledDocumentMetadata.get("readTime"));
    boolean exists = bundledDocumentMetadata.optBoolean("exists", false);
    JSONArray queriesJson = bundledDocumentMetadata.optJSONArray("queries");
    List<String> queries = new ArrayList<>();
    // Technically, the queries array should never be missing, but we treat a missing and empty
    // array the same to avoid crashing during data import.
    if (queriesJson != null) {
      for (int i = 0; i < queriesJson.length(); ++i) {
        queries.add(queriesJson.getString(i));
      }
    }
    return new BundledDocumentMetadata(key, readTime, exists, queries);
  }

  BundleDocument decodeDocument(JSONObject document) throws JSONException {
    String name = document.getString("name");
    DocumentKey key = DocumentKey.fromPath(decodeName(name));
    SnapshotVersion updateTime = decodeSnapshotVersion(document.get("updateTime"));

    Value.Builder value = Value.newBuilder();
    decodeMapValue(value, document.getJSONObject("fields"));

    return new BundleDocument(
        MutableDocument.newFoundDocument(
            key, updateTime, ObjectValue.fromMap(value.getMapValue().getFieldsMap())));
  }

  private ResourcePath decodeName(String name) {
    ResourcePath resourcePath = ResourcePath.fromString(name);
    if (!remoteSerializer.isLocalResourceName(resourcePath)) {
      throw new IllegalArgumentException(
          "Resource name is not valid for current instance: " + name);
    }
    return resourcePath.popFirst(5);
  }

  private SnapshotVersion decodeSnapshotVersion(Object timestamp) throws JSONException {
    return new SnapshotVersion(decodeTimestamp(timestamp));
  }

  private BundledQuery decodeBundledQuery(JSONObject bundledQuery) throws JSONException {
    JSONObject structuredQuery = bundledQuery.getJSONObject("structuredQuery");
    verifyNoSelect(structuredQuery);

    ResourcePath parent = decodeName(bundledQuery.getString("parent"));
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
    @Nullable Bound startAt = decodeStartAtBound(structuredQuery.optJSONObject("startAt"));
    @Nullable Bound endAt = decodeEndAtBound(structuredQuery.optJSONObject("endAt"));

    verifyNoOffset(structuredQuery);
    int limit = decodeLimit(structuredQuery);
    Query.LimitType limitType = decodeLimitType(bundledQuery);

    return new BundledQuery(
        new Query(
                parent,
                collectionGroup,
                filters,
                orderBys,
                limit,
                // Not using `limitType` because bundled queries are what the backend sees,
                // and there is no limit_to_last for the backend.
                // Limit type is applied when the query is read back instead.
                Query.LimitType.LIMIT_TO_FIRST,
                startAt,
                endAt)
            .toTarget(),
        limitType);
  }

  private int decodeLimit(JSONObject structuredQuery) {
    JSONObject limit = structuredQuery.optJSONObject("limit");

    if (limit != null) {
      return limit.optInt("value", -1); // ProtobufJS
    } else {
      return structuredQuery.optInt("limit", -1); // Proto3 JSON
    }
  }

  private Bound decodeStartAtBound(@Nullable JSONObject bound) throws JSONException {
    if (bound != null) {
      boolean before = bound.optBoolean("before", false);
      List<Value> position = decodePosition(bound);
      return new Bound(position, before);
    }
    return null;
  }

  private Bound decodeEndAtBound(@Nullable JSONObject bound) throws JSONException {
    if (bound != null) {
      boolean before = bound.optBoolean("before", false);
      List<Value> position = decodePosition(bound);
      return new Bound(position, !before);
    }
    return null;
  }

  private List<Value> decodePosition(JSONObject bound) throws JSONException {
    List<Value> cursor = new ArrayList<>();
    JSONArray values = bound.optJSONArray("values");
    if (values != null) {
      for (int i = 0; i < values.length(); ++i) {
        cursor.add(decodeValue(values.getJSONObject(i)));
      }
    }
    return cursor;
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
    if (!compositeFilter.getString("op").equals("AND")) {
      throw new IllegalArgumentException(
          "The Android SDK only supports composite filters of type 'AND'");
    }

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
      builder.setIntegerValue(value.optLong("integerValue"));
    } else if (value.has("doubleValue")) {
      builder.setDoubleValue(value.optDouble("doubleValue"));
    } else if (value.has("timestampValue")) {
      decodeTimestamp(builder, value.get("timestampValue"));
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
            .setLatitude(geoPoint.optDouble("latitude"))
            .setLongitude(geoPoint.optDouble("longitude")));
  }

  private Timestamp decodeTimestamp(JSONObject timestamp) {
    return new Timestamp(timestamp.optLong("seconds"), timestamp.optInt("nanos"));
  }

  private Timestamp decodeTimestamp(String timestamp) {
    // This method is copied from com.google.protobuf.util.Timestamps. See:
    // https://chromium.googlesource.com/external/github.com/google/protobuf/+/HEAD/java/util/src/main/java/com/google/protobuf/util/Timestamps.java?autodive=0%2F#232

    try {
      int dayOffset = timestamp.indexOf('T');
      if (dayOffset == -1) {
        throw new IllegalArgumentException("Invalid timestamp: " + timestamp);
      }
      int timezoneOffsetPosition = timestamp.indexOf('Z', dayOffset);
      if (timezoneOffsetPosition == -1) {
        timezoneOffsetPosition = timestamp.indexOf('+', dayOffset);
      }
      if (timezoneOffsetPosition == -1) {
        timezoneOffsetPosition = timestamp.indexOf('-', dayOffset);
      }
      if (timezoneOffsetPosition == -1) {
        throw new IllegalArgumentException(
            "Invalid timestamp: Missing valid timezone offset: " + timestamp);
      }
      // Parse seconds and nanos.
      String timeValue = timestamp.substring(0, timezoneOffsetPosition);
      String secondValue = timeValue;
      String nanoValue = "";
      int pointPosition = timeValue.indexOf('.');
      if (pointPosition != -1) {
        secondValue = timeValue.substring(0, pointPosition);
        nanoValue = timeValue.substring(pointPosition + 1);
      }
      Date date = timestampFormat.parse(secondValue);
      long seconds = date.getTime() / MILLIS_PER_SECOND;
      int nanos = nanoValue.isEmpty() ? 0 : parseNanos(nanoValue);
      // Parse timezone offsets.
      if (timestamp.charAt(timezoneOffsetPosition) == 'Z') {
        if (timestamp.length() != timezoneOffsetPosition + 1) {
          throw new IllegalArgumentException(
              "Invalid timestamp: Invalid trailing data \""
                  + timestamp.substring(timezoneOffsetPosition)
                  + "\"");
        }
      } else {
        String offsetValue = timestamp.substring(timezoneOffsetPosition + 1);
        long offset = decodeTimezoneOffset(offsetValue);
        if (timestamp.charAt(timezoneOffsetPosition) == '+') {
          seconds -= offset;
        } else {
          seconds += offset;
        }
      }
      return new Timestamp(seconds, nanos);
    } catch (ParseException e) {
      throw new IllegalArgumentException("Failed to parse timestamp", e);
    }
  }

  private Timestamp decodeTimestamp(Object timestamp) throws JSONException {
    if (timestamp instanceof String) {
      return decodeTimestamp((String) timestamp);
    } else {
      if (!(timestamp instanceof JSONObject)) {
        throw new IllegalArgumentException(
            "Timestamps must be either ISO 8601-formatted strings or JSON objects");
      }
      return decodeTimestamp((JSONObject) timestamp);
    }
  }

  private void decodeTimestamp(Value.Builder builder, Object timestamp) throws JSONException {
    Timestamp decoded = decodeTimestamp(timestamp);
    builder.setTimestampValue(
        com.google.protobuf.Timestamp.newBuilder()
            .setSeconds(decoded.getSeconds())
            .setNanos(decoded.getNanoseconds()));
  }

  private static int parseNanos(String value) {
    int result = 0;
    for (int i = 0; i < 9; ++i) {
      result = result * 10;
      if (i < value.length()) {
        if (value.charAt(i) < '0' || value.charAt(i) > '9') {
          throw new IllegalArgumentException("Invalid nanoseconds: " + value);
        }
        result += value.charAt(i) - '0';
      }
    }
    return result;
  }

  private static long decodeTimezoneOffset(String value) {
    int pos = value.indexOf(':');
    if (pos == -1) {
      throw new IllegalArgumentException("Invalid offset value: " + value);
    }
    String hours = value.substring(0, pos);
    String minutes = value.substring(pos + 1);
    return (Long.parseLong(hours) * 60 + Long.parseLong(minutes)) * 60;
  }

  private FieldFilter.Operator decodeFieldFilterOperator(String operator) {
    return FieldFilter.Operator.valueOf(operator);
  }

  private void decodeUnaryFilter(List<Filter> result, JSONObject unaryFilter) throws JSONException {
    FieldPath fieldPath = decodeFieldReference(unaryFilter.getJSONObject("field"));
    String operator = unaryFilter.getString("op");

    switch (operator) {
      case "IS_NAN":
        result.add(FieldFilter.create(fieldPath, FieldFilter.Operator.EQUAL, Values.NAN_VALUE));
        break;
      case "IS_NULL":
        result.add(FieldFilter.create(fieldPath, FieldFilter.Operator.EQUAL, Values.NULL_VALUE));
        break;
      case "IS_NOT_NAN":
        result.add(FieldFilter.create(fieldPath, FieldFilter.Operator.NOT_EQUAL, Values.NAN_VALUE));
        break;
      case "IS_NOT_NULL":
        result.add(
            FieldFilter.create(fieldPath, FieldFilter.Operator.NOT_EQUAL, Values.NULL_VALUE));
        break;
      default:
        throw new IllegalArgumentException("Unexpected unary filter: " + operator);
    }
  }

  private FieldPath decodeFieldReference(JSONObject fieldReference) throws JSONException {
    return FieldPath.fromServerFormat(fieldReference.getString("fieldPath"));
  }

  private Query.LimitType decodeLimitType(JSONObject bundledQuery) {
    String limitType = bundledQuery.optString("limitType", "FIRST");
    if (limitType.equals("FIRST")) {
      return Query.LimitType.LIMIT_TO_FIRST;
    } else if (limitType.equals("LAST")) {
      return Query.LimitType.LIMIT_TO_LAST;
    } else {
      throw new IllegalArgumentException("Invalid limit type for bundle query: " + limitType);
    }
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
