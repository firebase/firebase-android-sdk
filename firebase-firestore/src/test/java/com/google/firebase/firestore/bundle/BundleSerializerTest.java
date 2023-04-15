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

import static com.google.firebase.firestore.testutil.TestUtil.bound;
import static com.google.firebase.firestore.testutil.TestUtil.filter;
import static com.google.firebase.firestore.testutil.TestUtil.key;
import static com.google.firebase.firestore.testutil.TestUtil.orderBy;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.core.Target;
import com.google.firebase.firestore.model.DatabaseId;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.MutableDocument;
import com.google.firebase.firestore.model.ObjectValue;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.remote.RemoteSerializer;
import com.google.firebase.firestore.testutil.TestUtil;
import com.google.firestore.v1.ArrayValue;
import com.google.firestore.v1.MapValue;
import com.google.firestore.v1.Value;
import com.google.protobuf.NullValue;
import com.google.protobuf.Timestamp;
import com.google.type.LatLng;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class BundleSerializerTest {
  // Note: This tests uses single-quoted JSON strings, which are accepted by
  // org.json.JSONObject.
  // While they are invalid JSON, they allow us to no use non-escaped quotes in
  // the test file.

  private static String TEST_PROJECT = "projects/project/databases/(default)/documents";
  private static String TEST_DOCUMENT = TEST_PROJECT + "/coll/doc";

  private BundleSerializer serializer =
      new BundleSerializer(new RemoteSerializer(DatabaseId.forProject("project")));

  // Value decoding tests

  @Test
  public void testDecodesNullValue() throws JSONException {
    String json = "{ nullValue: null }";
    Value proto = Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build();
    assertDecodesValue(json, proto);
  }

  @Test
  public void testDecodesBooleanValue() throws JSONException {
    List<Boolean> tests = asList(true, false);
    for (Boolean test : tests) {
      String json = "{ booleanValue: " + test + " }";
      Value proto = Value.newBuilder().setBooleanValue(test).build();
      assertDecodesValue(json, proto);
    }
  }

  @Test
  public void testDecodesIntegerValues() throws JSONException {
    List<Long> tests = asList(Long.MIN_VALUE, -100L, -1L, 0L, 1L, 100L, Long.MAX_VALUE);
    for (Long test : tests) {
      String json = "{ integerValue: " + test + " }";
      Value proto = Value.newBuilder().setIntegerValue(test).build();
      assertDecodesValue(json, proto);
    }
  }

  @Test
  public void testDecodesStringEncodedIntegerValues() throws JSONException {
    List<Long> tests = asList(Long.MIN_VALUE, -100L, -1L, 0L, 1L, 100L, Long.MAX_VALUE);
    for (Long test : tests) {
      String json = "{ integerValue: '" + test + "' }";
      Value proto = Value.newBuilder().setIntegerValue(test).build();
      assertDecodesValue(json, proto);
    }
  }

  @Test
  public void testDecodesDoubleValues() throws JSONException {
    List<Double> tests =
        asList(
            -Double.MAX_VALUE,
            Long.MAX_VALUE * -1.0 - 1.0,
            -2.0,
            -1.1,
            -1.0,
            -Double.MIN_VALUE,
            -Double.MIN_NORMAL,
            -0.0,
            0.0,
            Double.MIN_NORMAL,
            Double.MIN_VALUE,
            0.1,
            1.1,
            Long.MAX_VALUE * 1.0,
            Double.MAX_VALUE);
    for (Double test : tests) {
      String json = "{ doubleValue: " + test + " }";
      Value proto = Value.newBuilder().setDoubleValue(test).build();
      assertDecodesValue(json, proto);
    }
  }

  @Test
  public void testDecodesStringEncodedDoubleValues() throws JSONException {
    List<Double> tests = asList(Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
    for (Double test : tests) {
      String json = "{ doubleValue: '" + test + "' }";
      Value proto = Value.newBuilder().setDoubleValue(test).build();
      assertDecodesValue(json, proto);
    }
  }

  @Test
  public void testDecodesStringValues() throws JSONException {
    List<String> tests = asList("", "a", "abc def", "æ", "\0\ud7ff\ue000\uffff", "(╯°□°）╯︵ ┻━┻");
    for (String test : tests) {
      String json = "{ stringValue: '" + test + "' }";
      Value proto = Value.newBuilder().setStringValue(test).build();
      assertDecodesValue(json, proto);
    }
  }

  @Test
  public void testDecodesDateValues() throws JSONException {
    String[] json =
        new String[] {
          "'1970-01-01T00:00:00.000Z'",
          "{ }",
          "'1970-01-01T00:00:00.001Z'",
          "{ nanos: 1000000 }",
          "'2020-01-01T01:00:00.000Z'",
          "{ seconds: 1577840400 }",
          "'2020-01-01T01:00:00.001Z'",
          "{ seconds: 1577840400, nanos: 1000000 }",
          "'2020-01-01T01:02:00.001002Z'",
          "{ seconds: '1577840520', nanos: 1002000 }",
          "'2020-01-01T01:02:03.001002003Z'",
          "{ seconds: 1577840523, nanos: 1002003 }",
        };

    Timestamp[] timestamps =
        new Timestamp[] {
          Timestamp.newBuilder().setNanos(0).setSeconds(0).build(),
          Timestamp.newBuilder().setNanos(0).setSeconds(0).build(),
          Timestamp.newBuilder().setNanos(1000000).setSeconds(0).build(),
          Timestamp.newBuilder().setNanos(1000000).setSeconds(0).build(),
          Timestamp.newBuilder().setNanos(0).setSeconds(1577840400).build(),
          Timestamp.newBuilder().setNanos(0).setSeconds(1577840400).build(),
          Timestamp.newBuilder().setNanos(1000000).setSeconds(1577840400).build(),
          Timestamp.newBuilder().setNanos(1000000).setSeconds(1577840400).build(),
          Timestamp.newBuilder().setNanos(1002000).setSeconds(1577840520).build(),
          Timestamp.newBuilder().setNanos(1002000).setSeconds(1577840520).build(),
          Timestamp.newBuilder().setNanos(1002003).setSeconds(1577840523).build(),
          Timestamp.newBuilder().setNanos(1002003).setSeconds(1577840523).build()
        };

    for (int i = 0; i < json.length; i++) {
      assertDecodesValue(
          "{ timestampValue: " + json[i] + " }",
          Value.newBuilder().setTimestampValue(timestamps[i]).build());
    }
  }

  @Test
  public void testDecodesGeoPointValues() throws JSONException {
    String json = "{ geoPointValue: { latitude: 1.23, longitude: 4.56 } }";
    Value.Builder proto = Value.newBuilder();
    proto.setGeoPointValue(LatLng.newBuilder().setLatitude(1.23).setLongitude(4.56));

    assertDecodesValue(json, proto.build());
  }

  @Test
  public void testDecodesBlobValues() throws JSONException {
    String json = "{ bytesValue: 'AAECAw==' }";
    Value.Builder proto = Value.newBuilder();
    proto.setBytesValue(TestUtil.byteString(0, 1, 2, 3));

    assertDecodesValue(json, proto.build());
  }

  @Test
  public void testDecodesReferenceValues() throws JSONException {
    String json = "{ referenceValue: '" + TEST_DOCUMENT + "' }";
    Value.Builder proto = Value.newBuilder();
    proto.setReferenceValue(TEST_DOCUMENT);

    assertDecodesValue(json, proto.build());
  }

  @Test
  public void testDecodesArrayValues() throws JSONException {
    String json =
        "{\n"
            + "  arrayValue: {\n"
            + "    values: [ { booleanValue: true }, { stringValue: 'foo' } ]\n"
            + "}\n"
            + "}";
    ArrayValue.Builder builder = ArrayValue.newBuilder();
    builder
        .addValues(Value.newBuilder().setBooleanValue(true))
        .addValues(Value.newBuilder().setStringValue("foo"));

    Value.Builder proto = Value.newBuilder();
    proto.setArrayValue(builder);
    assertDecodesValue(json, proto.build());
  }

  @Test
  public void testDecodesNestedObjectValues() throws JSONException {
    String json =
        "{ mapValue: { fields: {\n"
            + "b: { booleanValue: true },\n"
            + "d: {\n"
            + "    doubleValue:\n"
            + Double.MAX_VALUE
            + "},\n"
            + "i: { integerValue: 1 },\n"
            + "n: { nullValue: null },\n"
            + "s: { stringValue: 'foo' },\n"
            + "a: { arrayValue: {\n"
            + "    values: [\n"
            + "      { integerValue: 2 },\n"
            + "      { stringValue: 'bar'},\n"
            + "      { mapValue: { fields: { b: { booleanValue: false } } } }\n"
            + "    ]\n"
            + "  }\n"
            + "},\n"
            + "o: {\n"
            + "  mapValue: {\n"
            + "    fields: {\n"
            + "      d: { integerValue: 100 },\n"
            + "      nested: {\n"
            + "         mapValue: {\n"
            + "           fields: {\n"
            + "             e: { integerValue:\n"
            + Long.MIN_VALUE
            + "            }\n"
            + "          }\n"
            + "        }\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "} } } }\n";

    MapValue.Builder inner =
        MapValue.newBuilder().putFields("b", Value.newBuilder().setBooleanValue(false).build());
    ArrayValue.Builder array =
        ArrayValue.newBuilder()
            .addValues(Value.newBuilder().setIntegerValue(2))
            .addValues(Value.newBuilder().setStringValue("bar"))
            .addValues(Value.newBuilder().setMapValue(inner));

    inner =
        MapValue.newBuilder()
            .putFields("e", Value.newBuilder().setIntegerValue(Long.MIN_VALUE).build());

    MapValue.Builder middle =
        MapValue.newBuilder()
            .putFields("d", Value.newBuilder().setIntegerValue(100).build())
            .putFields("nested", Value.newBuilder().setMapValue(inner).build());

    MapValue.Builder obj =
        MapValue.newBuilder()
            .putFields("b", Value.newBuilder().setBooleanValue(true).build())
            .putFields("d", Value.newBuilder().setDoubleValue(Double.MAX_VALUE).build())
            .putFields("i", Value.newBuilder().setIntegerValue(1).build())
            .putFields("n", Value.newBuilder().setNullValueValue(0).build())
            .putFields("s", Value.newBuilder().setStringValue("foo").build())
            .putFields("a", Value.newBuilder().setArrayValue(array).build())
            .putFields("o", Value.newBuilder().setMapValue(middle).build());

    Value proto = Value.newBuilder().setMapValue(obj).build();
    assertDecodesValue(json, proto);
  }

  // Query decoding tests

  @Test
  public void testDecodesCollectionQuery() throws JSONException {
    String json = "{ from: [ { collectionId: 'coll' } ] }";
    Query query = TestUtil.query("coll");
    assertDecodesNamedQuery(json, query);
  }

  @Test
  public void testDecodesCollectionGroupQuery() throws JSONException {
    String json = "{ from: [ { collectionId: 'coll', allDescendants: true } ] }";
    Query query = new Query(ResourcePath.EMPTY, "coll");
    assertDecodesNamedQuery(json, query);
  }

  @Test
  public void testDecodesNullFilter() throws JSONException {
    String json =
        "{\n"
            + "from: [ { collectionId: 'coll' } ],\n"
            + "where: { unaryFilter: { op: 'IS_NULL', field: { fieldPath: 'f1' } } }\n"
            + "}";
    Query query = TestUtil.query("coll").filter(filter("f1", "==", null));
    assertDecodesNamedQuery(json, query);
  }

  @Test
  public void testDecodesNotNullFilter() throws JSONException {
    String json =
        "{\n"
            + "from: [ { collectionId: 'coll' } ],\n"
            + "where: { unaryFilter: { op: 'IS_NOT_NULL', field: { fieldPath: 'f1' } } }\n"
            + "}";
    Query query = TestUtil.query("coll").filter(filter("f1", "!=", null));
    assertDecodesNamedQuery(json, query);
  }

  @Test
  public void testDecodesNaNFilter() throws JSONException {
    String json =
        "{\n"
            + "from: [ { collectionId: 'coll' } ],\n"
            + "where: { unaryFilter: { op: 'IS_NAN', field: { fieldPath: 'f1' } } }\n"
            + "}";
    Query query = TestUtil.query("coll").filter(filter("f1", "==", Double.NaN));
    assertDecodesNamedQuery(json, query);
  }

  @Test
  public void testDecodesNotNaNFilter() throws JSONException {
    String json =
        "{\n"
            + "from: [ { collectionId: 'coll' } ],\n"
            + "where: { unaryFilter: { op: 'IS_NOT_NAN', field: { fieldPath: 'f1' } } }\n"
            + "}";
    Query query = TestUtil.query("coll").filter(filter("f1", "!=", Double.NaN));
    assertDecodesNamedQuery(json, query);
  }

  @Test
  public void testDecodesLessThanFilter() throws JSONException {
    String json =
        "{\n"
            + "from: [ { collectionId: 'coll' } ],\n"
            + "where: {\n"
            + "  fieldFilter: {\n"
            + "    op: 'LESS_THAN', field: { fieldPath: 'f1' }, value: { stringValue: 'foo' }\n"
            + "   }\n"
            + "}"
            + "}";
    Query query = TestUtil.query("coll").filter(filter("f1", "<", "foo"));
    assertDecodesNamedQuery(json, query);
  }

  @Test
  public void testDecodesLessThanOrEqualFilter() throws JSONException {
    String json =
        "{\n"
            + "from: [ { collectionId: 'coll' } ],\n"
            + "where: {\n"
            + "  fieldFilter: {\n"
            + "    op: 'LESS_THAN_OR_EQUAL',\n"
            + "    field: { fieldPath: 'f1' },\n"
            + "    value: { stringValue: 'foo' }\n"
            + "  }\n"
            + "} }";
    Query query = TestUtil.query("coll").filter(filter("f1", "<=", "foo"));
    assertDecodesNamedQuery(json, query);
  }

  @Test
  public void testDecodesGreaterThanFilter() throws JSONException {
    String json =
        "{\n"
            + "from: [ { collectionId: 'coll' } ],\n"
            + "where: {\n"
            + "  fieldFilter: {\n"
            + "    op: 'GREATER_THAN', field: { fieldPath: 'f1' }, value: { stringValue: 'foo' }\n"
            + "  }\n"
            + "} }";
    Query query = TestUtil.query("coll").filter(filter("f1", ">", "foo"));
    assertDecodesNamedQuery(json, query);
  }

  @Test
  public void testDecodesGreaterThanOrEqualFilter() throws JSONException {
    String json =
        "{\n"
            + "from: [ { collectionId: 'coll' } ],\n"
            + "where: {\n"
            + "  fieldFilter: {\n"
            + "    op: 'GREATER_THAN_OR_EQUAL',\n"
            + "    field: { fieldPath: 'f1' },\n"
            + "    value: { stringValue: 'foo' }\n"
            + "  }\n"
            + "} }";
    Query query = TestUtil.query("coll").filter(filter("f1", ">=", "foo"));
    assertDecodesNamedQuery(json, query);
  }

  @Test
  public void testDecodesEqualFilter() throws JSONException {
    String json =
        "{\n"
            + "from: [ { collectionId: 'coll' } ],\n"
            + "where: {\n"
            + "  fieldFilter: {\n"
            + "    op: 'EQUAL', field: { fieldPath: 'f1' }, value: { stringValue: 'foo' }\n"
            + "  }\n"
            + "} }";
    Query query = TestUtil.query("coll").filter(filter("f1", "==", "foo"));
    assertDecodesNamedQuery(json, query);
  }

  @Test
  public void testDecodesNotEqualFilter() throws JSONException {
    String json =
        "{\n"
            + "from: [ { collectionId: 'coll' } ],\n"
            + "where: {\n"
            + "  fieldFilter: {\n"
            + "    op: 'NOT_EQUAL', field: { fieldPath: 'f1' }, value: { stringValue: 'foo' }"
            + " }\n"
            + "} }";
    Query query = TestUtil.query("coll").filter(filter("f1", "!=", "foo"));
    assertDecodesNamedQuery(json, query);
  }

  @Test
  public void testDecodesArrayContainsFilter() throws JSONException {
    String json =
        "{\n"
            + "from: [ { collectionId: 'coll' } ],\n"
            + "where: {\n"
            + "  fieldFilter: {\n"
            + "    op: 'ARRAY_CONTAINS',\n"
            + "    field: { fieldPath: 'f1' },\n"
            + "    value: { stringValue: 'foo' }\n"
            + "  }\n"
            + "} }";
    Query query = TestUtil.query("coll").filter(filter("f1", "array-contains", "foo"));
    assertDecodesNamedQuery(json, query);
  }

  @Test
  public void testDecodesInFilter() throws JSONException {
    String json =
        "{\n"
            + "from: [ { collectionId: 'coll' } ],\n"
            + "where: {\n"
            + "  fieldFilter: {\n"
            + "    op: 'IN',\n"
            + "    field: { fieldPath: 'f1' },\n"
            + "    value: { arrayValue: { values: [ { stringValue: 'foo' } ] } }\n"
            + "  }\n"
            + "} }";
    Query query =
        TestUtil.query("coll").filter(filter("f1", "in", Collections.singletonList("foo")));
    assertDecodesNamedQuery(json, query);
  }

  @Test
  public void testDecodesArrayContainsAnyFilter() throws JSONException {
    String json =
        "{\n"
            + "from: [ { collectionId: 'coll' } ],\n"
            + "where: {\n"
            + "  fieldFilter: {\n"
            + "    op: 'ARRAY_CONTAINS_ANY',\n"
            + "    field: { fieldPath: 'f1' },\n"
            + "    value: { arrayValue: { values: [ { stringValue: 'foo' } ] } }\n"
            + "  }\n"
            + "} }";
    Query query =
        TestUtil.query("coll")
            .filter(filter("f1", "array-contains-any", Collections.singletonList("foo")));
    assertDecodesNamedQuery(json, query);
  }

  @Test
  public void testDecodesNotInFilter() throws JSONException {
    String json =
        "{\n"
            + "from: [ { collectionId: 'coll' } ],\n"
            + "where: {\n"
            + "  fieldFilter: {\n"
            + "    op: 'NOT_IN',\n"
            + "    field: { fieldPath: 'f1' },\n"
            + "    value: { arrayValue: { values: [ { stringValue: 'foo' } ] } }\n"
            + "  }"
            + "} }";
    Query query =
        TestUtil.query("coll").filter(filter("f1", "not-in", Collections.singletonList("foo")));
    assertDecodesNamedQuery(json, query);
  }

  @Test
  public void testDecodesCompositeFilter() throws JSONException {
    String json =
        "{\n"
            + "from: [ { collectionId: 'coll' } ],\n"
            + "where: { compositeFilter: { op: 'AND', filters: [\n"
            + "  { fieldFilter:\n"
            + "    { op: 'EQUAL', field: { fieldPath: 'f1' }, value: { stringValue: 'foo' } }\n"
            + "  },\n"
            + "  { fieldFilter:\n"
            + "    { op: 'EQUAL', field: { fieldPath: 'f2' }, value: { stringValue: 'bar' } }\n"
            + "  }\n"
            + "]}}}";
    Query query =
        TestUtil.query("coll").filter(filter("f1", "==", "foo")).filter(filter("f2", "==", "bar"));
    assertDecodesNamedQuery(json, query);
  }

  @Test
  public void testDecodeOrderByQuery() throws JSONException {
    String json =
        "{\n"
            + "from: [ { collectionId: 'coll' } ],\n"
            + "orderBy: [\n"
            + "  { field: { fieldPath: 'f1' }},\n"
            + "  { field:  { fieldPath: 'f2' }, direction: 'ASCENDING' },\n"
            + "  { field:  { fieldPath: 'f3' }, direction: 'DESCENDING' }\n"
            + "]}";
    Query query =
        TestUtil.query("coll")
            .orderBy(orderBy("f1"))
            .orderBy(orderBy("f2", "asc"))
            .orderBy(orderBy("f3", "desc"));
    assertDecodesNamedQuery(json, query);
  }

  @Test
  public void testDecodesLimitQuery() throws JSONException {
    String[] json =
        new String[] {
          "{ from: [ { collectionId: 'coll' } ], limit: { value: 5 } }", // ProtobufJS
          "{ from: [ { collectionId: 'coll' } ], limit: 5 }" // Proto3 JSON
        };

    for (String encoded : json) {
      Query query = TestUtil.query("coll").limitToFirst(5);
      assertDecodesNamedQuery(encoded, query);
    }
  }

  @Test
  public void testDecodesLimitToLastQuery() throws JSONException {
    String queryJson =
        "{\n"
            + "  name: 'query-1',\n"
            + "  bundledQuery: {\n"
            + "    parent: '"
            + TEST_PROJECT
            + "',\n"
            + "    structuredQuery:\n"
            + "{ from: [ { collectionId: 'coll' } ], limit: {value: 5 } }"
            + ",\n"
            + "    limitType: 'LAST'\n"
            + "   },\n"
            + " readTime: '2020-01-01T00:00:01.000000001Z'\n"
            + "}";
    NamedQuery actualNamedQuery = serializer.decodeNamedQuery(new JSONObject(queryJson));

    // Note we use limitToFirst instead of limitToLast to avoid order reverse.
    // Because this is what is saved in bundle files.
    Query query = TestUtil.query("coll").limitToFirst(5);
    Target target = query.toTarget();
    BundledQuery bundledQuery = new BundledQuery(target, Query.LimitType.LIMIT_TO_LAST);
    NamedQuery expectedNamedQuery =
        new NamedQuery(
            "query-1",
            bundledQuery,
            new SnapshotVersion(new com.google.firebase.Timestamp(1577836801, 1)));

    assertEquals(expectedNamedQuery, actualNamedQuery);
  }

  @Test
  public void testDecodesStartAtQuery() throws JSONException {
    String json =
        "{\n"
            + "from: [ { collectionId: 'coll' } ],\n"
            + "orderBy: [ { field: { fieldPath: 'foo' } } ],\n"
            + "startAt: { values: [ { stringValue: 'bar' } ], before: true } }";
    Query query =
        TestUtil.query("coll").orderBy(orderBy("foo")).startAt(bound(/* inclusive= */ true, "bar"));
    assertDecodesNamedQuery(json, query);
  }

  @Test
  public void testDecodesEndBeforeQuery() throws JSONException {
    String json =
        "{\n"
            + "from: [ { collectionId: 'coll' } ],\n"
            + "orderBy: [ { field: { fieldPath: 'foo' } } ],\n"
            + "endAt: { values: [ { stringValue: 'bar' } ], before: true } }";
    Query query =
        TestUtil.query("coll").orderBy(orderBy("foo")).endAt(bound(/* inclusive= */ false, "bar"));
    assertDecodesNamedQuery(json, query);
  }

  @Test
  public void testDoesNotDecodeOffset() throws JSONException {
    String json = "{ from: [ { collectionId: 'coll' } ], offset: 5 }";
    Query query = TestUtil.query("coll");
    assertThrows(IllegalArgumentException.class, () -> assertDecodesNamedQuery(json, query));
  }

  @Test
  public void testDoesNotDecodeSelect() throws JSONException {
    String json = "{ from: [ { collectionId: 'coll' } ], select: [] }";
    Query query = TestUtil.query("coll");
    assertThrows(IllegalArgumentException.class, () -> assertDecodesNamedQuery(json, query));
  }

  @Test
  public void testDoesNotDecodeMissingCollection() throws JSONException {
    String json = "{ from: [ ] }";
    Query query = TestUtil.query("coll");
    assertThrows(IllegalArgumentException.class, () -> assertDecodesNamedQuery(json, query));
  }

  @Test
  public void testDoesNotDecodeMultipleCollections() throws JSONException {
    String json = "{ from: [ { collectionId: 'c1' }, { collectionId: 'c2' } ] }";
    Query query = TestUtil.query("coll");
    assertThrows(IllegalArgumentException.class, () -> assertDecodesNamedQuery(json, query));
  }

  // BundleMetadata tests

  @Test
  public void testDecodesBundleMetadata() throws JSONException {
    String json =
        "{\n"
            + "id: 'bundle-1',\n"
            + "version: 1,\n"
            + "createTime: { seconds: 2, nanos: 3 },\n"
            + "totalDocuments: 4,\n"
            + "totalBytes: 5\n"
            + "}";
    BundleMetadata expectedMetadata =
        new BundleMetadata(
            "bundle-1",
            /* schemaVersion= */ 1,
            new SnapshotVersion(new com.google.firebase.Timestamp(2, 3)),
            /* totalDocuments= */ 4,
            /* totalBytes= */ 5);
    BundleMetadata actualMetadata = serializer.decodeBundleMetadata(new JSONObject(json));
    assertEquals(expectedMetadata, actualMetadata);
  }

  // BundledDocumentMetadata tests

  @Test
  public void testDecodesBundledDocumentMetadata() throws JSONException {
    String json =
        "{\n"
            + "name: '"
            + TEST_DOCUMENT
            + "',\n"
            + "readTime: '2020-01-01T00:00:01.000000001Z',\n"
            + "exists: true,\n"
            + "queries: [ 'query-1', 'query-2' ]\n"
            + "}";
    BundledDocumentMetadata expectedMetadata =
        new BundledDocumentMetadata(
            key("coll/doc"),
            new SnapshotVersion(new com.google.firebase.Timestamp(1577836801, 1)),
            true,
            Arrays.asList("query-1", "query-2"));
    BundledDocumentMetadata actualMetadata =
        serializer.decodeBundledDocumentMetadata(new JSONObject(json));
    assertEquals(expectedMetadata, actualMetadata);
  }

  @Test
  public void testDecodesTargetWithoutImplicitOrderByOnName() throws JSONException {
    String json =
        "{\"name\":\"myNamedQuery\",\"bundledQuery\":{\"parent\":\"projects/project/databases"
            + "/(default)/documents\",\"structuredQuery\":{\"from\":[{\"collectionId\":\"foo\"}],"
            + "\"limit\":{\"value\":10}},\"limitType\":\"FIRST\"},"
            + "\"readTime\":{\"seconds\":\"1679674432\",\"nanos\":579934000}}";
    NamedQuery query = serializer.decodeNamedQuery(new JSONObject(json));
    assertEquals(
        TestUtil.query("foo").limitToFirst(10).toTarget(), query.getBundledQuery().getTarget());
    assertEquals(Query.LimitType.LIMIT_TO_FIRST, query.getBundledQuery().getLimitType());
  }

  @Test
  public void testDecodesLimitToLastTargetWithoutImplicitOrderByOnName() throws JSONException {
    String json =
        "{\"name\":\"myNamedQuery\",\"bundledQuery\":{\"parent\":\"projects/project/databases"
            + "/(default)/documents\",\"structuredQuery\":{\"from\":[{\"collectionId\":\"foo\"}],"
            + "\"limit\":{\"value\":10}},\"limitType\":\"LAST\"},"
            + "\"readTime\":{\"seconds\":\"1679674432\",\"nanos\":579934000}}";
    NamedQuery query = serializer.decodeNamedQuery(new JSONObject(json));
    assertEquals(
        // Note that `limitToFirst(10)` is expected.
        TestUtil.query("foo").limitToFirst(10).toTarget(), query.getBundledQuery().getTarget());
    assertEquals(Query.LimitType.LIMIT_TO_LAST, query.getBundledQuery().getLimitType());
  }

  private void assertDecodesValue(String json, Value proto) throws JSONException {
    String documentJson =
        "{\n"
            + "  name: '"
            + TEST_DOCUMENT
            + "',\n"
            + "  fields: {\n"
            + "    foo:\n"
            + json
            + "\n"
            + "  },\n"
            + "  crateTime: '2020-01-01T00:00:01.000000001Z',\n"
            + "  updateTime: '2020-01-01T00:00:02.000000002Z'\n"
            + "}";
    BundleDocument actualDocument = serializer.decodeDocument(new JSONObject(documentJson));
    BundleDocument expectedDocument =
        new BundleDocument(
            MutableDocument.newFoundDocument(
                DocumentKey.fromName(TEST_DOCUMENT),
                new SnapshotVersion(new com.google.firebase.Timestamp(1577836802, 2)),
                new ObjectValue(
                    Value.newBuilder()
                        .setMapValue(MapValue.newBuilder().putFields("foo", proto))
                        .build())));

    assertEquals(expectedDocument, actualDocument);
  }

  private void assertDecodesNamedQuery(String json, Query query) throws JSONException {
    String queryJson =
        "{\n"
            + "  name: 'query-1',\n"
            + "  bundledQuery: {\n"
            + "    parent: '"
            + TEST_PROJECT
            + "',\n"
            + "    structuredQuery:\n"
            + json
            + ",\n"
            + "    limitType: '"
            + (query.getLimitType().equals(Query.LimitType.LIMIT_TO_LAST) ? "LAST" : "FIRST")
            + "'\n"
            + "   },\n"
            + " readTime: '2020-01-01T00:00:01.000000001Z'\n"
            + "}";
    NamedQuery actualNamedQuery = serializer.decodeNamedQuery(new JSONObject(queryJson));

    Target target = query.toTarget();
    BundledQuery bundledQuery =
        new BundledQuery(
            target,
            query.getLimitType().equals(Query.LimitType.LIMIT_TO_LAST)
                ? Query.LimitType.LIMIT_TO_LAST
                : Query.LimitType.LIMIT_TO_FIRST);
    NamedQuery expectedNamedQuery =
        new NamedQuery(
            "query-1",
            bundledQuery,
            new SnapshotVersion(new com.google.firebase.Timestamp(1577836801, 1)));

    assertEquals(expectedNamedQuery, actualNamedQuery);
  }
}
