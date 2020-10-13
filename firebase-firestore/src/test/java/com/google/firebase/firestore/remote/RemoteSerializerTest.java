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

package com.google.firebase.firestore.remote;

import static com.google.firebase.firestore.testutil.TestUtil.deleteMutation;
import static com.google.firebase.firestore.testutil.TestUtil.deletedDoc;
import static com.google.firebase.firestore.testutil.TestUtil.doc;
import static com.google.firebase.firestore.testutil.TestUtil.field;
import static com.google.firebase.firestore.testutil.TestUtil.filter;
import static com.google.firebase.firestore.testutil.TestUtil.key;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.testutil.TestUtil.orderBy;
import static com.google.firebase.firestore.testutil.TestUtil.patchMutation;
import static com.google.firebase.firestore.testutil.TestUtil.query;
import static com.google.firebase.firestore.testutil.TestUtil.ref;
import static com.google.firebase.firestore.testutil.TestUtil.setMutation;
import static com.google.firebase.firestore.testutil.TestUtil.transformMutation;
import static com.google.firebase.firestore.testutil.TestUtil.verifyMutation;
import static com.google.firebase.firestore.testutil.TestUtil.wrap;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.core.ArrayContainsAnyFilter;
import com.google.firebase.firestore.core.Bound;
import com.google.firebase.firestore.core.FieldFilter;
import com.google.firebase.firestore.core.InFilter;
import com.google.firebase.firestore.core.KeyFieldFilter;
import com.google.firebase.firestore.core.NotInFilter;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.local.QueryPurpose;
import com.google.firebase.firestore.local.TargetData;
import com.google.firebase.firestore.model.DatabaseId;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.ObjectValue;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.model.Values;
import com.google.firebase.firestore.model.mutation.Mutation;
import com.google.firebase.firestore.remote.WatchChange.WatchTargetChange;
import com.google.firebase.firestore.remote.WatchChange.WatchTargetChangeType;
import com.google.firebase.firestore.testutil.TestUtil;
import com.google.firestore.v1.ArrayValue;
import com.google.firestore.v1.Cursor;
import com.google.firestore.v1.Document;
import com.google.firestore.v1.DocumentChange;
import com.google.firestore.v1.DocumentDelete;
import com.google.firestore.v1.DocumentMask;
import com.google.firestore.v1.DocumentRemove;
import com.google.firestore.v1.DocumentTransform;
import com.google.firestore.v1.ListenResponse;
import com.google.firestore.v1.MapValue;
import com.google.firestore.v1.Precondition;
import com.google.firestore.v1.StructuredQuery;
import com.google.firestore.v1.StructuredQuery.CollectionSelector;
import com.google.firestore.v1.StructuredQuery.CompositeFilter;
import com.google.firestore.v1.StructuredQuery.Direction;
import com.google.firestore.v1.StructuredQuery.FieldFilter.Operator;
import com.google.firestore.v1.StructuredQuery.FieldReference;
import com.google.firestore.v1.StructuredQuery.Filter;
import com.google.firestore.v1.StructuredQuery.Order;
import com.google.firestore.v1.StructuredQuery.UnaryFilter;
import com.google.firestore.v1.Target;
import com.google.firestore.v1.Target.DocumentsTarget;
import com.google.firestore.v1.Target.QueryTarget;
import com.google.firestore.v1.TargetChange;
import com.google.firestore.v1.TargetChange.TargetChangeType;
import com.google.firestore.v1.Value;
import com.google.firestore.v1.Write;
import com.google.protobuf.ByteString;
import com.google.protobuf.Int32Value;
import com.google.protobuf.NullValue;
import com.google.protobuf.Timestamp;
import com.google.type.LatLng;
import io.grpc.Status;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Add a test for serializer. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public final class RemoteSerializerTest {
  private DatabaseId databaseId;
  private RemoteSerializer serializer;

  @Before
  public void setUp() {
    databaseId = DatabaseId.forDatabase("p", "d");
    serializer = new RemoteSerializer(databaseId);
  }

  private void assertRoundTrip(Value actual, Value proto, Value.ValueTypeCase typeCase) {
    assertEquals(typeCase, actual.getValueTypeCase());
    assertEquals(proto, actual);
    assertTrue(Values.equals(actual, proto));
  }

  @Test
  public void testEncodesNull() {
    Value value = wrap(null);
    Value proto = Value.newBuilder().setNullValueValue(0).build();
    assertRoundTrip(value, proto, Value.ValueTypeCase.NULL_VALUE);
  }

  @Test
  public void testEncodesBoolean() {
    List<Boolean> tests = asList(true, false);
    for (Boolean test : tests) {
      Value value = wrap(test);
      Value proto = Value.newBuilder().setBooleanValue(test).build();
      assertRoundTrip(value, proto, Value.ValueTypeCase.BOOLEAN_VALUE);
    }
  }

  @Test
  public void testEncodesIntegers() {
    List<Long> tests = asList(Long.MIN_VALUE, -100L, -1L, 0L, 1L, 100L, Long.MAX_VALUE);
    for (Long test : tests) {
      Value value = wrap(test);
      Value proto = Value.newBuilder().setIntegerValue(test).build();
      assertRoundTrip(value, proto, Value.ValueTypeCase.INTEGER_VALUE);
    }
  }

  @Test
  public void testEncodesDoubles() {
    List<Double> tests =
        asList(
            Double.NEGATIVE_INFINITY,
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
            Double.MAX_VALUE,
            Double.POSITIVE_INFINITY);
    for (Double test : tests) {
      Value value = wrap(test);
      Value proto = Value.newBuilder().setDoubleValue(test).build();
      assertRoundTrip(value, proto, Value.ValueTypeCase.DOUBLE_VALUE);
    }
  }

  @Test
  public void testEncodesStrings() {
    List<String> tests = asList("", "a", "abc def", "æ", "\0\ud7ff\ue000\uffff", "(╯°□°）╯︵ ┻━┻");
    for (String test : tests) {
      Value value = wrap(test);
      Value proto = Value.newBuilder().setStringValue(test).build();
      assertRoundTrip(value, proto, Value.ValueTypeCase.STRING_VALUE);
    }
  }

  @Test
  public void testEncodesDates() {
    Calendar date1 = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    date1.set(2016, 0, 2, 10, 20, 50);
    date1.set(Calendar.MILLISECOND, 500);

    Calendar date2 = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    date2.set(2016, 5, 17, 10, 50, 15);
    date2.set(Calendar.MILLISECOND, 0);

    List<Date> tests = asList(date1.getTime(), date2.getTime());

    Timestamp ts1 = Timestamp.newBuilder().setNanos(500000000).setSeconds(1451730050).build();

    Timestamp ts2 = Timestamp.newBuilder().setNanos(0).setSeconds(1466160615).build();
    List<Value> expected =
        asList(
            Value.newBuilder().setTimestampValue(ts1).build(),
            Value.newBuilder().setTimestampValue(ts2).build());

    for (int i = 0; i < tests.size(); i++) {
      Value value = wrap(tests.get(i));
      assertRoundTrip(value, expected.get(i), Value.ValueTypeCase.TIMESTAMP_VALUE);
    }
  }

  @Test
  public void testEncodesGeoPoints() {
    Value geoPoint = wrap(new GeoPoint(1.23, 4.56));
    Value.Builder proto = Value.newBuilder();
    proto.setGeoPointValue(LatLng.newBuilder().setLatitude(1.23).setLongitude(4.56));

    assertRoundTrip(geoPoint, proto.build(), Value.ValueTypeCase.GEO_POINT_VALUE);
  }

  @Test
  public void testEncodesBlobs() {
    Value blob = wrap(TestUtil.blob(0, 1, 2, 3));
    Value.Builder proto = Value.newBuilder();
    proto.setBytesValue(TestUtil.byteString(0, 1, 2, 3));

    assertRoundTrip(blob, proto.build(), Value.ValueTypeCase.BYTES_VALUE);
  }

  @Test
  public void testEncodesReferences() {
    DocumentReference value = ref("foo/bar");
    Value ref = wrap(value);
    Value.Builder proto = Value.newBuilder();
    proto.setReferenceValue("projects/project/databases/(default)/documents/foo/bar");

    assertRoundTrip(ref, proto.build(), Value.ValueTypeCase.REFERENCE_VALUE);
  }

  @Test
  public void testEncodeArrays() {
    Value model = wrap(asList(true, "foo"));
    ArrayValue.Builder builder = ArrayValue.newBuilder();
    builder
        .addValues(Value.newBuilder().setBooleanValue(true))
        .addValues(Value.newBuilder().setStringValue("foo"));

    Value.Builder proto = Value.newBuilder();
    proto.setArrayValue(builder);
    assertRoundTrip(model, proto.build(), Value.ValueTypeCase.ARRAY_VALUE);
  }

  @Test
  public void testEncodesNestedObjects() {
    ObjectValue model =
        TestUtil.wrapObject(
            map(
                "b",
                true,
                "d",
                Double.MAX_VALUE,
                "i",
                1,
                "n",
                null,
                "s",
                "foo",
                "a",
                asList(2, "bar", map("b", false)),
                "o",
                map("d", 100, "nested", map("e", Long.MIN_VALUE))));

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
    assertRoundTrip(model.getProto(), proto, Value.ValueTypeCase.MAP_VALUE);
  }

  @Test
  public void testEncodeDeleteMutation() {
    Mutation mutation = deleteMutation("docs/1");

    Write expected =
        Write.newBuilder().setDelete("projects/p/databases/d/documents/docs/1").build();
    assertRoundTripForMutation(mutation, expected);
  }

  @Test
  public void testEncodeVerifyMutation() {
    Mutation mutation = verifyMutation("docs/1", 4);

    Write expected =
        Write.newBuilder()
            .setVerify("projects/p/databases/d/documents/docs/1")
            .setCurrentDocument(
                Precondition.newBuilder()
                    .setUpdateTime(Timestamp.newBuilder().setNanos(4000).build())
                    .build())
            .build();
    assertRoundTripForMutation(mutation, expected);
  }

  @Test
  public void testEncodeSetMutation() {
    Mutation mutation = setMutation("docs/1", map("key", "value"));

    Write expected =
        Write.newBuilder()
            .setUpdate(
                Document.newBuilder()
                    .setName("projects/p/databases/d/documents/docs/1")
                    .putFields("key", Value.newBuilder().setStringValue("value").build()))
            .build();

    assertRoundTripForMutation(mutation, expected);
  }

  @Test
  public void testEncodesPatchMutation() {
    Mutation mutation = patchMutation("docs/1", map("key", "value", "key2", true));

    Write expected =
        Write.newBuilder()
            .setUpdate(
                Document.newBuilder()
                    .setName("projects/p/databases/d/documents/docs/1")
                    .putFields("key", Value.newBuilder().setStringValue("value").build())
                    .putFields("key2", Value.newBuilder().setBooleanValue(true).build()))
            .setUpdateMask(DocumentMask.newBuilder().addAllFieldPaths(asList("key", "key2")))
            .setCurrentDocument(Precondition.newBuilder().setExists(true))
            .build();

    assertRoundTripForMutation(mutation, expected);
  }

  @Test
  public void testEncodesPatchMutationWithFieldMask() {
    Mutation mutation =
        patchMutation("docs/1", map("key", "value", "key2", true), asList(field("key")));

    Write expected =
        Write.newBuilder()
            .setUpdate(
                Document.newBuilder()
                    .setName("projects/p/databases/d/documents/docs/1")
                    .putFields("key", Value.newBuilder().setStringValue("value").build())
                    .putFields("key2", Value.newBuilder().setBooleanValue(true).build()))
            .setUpdateMask(DocumentMask.newBuilder().addFieldPaths("key"))
            .build();

    assertRoundTripForMutation(mutation, expected);
  }

  @Test
  public void testEncodesServerTimestampTransformMutation() {
    Mutation mutation =
        transformMutation(
            "docs/1",
            map(
                "a",
                com.google.firebase.firestore.FieldValue.serverTimestamp(),
                "bar.baz",
                com.google.firebase.firestore.FieldValue.serverTimestamp()));

    Write expected =
        Write.newBuilder()
            .setTransform(
                DocumentTransform.newBuilder()
                    .setDocument("projects/p/databases/d/documents/docs/1")
                    .addFieldTransforms(
                        DocumentTransform.FieldTransform.newBuilder()
                            .setFieldPath("a")
                            .setSetToServerValue(
                                DocumentTransform.FieldTransform.ServerValue.REQUEST_TIME))
                    .addFieldTransforms(
                        DocumentTransform.FieldTransform.newBuilder()
                            .setFieldPath("bar.baz")
                            .setSetToServerValue(
                                DocumentTransform.FieldTransform.ServerValue.REQUEST_TIME)))
            .setCurrentDocument(Precondition.newBuilder().setExists(true))
            .build();

    assertRoundTripForMutation(mutation, expected);
  }

  @Test
  public void testEncodesArrayTransformMutations() {
    Mutation mutation =
        transformMutation(
            "docs/1",
            map(
                "a", com.google.firebase.firestore.FieldValue.arrayUnion("a", 2),
                "bar.baz", com.google.firebase.firestore.FieldValue.arrayRemove(map("x", 1))));

    Write expected =
        Write.newBuilder()
            .setTransform(
                DocumentTransform.newBuilder()
                    .setDocument("projects/p/databases/d/documents/docs/1")
                    .addFieldTransforms(
                        DocumentTransform.FieldTransform.newBuilder()
                            .setFieldPath("a")
                            .setAppendMissingElements(
                                ArrayValue.newBuilder().addValues(wrap("a")).addValues(wrap(2))))
                    .addFieldTransforms(
                        DocumentTransform.FieldTransform.newBuilder()
                            .setFieldPath("bar.baz")
                            .setRemoveAllFromArray(
                                ArrayValue.newBuilder().addValues(wrap(map("x", 1))))))
            .setCurrentDocument(Precondition.newBuilder().setExists(true))
            .build();

    assertRoundTripForMutation(mutation, expected);
  }

  private void assertRoundTripForMutation(Mutation mutation, Write proto) {
    Write actualProto = serializer.encodeMutation(mutation);
    assertEquals(proto, actualProto);

    Mutation actualMutation = serializer.decodeMutation(proto);
    assertEquals(mutation, actualMutation);
  }

  private Order defaultKeyOrder() {
    return Order.newBuilder()
        .setField(FieldReference.newBuilder().setFieldPath(DocumentKey.KEY_FIELD_NAME))
        .setDirection(Direction.ASCENDING)
        .build();
  }

  @Test
  public void testEncodesListenRequestLabels() {
    Query query = query("collection/key");
    TargetData targetData = new TargetData(query.toTarget(), 2, 3, QueryPurpose.LISTEN);

    Map<String, String> result = serializer.encodeListenRequestLabels(targetData);
    assertNull(result);

    targetData = new TargetData(query.toTarget(), 2, 3, QueryPurpose.LIMBO_RESOLUTION);
    result = serializer.encodeListenRequestLabels(targetData);
    assertEquals(map("goog-listen-tags", "limbo-document"), result);

    targetData = new TargetData(query.toTarget(), 2, 3, QueryPurpose.EXISTENCE_FILTER_MISMATCH);
    result = serializer.encodeListenRequestLabels(targetData);
    assertEquals(map("goog-listen-tags", "existence-filter-mismatch"), result);
  }

  @Test
  public void testEncodesFirstLevelKeyQueries() {
    Query q = Query.atPath(ResourcePath.fromString("docs/1"));
    Target actual =
        serializer.encodeTarget(new TargetData(q.toTarget(), 1, 2, QueryPurpose.LISTEN));

    DocumentsTarget.Builder docs =
        DocumentsTarget.newBuilder().addDocuments("projects/p/databases/d/documents/docs/1");
    Target expected =
        Target.newBuilder()
            .setDocuments(docs)
            .setTargetId(1)
            .setResumeToken(ByteString.EMPTY)
            .build();

    assertEquals(expected, actual);
    assertEquals(
        serializer.decodeDocumentsTarget(serializer.encodeDocumentsTarget(q.toTarget())),
        q.toTarget());
  }

  @Test
  public void testEncodesFirstLevelAncestorQueries() {
    Query q = Query.atPath(ResourcePath.fromString("messages"));
    Target actual = serializer.encodeTarget(wrapTargetData(q));

    StructuredQuery.Builder structuredQueryBuilder =
        StructuredQuery.newBuilder()
            .addFrom(CollectionSelector.newBuilder().setCollectionId("messages"))
            .addOrderBy(defaultKeyOrder());
    QueryTarget.Builder queryBuilder =
        QueryTarget.newBuilder()
            .setParent("projects/p/databases/d/documents")
            .setStructuredQuery(structuredQueryBuilder);
    Target expected =
        Target.newBuilder()
            .setQuery(queryBuilder)
            .setTargetId(1)
            .setResumeToken(ByteString.EMPTY)
            .build();

    assertEquals(expected, actual);
    assertEquals(
        serializer.decodeQueryTarget(serializer.encodeQueryTarget(q.toTarget())), q.toTarget());
  }

  @Test
  public void testEncodesNestedAncestorQueries() {
    Query q = Query.atPath(ResourcePath.fromString("rooms/1/messages/10/attachments"));
    Target actual = serializer.encodeTarget(wrapTargetData(q));

    StructuredQuery.Builder structuredQueryBuilder =
        StructuredQuery.newBuilder()
            .addFrom(CollectionSelector.newBuilder().setCollectionId("attachments"))
            .addOrderBy(defaultKeyOrder());
    QueryTarget queryBuilder =
        QueryTarget.newBuilder()
            .setParent("projects/p/databases/d/documents/rooms/1/messages/10")
            .setStructuredQuery(structuredQueryBuilder)
            .build();
    Target expected =
        Target.newBuilder()
            .setQuery(queryBuilder)
            .setTargetId(1)
            .setResumeToken(ByteString.EMPTY)
            .build();

    assertEquals(expected, actual);
    assertEquals(
        serializer.decodeQueryTarget(serializer.encodeQueryTarget(q.toTarget())), q.toTarget());
  }

  @Test
  public void testEncodesSingleFilterAtFirstLevelCollections() {
    Query q = Query.atPath(ResourcePath.fromString("docs")).filter(filter("prop", "<", 42));
    Target actual = serializer.encodeTarget(wrapTargetData(q));

    StructuredQuery.Builder structuredQueryBuilder =
        StructuredQuery.newBuilder()
            .addFrom(CollectionSelector.newBuilder().setCollectionId("docs"))
            .setWhere(
                Filter.newBuilder()
                    .setFieldFilter(
                        StructuredQuery.FieldFilter.newBuilder()
                            .setField(FieldReference.newBuilder().setFieldPath("prop"))
                            .setOp(Operator.LESS_THAN)
                            .setValue(Value.newBuilder().setIntegerValue(42))))
            .addOrderBy(
                Order.newBuilder()
                    .setField(FieldReference.newBuilder().setFieldPath("prop"))
                    .setDirection(Direction.ASCENDING))
            .addOrderBy(defaultKeyOrder());
    QueryTarget.Builder queryBuilder =
        QueryTarget.newBuilder()
            .setParent("projects/p/databases/d/documents")
            .setStructuredQuery(structuredQueryBuilder);
    Target expected =
        Target.newBuilder()
            .setQuery(queryBuilder)
            .setTargetId(1)
            .setResumeToken(ByteString.EMPTY)
            .build();

    assertEquals(expected, actual);
    assertEquals(
        serializer.decodeQueryTarget(serializer.encodeQueryTarget(q.toTarget())), q.toTarget());
  }

  @Test
  public void testEncodesMultipleFiltersOnDeeperCollections() {
    Query q =
        Query.atPath(ResourcePath.fromString("rooms/1/messages/10/attachments"))
            .filter(filter("prop", "<", 42))
            .filter(filter("author", "==", "dimond"))
            .filter(filter("tags", "array-contains", "pending"));
    Target actual = serializer.encodeTarget(wrapTargetData(q));

    StructuredQuery.Builder structuredQueryBuilder =
        StructuredQuery.newBuilder()
            .addFrom(CollectionSelector.newBuilder().setCollectionId("attachments"))
            .setWhere(
                Filter.newBuilder()
                    .setCompositeFilter(
                        StructuredQuery.CompositeFilter.newBuilder()
                            .setOp(CompositeFilter.Operator.AND)
                            .addFilters(
                                Filter.newBuilder()
                                    .setFieldFilter(
                                        StructuredQuery.FieldFilter.newBuilder()
                                            .setField(
                                                FieldReference.newBuilder().setFieldPath("prop"))
                                            .setOp(Operator.LESS_THAN)
                                            .setValue(Value.newBuilder().setIntegerValue(42))))
                            .addFilters(
                                Filter.newBuilder()
                                    .setFieldFilter(
                                        StructuredQuery.FieldFilter.newBuilder()
                                            .setField(
                                                FieldReference.newBuilder().setFieldPath("author"))
                                            .setOp(Operator.EQUAL)
                                            .setValue(Value.newBuilder().setStringValue("dimond"))))
                            .addFilters(
                                Filter.newBuilder()
                                    .setFieldFilter(
                                        StructuredQuery.FieldFilter.newBuilder()
                                            .setField(
                                                FieldReference.newBuilder().setFieldPath("tags"))
                                            .setOp(Operator.ARRAY_CONTAINS)
                                            .setValue(
                                                Value.newBuilder().setStringValue("pending"))))))
            .addOrderBy(
                Order.newBuilder()
                    .setField(FieldReference.newBuilder().setFieldPath("prop"))
                    .setDirection(Direction.ASCENDING))
            .addOrderBy(defaultKeyOrder());
    QueryTarget.Builder queryBuilder =
        QueryTarget.newBuilder()
            .setParent("projects/p/databases/d/documents/rooms/1/messages/10")
            .setStructuredQuery(structuredQueryBuilder);
    Target expected =
        Target.newBuilder()
            .setQuery(queryBuilder)
            .setTargetId(1)
            .setResumeToken(ByteString.EMPTY)
            .build();

    assertEquals(expected, actual);
    assertEquals(
        serializer.decodeQueryTarget(serializer.encodeQueryTarget(q.toTarget())), q.toTarget());
  }

  @Test
  public void testInSerialization() {
    FieldFilter inputFilter = filter("field", "in", asList(42));
    StructuredQuery.Filter apiFilter = serializer.encodeUnaryOrFieldFilter(inputFilter);

    ArrayValue.Builder inFilterValue =
        ArrayValue.newBuilder().addValues(Value.newBuilder().setIntegerValue(42));
    StructuredQuery.Filter expectedFilter =
        Filter.newBuilder()
            .setFieldFilter(
                StructuredQuery.FieldFilter.newBuilder()
                    .setField(FieldReference.newBuilder().setFieldPath("field"))
                    .setOp(Operator.IN)
                    .setValue(Value.newBuilder().setArrayValue(inFilterValue))
                    .build())
            .build();

    assertEquals(expectedFilter, apiFilter);
    FieldFilter roundTripped = serializer.decodeFieldFilter(apiFilter.getFieldFilter());
    assertEquals(roundTripped, inputFilter);
    assertTrue(roundTripped instanceof InFilter);
  }

  @Test
  public void testNotEqualSerialization() {
    FieldFilter inputFilter = filter("field", "!=", 42);
    StructuredQuery.Filter apiFilter = serializer.encodeUnaryOrFieldFilter(inputFilter);

    StructuredQuery.Filter expectedFilter =
        Filter.newBuilder()
            .setFieldFilter(
                StructuredQuery.FieldFilter.newBuilder()
                    .setField(FieldReference.newBuilder().setFieldPath("field"))
                    .setOp(Operator.NOT_EQUAL)
                    .setValue(Value.newBuilder().setIntegerValue(42))
                    .build())
            .build();

    assertEquals(expectedFilter, apiFilter);
    FieldFilter roundTripped = serializer.decodeFieldFilter(apiFilter.getFieldFilter());
    assertEquals(roundTripped, inputFilter);
  }

  @Test
  public void testNotInSerialization() {
    FieldFilter inputFilter = filter("field", "not-in", asList(42));
    StructuredQuery.Filter apiFilter = serializer.encodeUnaryOrFieldFilter(inputFilter);

    ArrayValue.Builder notInFilterValue =
        ArrayValue.newBuilder().addValues(Value.newBuilder().setIntegerValue(42));
    StructuredQuery.Filter expectedFilter =
        Filter.newBuilder()
            .setFieldFilter(
                StructuredQuery.FieldFilter.newBuilder()
                    .setField(FieldReference.newBuilder().setFieldPath("field"))
                    .setOp(Operator.NOT_IN)
                    .setValue(Value.newBuilder().setArrayValue(notInFilterValue))
                    .build())
            .build();

    assertEquals(expectedFilter, apiFilter);
    FieldFilter roundTripped = serializer.decodeFieldFilter(apiFilter.getFieldFilter());
    assertEquals(roundTripped, inputFilter);
    assertTrue(roundTripped instanceof NotInFilter);
  }

  @Test
  public void testNotInWithNullSerialization() {
    List<Object> nullArray = new ArrayList<>();
    nullArray.add(null);
    FieldFilter inputFilter = filter("field", "not-in", nullArray);
    StructuredQuery.Filter apiFilter = serializer.encodeUnaryOrFieldFilter(inputFilter);

    ArrayValue.Builder notInFilterValue =
        ArrayValue.newBuilder().addValues(Value.newBuilder().setNullValue(NullValue.NULL_VALUE));
    StructuredQuery.Filter expectedFilter =
        Filter.newBuilder()
            .setFieldFilter(
                StructuredQuery.FieldFilter.newBuilder()
                    .setField(FieldReference.newBuilder().setFieldPath("field"))
                    .setOp(Operator.NOT_IN)
                    .setValue(Value.newBuilder().setArrayValue(notInFilterValue))
                    .build())
            .build();

    assertEquals(expectedFilter, apiFilter);
    FieldFilter roundTripped = serializer.decodeFieldFilter(apiFilter.getFieldFilter());
    assertEquals(roundTripped, inputFilter);
    assertTrue(roundTripped instanceof NotInFilter);
  }

  @Test
  public void testArrayContainsAnySerialization() {
    FieldFilter inputFilter = filter("field", "array-contains-any", asList(42));
    StructuredQuery.Filter apiFilter = serializer.encodeUnaryOrFieldFilter(inputFilter);

    ArrayValue.Builder arrayContainsAnyFilterValue =
        ArrayValue.newBuilder().addValues(Value.newBuilder().setIntegerValue(42));
    StructuredQuery.Filter expectedFilter =
        Filter.newBuilder()
            .setFieldFilter(
                StructuredQuery.FieldFilter.newBuilder()
                    .setField(FieldReference.newBuilder().setFieldPath("field"))
                    .setOp(Operator.ARRAY_CONTAINS_ANY)
                    .setValue(Value.newBuilder().setArrayValue(arrayContainsAnyFilterValue))
                    .build())
            .build();

    assertEquals(expectedFilter, apiFilter);
    FieldFilter roundTripped = serializer.decodeFieldFilter(apiFilter.getFieldFilter());
    assertEquals(roundTripped, inputFilter);
    assertTrue(roundTripped instanceof ArrayContainsAnyFilter);
  }

  @Test
  public void testKeyFieldSerializationEncoding() {
    FieldFilter inputFilter = filter("__name__", "==", ref("project/database"));
    StructuredQuery.Filter apiFilter = serializer.encodeUnaryOrFieldFilter(inputFilter);

    StructuredQuery.Filter expectedFilter =
        Filter.newBuilder()
            .setFieldFilter(
                StructuredQuery.FieldFilter.newBuilder()
                    .setField(FieldReference.newBuilder().setFieldPath("__name__"))
                    .setOp(Operator.EQUAL)
                    .setValue(
                        Value.newBuilder()
                            .setReferenceValue(
                                "projects/project/databases/(default)/documents/project/database"))
                    .build())
            .build();

    assertEquals(expectedFilter, apiFilter);
    FieldFilter roundTripped = serializer.decodeFieldFilter(apiFilter.getFieldFilter());
    assertEquals(roundTripped, inputFilter);
    assertTrue(roundTripped instanceof KeyFieldFilter);
  }

  // TODO(PORTING NOTE): Android currently tests most filter serialization (for equals, greater
  // than, array-contains, etc.) only in testEncodesMultipleFiltersOnDeeperCollections and lacks
  // isolated filter tests like the other platforms have. We should fix this.

  @Test
  public void testEncodesNullFilter() {
    unaryFilterTest("==", null, UnaryFilter.Operator.IS_NULL);
  }

  @Test
  public void testEncodesNaNFilter() {
    unaryFilterTest("==", Double.NaN, UnaryFilter.Operator.IS_NAN);
  }

  @Test
  public void testEncodesNotNaNFilter() {
    unaryFilterTest("!=", Double.NaN, UnaryFilter.Operator.IS_NOT_NAN);
  }

  @Test
  public void testEncodesNotNullFilter() {
    unaryFilterTest("!=", null, UnaryFilter.Operator.IS_NOT_NULL);
  }

  private void unaryFilterTest(
      String op, Object equalityValue, UnaryFilter.Operator unaryOperator) {
    Query q =
        Query.atPath(ResourcePath.fromString("docs")).filter(filter("prop", op, equalityValue));
    Target actual = serializer.encodeTarget(wrapTargetData(q));

    StructuredQuery.Builder structuredQueryBuilder =
        StructuredQuery.newBuilder()
            .addFrom(CollectionSelector.newBuilder().setCollectionId("docs"))
            .setWhere(
                Filter.newBuilder()
                    .setUnaryFilter(
                        UnaryFilter.newBuilder()
                            .setField(FieldReference.newBuilder().setFieldPath("prop"))
                            .setOp(unaryOperator)));

    // Add extra ORDER_BY field for '!=' since it is an inequality.
    if (op.equals("!=")) {
      structuredQueryBuilder.addOrderBy(
          Order.newBuilder()
              .setDirection(Direction.ASCENDING)
              .setField(FieldReference.newBuilder().setFieldPath("prop")));
    }
    structuredQueryBuilder.addOrderBy(defaultKeyOrder());

    QueryTarget.Builder queryBuilder =
        QueryTarget.newBuilder()
            .setParent("projects/p/databases/d/documents")
            .setStructuredQuery(structuredQueryBuilder);
    Target expected =
        Target.newBuilder()
            .setQuery(queryBuilder)
            .setTargetId(1)
            .setResumeToken(ByteString.EMPTY)
            .build();

    assertEquals(expected, actual);
    assertEquals(
        serializer.decodeQueryTarget(serializer.encodeQueryTarget(q.toTarget())), q.toTarget());
  }

  @Test
  public void testEncodesSortOrders() {
    Query q = Query.atPath(ResourcePath.fromString("docs")).orderBy(orderBy("prop"));
    Target actual = serializer.encodeTarget(wrapTargetData(q));

    StructuredQuery.Builder structuredQueryBuilder =
        StructuredQuery.newBuilder()
            .addFrom(CollectionSelector.newBuilder().setCollectionId("docs"))
            .addOrderBy(
                Order.newBuilder()
                    .setDirection(Direction.ASCENDING)
                    .setField(FieldReference.newBuilder().setFieldPath("prop")))
            .addOrderBy(defaultKeyOrder());
    QueryTarget.Builder queryBuilder =
        QueryTarget.newBuilder()
            .setParent("projects/p/databases/d/documents")
            .setStructuredQuery(structuredQueryBuilder);
    Target expected =
        Target.newBuilder()
            .setQuery(queryBuilder)
            .setTargetId(1)
            .setResumeToken(ByteString.EMPTY)
            .build();

    assertEquals(expected, actual);
    assertEquals(
        serializer.decodeQueryTarget(serializer.encodeQueryTarget(q.toTarget())), q.toTarget());
  }

  @Test
  public void testEncodesSortOrdersDescending() {
    Query q =
        Query.atPath(ResourcePath.fromString("rooms/1/messages/10/attachments"))
            .orderBy(orderBy("prop", "desc"));
    Target actual = serializer.encodeTarget(wrapTargetData(q));

    StructuredQuery.Builder structuredQueryBuilder =
        StructuredQuery.newBuilder()
            .addFrom(CollectionSelector.newBuilder().setCollectionId("attachments"))
            .addOrderBy(
                Order.newBuilder()
                    .setDirection(Direction.DESCENDING)
                    .setField(FieldReference.newBuilder().setFieldPath("prop")))
            .addOrderBy(
                Order.newBuilder()
                    .setDirection(Direction.DESCENDING)
                    .setField(
                        FieldReference.newBuilder().setFieldPath(DocumentKey.KEY_FIELD_NAME)));
    QueryTarget.Builder queryBuilder =
        QueryTarget.newBuilder()
            .setParent("projects/p/databases/d/documents/rooms/1/messages/10")
            .setStructuredQuery(structuredQueryBuilder);
    Target expected =
        Target.newBuilder()
            .setQuery(queryBuilder)
            .setTargetId(1)
            .setResumeToken(ByteString.EMPTY)
            .build();

    assertEquals(expected, actual);
    assertEquals(
        serializer.decodeQueryTarget(serializer.encodeQueryTarget(q.toTarget())), q.toTarget());
  }

  @Test
  public void testEncodesLimits() {
    Query q = Query.atPath(ResourcePath.fromString("docs")).limitToFirst(26);
    Target actual = serializer.encodeTarget(wrapTargetData(q));

    StructuredQuery.Builder structuredQueryBuilder =
        StructuredQuery.newBuilder()
            .addFrom(CollectionSelector.newBuilder().setCollectionId("docs"))
            .addOrderBy(defaultKeyOrder())
            .setLimit(Int32Value.newBuilder().setValue(26));
    QueryTarget.Builder queryBuilder =
        QueryTarget.newBuilder()
            .setParent("projects/p/databases/d/documents")
            .setStructuredQuery(structuredQueryBuilder);
    Target expected =
        Target.newBuilder()
            .setQuery(queryBuilder)
            .setTargetId(1)
            .setResumeToken(ByteString.EMPTY)
            .build();

    assertEquals(expected, actual);
    assertEquals(
        serializer.decodeQueryTarget(serializer.encodeQueryTarget(q.toTarget())), q.toTarget());
  }

  @Test
  public void testEncodesBounds() {
    Query q =
        Query.atPath(ResourcePath.fromString("docs"))
            .startAt(new Bound(asList(Values.refValue(databaseId, key("foo/bar"))), true))
            .endAt(new Bound(asList(Values.refValue(databaseId, key("foo/baz"))), false));
    Target actual = serializer.encodeTarget(wrapTargetData(q));

    StructuredQuery.Builder structuredQueryBuilder =
        StructuredQuery.newBuilder()
            .addFrom(CollectionSelector.newBuilder().setCollectionId("docs"))
            .addOrderBy(defaultKeyOrder())
            .setStartAt(
                Cursor.newBuilder()
                    .setBefore(true)
                    .addValues(
                        Value.newBuilder()
                            .setReferenceValue("projects/p/databases/d/documents/foo/bar")))
            .setEndAt(
                Cursor.newBuilder()
                    .setBefore(false)
                    .addValues(
                        Value.newBuilder()
                            .setReferenceValue("projects/p/databases/d/documents/foo/baz")));

    QueryTarget.Builder queryBuilder =
        QueryTarget.newBuilder()
            .setParent("projects/p/databases/d/documents")
            .setStructuredQuery(structuredQueryBuilder);
    Target expected =
        Target.newBuilder()
            .setQuery(queryBuilder)
            .setTargetId(1)
            .setResumeToken(ByteString.EMPTY)
            .build();

    assertEquals(expected, actual);
    assertEquals(
        serializer.decodeQueryTarget(serializer.encodeQueryTarget(q.toTarget())), q.toTarget());
  }

  @Test
  public void testEncodesResumeTokens() {
    Query q = Query.atPath(ResourcePath.fromString("docs"));
    TargetData targetData =
        new TargetData(q.toTarget(), 1, 2, QueryPurpose.LISTEN)
            .withResumeToken(TestUtil.resumeToken(1000), SnapshotVersion.NONE);
    Target actual = serializer.encodeTarget(targetData);

    StructuredQuery.Builder structuredQueryBuilder =
        StructuredQuery.newBuilder()
            .addFrom(CollectionSelector.newBuilder().setCollectionId("docs"))
            .addOrderBy(defaultKeyOrder());

    QueryTarget.Builder queryBuilder =
        QueryTarget.newBuilder()
            .setParent("projects/p/databases/d/documents")
            .setStructuredQuery(structuredQueryBuilder);
    Target expected =
        Target.newBuilder()
            .setQuery(queryBuilder)
            .setTargetId(1)
            .setResumeToken(TestUtil.resumeToken(1000))
            .build();

    assertEquals(expected, actual);
    assertEquals(
        serializer.decodeQueryTarget(serializer.encodeQueryTarget(q.toTarget())), q.toTarget());
  }

  /**
   * Wraps the given query in TargetData. This is useful because the APIs we're testing accept
   * TargetData, but for the most part we're just testing variations on Query.
   */
  private static TargetData wrapTargetData(Query query) {
    return new TargetData(query.toTarget(), 1, 2, QueryPurpose.LISTEN);
  }

  @Test
  public void testConvertsTargetChangeWithAdded() {
    WatchTargetChange expected = new WatchTargetChange(WatchTargetChangeType.Added, asList(1, 4));
    WatchTargetChange actual =
        (WatchTargetChange)
            serializer.decodeWatchChange(
                ListenResponse.newBuilder()
                    .setTargetChange(
                        TargetChange.newBuilder()
                            .setTargetChangeType(TargetChangeType.ADD)
                            .addTargetIds(1)
                            .addTargetIds(4))
                    .build());
    assertEquals(expected, actual);
  }

  @Test
  public void testConvertsTargetChangeWithRemoved() {
    WatchTargetChange expected =
        new WatchTargetChange(
            WatchTargetChangeType.Removed,
            asList(1, 4),
            ByteString.copyFrom(new byte[] {0, 1, 2}),
            Status.PERMISSION_DENIED);
    WatchTargetChange actual =
        (WatchTargetChange)
            serializer.decodeWatchChange(
                ListenResponse.newBuilder()
                    .setTargetChange(
                        TargetChange.newBuilder()
                            .setTargetChangeType(TargetChangeType.REMOVE)
                            .addTargetIds(1)
                            .addTargetIds(4)
                            .setCause(com.google.rpc.Status.newBuilder().setCode(7))
                            .setResumeToken(ByteString.copyFrom(new byte[] {0, 1, 2})))
                    .build());
    assertEquals(expected, actual);
  }

  @Test
  public void testConvertsTargetChangeWithNoChange() {
    WatchTargetChange expected =
        new WatchTargetChange(WatchTargetChangeType.NoChange, asList(1, 4));
    WatchTargetChange actual =
        (WatchTargetChange)
            serializer.decodeWatchChange(
                ListenResponse.newBuilder()
                    .setTargetChange(
                        TargetChange.newBuilder()
                            .setTargetChangeType(TargetChangeType.NO_CHANGE)
                            .addTargetIds(1)
                            .addTargetIds(4))
                    .build());
    assertEquals(expected, actual);
  }

  @Test
  public void testConvertsDocumentChangeWithTargetIds() {
    WatchChange.DocumentChange expected =
        new WatchChange.DocumentChange(
            asList(1, 2), asList(), key("coll/1"), doc("coll/1", 5, map("foo", "bar")));
    WatchChange.DocumentChange actual =
        (WatchChange.DocumentChange)
            serializer.decodeWatchChange(
                ListenResponse.newBuilder()
                    .setDocumentChange(
                        DocumentChange.newBuilder()
                            .setDocument(
                                Document.newBuilder()
                                    .setName(serializer.encodeKey(key("coll/1")))
                                    .setUpdateTime(
                                        serializer.encodeTimestamp(
                                            new com.google.firebase.Timestamp(0, 5000)))
                                    .putFields(
                                        "foo", Value.newBuilder().setStringValue("bar").build()))
                            .addTargetIds(1)
                            .addTargetIds(2))
                    .build());
    assertEquals(expected, actual);
  }

  @Test
  public void testConvertsDocumentChangeWithRemovedTargetIds() {
    WatchChange.DocumentChange expected =
        new WatchChange.DocumentChange(
            asList(2), asList(1), key("coll/1"), doc("coll/1", 5, map("foo", "bar")));
    WatchChange.DocumentChange actual =
        (WatchChange.DocumentChange)
            serializer.decodeWatchChange(
                ListenResponse.newBuilder()
                    .setDocumentChange(
                        DocumentChange.newBuilder()
                            .setDocument(
                                Document.newBuilder()
                                    .setName(serializer.encodeKey(key("coll/1")))
                                    .setUpdateTime(
                                        serializer.encodeTimestamp(
                                            new com.google.firebase.Timestamp(0, 5000)))
                                    .putFields(
                                        "foo", Value.newBuilder().setStringValue("bar").build()))
                            .addTargetIds(2)
                            .addRemovedTargetIds(1))
                    .build());
    assertEquals(expected, actual);
  }

  @Test
  public void testConvertsDocumentChangeWithDeletions() {
    WatchChange.DocumentChange expected =
        new WatchChange.DocumentChange(
            asList(), asList(1, 2), key("coll/1"), deletedDoc("coll/1", 5));
    WatchChange.DocumentChange actual =
        (WatchChange.DocumentChange)
            serializer.decodeWatchChange(
                ListenResponse.newBuilder()
                    .setDocumentDelete(
                        DocumentDelete.newBuilder()
                            .setDocument(serializer.encodeKey(key("coll/1")))
                            .setReadTime(
                                serializer.encodeTimestamp(
                                    new com.google.firebase.Timestamp(0, 5000)))
                            .addRemovedTargetIds(1)
                            .addRemovedTargetIds(2))
                    .build());
    assertEquals(expected, actual);
  }

  @Test
  public void testConvertsDocumentChangeWithRemoves() {
    WatchChange.DocumentChange expected =
        new WatchChange.DocumentChange(asList(), asList(1, 2), key("coll/1"), null);
    WatchChange.DocumentChange actual =
        (WatchChange.DocumentChange)
            serializer.decodeWatchChange(
                ListenResponse.newBuilder()
                    .setDocumentRemove(
                        DocumentRemove.newBuilder()
                            .setDocument(serializer.encodeKey(key("coll/1")))
                            .addRemovedTargetIds(1)
                            .addRemovedTargetIds(2))
                    .build());
    assertEquals(expected, actual);
  }
}
