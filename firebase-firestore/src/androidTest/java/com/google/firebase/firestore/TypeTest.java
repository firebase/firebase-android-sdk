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

package com.google.firebase.firestore;

import static com.google.firebase.firestore.testutil.IntegrationTestUtil.assertSDKQueryResultsConsistentWithBackend;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testCollection;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testCollectionOnNightly;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testDocumentOnNightly;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitFor;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.writeTestDocsOnCollection;
import static com.google.firebase.firestore.testutil.TestUtil.blob;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.testutil.IntegrationTestUtil;
import com.google.protobuf.ByteString;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class TypeTest {

  @After
  public void tearDown() {
    IntegrationTestUtil.tearDown();
  }

  private static void verifySuccessfulWriteReadCycle(
      Map<String, Object> data, DocumentReference documentReference) {
    waitFor(documentReference.set(data));
    DocumentSnapshot doc = waitFor(documentReference.get());
    assertEquals(data, doc.getData());
  }

  private static DocumentReference testDoc() {
    return testCollection().document();
  }

  @Test
  public void testCanReadAndWriteNullFields() {
    verifySuccessfulWriteReadCycle(map("a", 1.0, "b", null), testDoc());
  }

  @Test
  public void testCanReadAndWriteListFields() {
    verifySuccessfulWriteReadCycle(
        map("array", Arrays.asList(1.0, "foo", map("deep", true), null)), testDoc());
  }

  @Test
  public void testCanReadAndWriteBlobFields() {
    verifySuccessfulWriteReadCycle(map("blob", blob(1, 2, 3)), testDoc());
  }

  @Test
  public void testCanReadAndWriteGeoPointFields() {
    verifySuccessfulWriteReadCycle(map("geoPoint", new GeoPoint(1.23, 4.56)), testDoc());
  }

  @Test
  public void testCanReadAndWriteTimestamps() {
    Timestamp timestamp = new Timestamp(100, 123000000);
    verifySuccessfulWriteReadCycle(map("timestamp", timestamp), testDoc());
  }

  @Test
  public void testCanReadAndWriteDates() {
    Date date = new Date(1491847082123L);
    // Tests are set up to read back Timestamps, not Dates.
    verifySuccessfulWriteReadCycle(map("date", new Timestamp(date)), testDoc());
  }

  @Test
  public void testCanReadAndWriteDocumentReferences() {
    DocumentReference docRef = testDoc();
    Map<String, Object> data = map("a", 42L, "ref", docRef);
    verifySuccessfulWriteReadCycle(data, docRef);
  }

  @Test
  public void testCanReadAndWriteDocumentReferencesInLists() {
    DocumentReference docRef = testDoc();
    List<Object> refs = Collections.singletonList(docRef);
    Map<String, Object> data = map("a", 42L, "refs", refs);
    verifySuccessfulWriteReadCycle(data, docRef);
  }

  @Test
  public void testCanReadAndWriteMinKey() {
    verifySuccessfulWriteReadCycle(map("minKey", MinKey.instance()), testDocumentOnNightly());
  }

  @Test
  public void testCanReadAndWriteMaxKey() {
    verifySuccessfulWriteReadCycle(map("maxKey", MaxKey.instance()), testDocumentOnNightly());
  }

  @Test
  public void testCanReadAndWriteRegexValue() {
    verifySuccessfulWriteReadCycle(
        map("regex", new RegexValue("^foo", "i")), testDocumentOnNightly());
  }

  @Test
  public void testCanReadAndWriteInt32Value() {
    verifySuccessfulWriteReadCycle(map("int32", new Int32Value(1)), testDocumentOnNightly());
  }

  @Test
  public void testCanReadAndWriteDecimal128Value() {
    Map<String, Object> decimal128Values =
        map(
            "decimalSciPositive", new Decimal128Value("1.2e3"),
            "decimalSciNegative", new Decimal128Value("-1.2e3"),
            "decimalSciNegativeExponent", new Decimal128Value("1.2e-3"),
            "decimalSciNegativeValueAndExponent", new Decimal128Value("-1.2e-3"),
            "decimalSciExplicitPositiveExponent", new Decimal128Value("1.2e+3"),
            "decimalFloatPositive", new Decimal128Value("1.1"),
            "decimalIntNegative", new Decimal128Value("-1"),
            "decimalZeroNegative", new Decimal128Value("-0"),
            "decimalZeroInt", new Decimal128Value("0"),
            "decimalZeroFloat", new Decimal128Value("0.0"),
            "decimalNaN", new Decimal128Value("NaN"),
            "decimalInfinityPositive", new Decimal128Value("Infinity"),
            "decimalInfinityNegative", new Decimal128Value("-Infinity"));
    verifySuccessfulWriteReadCycle(decimal128Values, testDocumentOnNightly());
  }

  @Test
  public void testCanReadAndWriteBsonTimestampValue() {
    verifySuccessfulWriteReadCycle(
        map("bsonTimestamp", new BsonTimestamp(1, 2)), testDocumentOnNightly());
  }

  @Test
  public void testCanReadAndWriteBsonObjectIdValue() {
    verifySuccessfulWriteReadCycle(
        map("bsonObjectId", new BsonObjectId("507f191e810c19729de860ea")), testDocumentOnNightly());
  }

  @Test
  public void testCanReadAndWriteBsonBinaryValue() {
    verifySuccessfulWriteReadCycle(
        map("bsonBinary", BsonBinaryData.fromBytes(1, new byte[] {1, 2, 3})),
        testDocumentOnNightly());

    verifySuccessfulWriteReadCycle(
        map("bsonBinary", BsonBinaryData.fromBytes(128, new byte[] {1, 2, 3})),
        testDocumentOnNightly());

    verifySuccessfulWriteReadCycle(
        map("bsonBinary", BsonBinaryData.fromByteString(255, ByteString.EMPTY)),
        testDocumentOnNightly());
  }

  @Test
  public void testCanReadAndWriteBsonTypesInLists() {
    List<Object> data =
        Arrays.asList(
            new BsonObjectId("507f191e810c19729de860ea"),
            new RegexValue("^foo", "i"),
            new BsonTimestamp(1, 2),
            BsonBinaryData.fromBytes(1, new byte[] {1, 2, 3}),
            new Int32Value(1),
            new Decimal128Value("1.2e3"),
            MinKey.instance(),
            MaxKey.instance());

    verifySuccessfulWriteReadCycle(map("BsonTypes", data), testDocumentOnNightly());
  }

  @Test
  public void testCanReadAndWriteBsonTypesInMaps() {
    Map<String, Object> data =
        map(
            "bsonObjectId",
            new BsonObjectId("507f191e810c19729de860ea"),
            "regex",
            new RegexValue("^foo", "i"),
            "bsonTimestamp",
            new BsonTimestamp(1, 2),
            "bsonBinary",
            BsonBinaryData.fromBytes(1, new byte[] {1, 2, 3}),
            "int32",
            new Int32Value(1),
            "decimal128",
            new Decimal128Value("1.2e3"),
            "minKey",
            MinKey.instance(),
            "maxKey",
            MaxKey.instance());

    verifySuccessfulWriteReadCycle(map("BsonTypes", data), testDocumentOnNightly());
  }

  @Test
  public void invalidRegexGetsRejected() throws Exception {
    Exception error = null;
    try {
      waitFor(testDocumentOnNightly().set(map("key", new RegexValue("foo", "a"))));
    } catch (Exception e) {
      error = e;
    }
    assertNotNull(error);
    assertTrue(
        error
            .getMessage()
            .contains(
                "Invalid regex option 'a'. Supported options are 'i', 'm', 's', 'u', and 'x'"));
  }

  @Test
  public void invalidDecimal128ValueGetsRejected() throws Exception {
    Exception error = null;
    try {
      waitFor(testDocumentOnNightly().set(map("key", new Decimal128Value(""))));
    } catch (Exception e) {
      error = e;
    }
    assertNotNull(error);
    assertTrue(error.getMessage().contains("Invalid number"));

    try {
      waitFor(testDocumentOnNightly().set(map("key", new Decimal128Value("abc"))));
    } catch (Exception e) {
      error = e;
    }
    assertNotNull(error);
    assertTrue(error.getMessage().contains("Invalid number"));

    try {
      waitFor(testDocumentOnNightly().set(map("key", new Decimal128Value("1 23.45"))));
    } catch (Exception e) {
      error = e;
    }
    assertNotNull(error);
    assertTrue(error.getMessage().contains("Invalid number"));

    try {
      waitFor(testDocumentOnNightly().set(map("key", new Decimal128Value("1e1234567890"))));
    } catch (Exception e) {
      error = e;
    }
    assertNotNull(error);
    assertTrue(error.getMessage().contains("Exponent too large"));
  }

  @Test
  public void invalidBsonObjectIdGetsRejected() throws Exception {
    Exception error = null;
    try {
      // bsonObjectId with length not equal to 24 gets rejected
      waitFor(testDocumentOnNightly().set(map("key", new BsonObjectId("foobar"))));
    } catch (Exception e) {
      error = e;
    }
    assertNotNull(error);
    assertTrue(error.getMessage().contains("Object ID hex string has incorrect length."));
  }

  @Test
  public void invalidBsonBinaryDataGetsRejected() throws Exception {
    Exception error = null;
    try {
      waitFor(
          testDocumentOnNightly()
              .set(map("key", BsonBinaryData.fromBytes(1234, new byte[] {1, 2, 3}))));
    } catch (Exception e) {
      error = e;
    }
    assertNotNull(error);
    assertTrue(
        error
            .getMessage()
            .contains(
                "The subtype for BsonBinaryData must be a value in the inclusive [0, 255] range."));
  }

  @Test
  public void invalidBsonTimestampDataGetsRejected() throws Exception {
    Exception error = null;
    try {
      waitFor(testDocumentOnNightly().set(map("key", new BsonTimestamp(-1, 1))));
    } catch (Exception e) {
      error = e;
    }
    assertNotNull(error);
    assertTrue(
        error
            .getMessage()
            .contains(
                "The field 'seconds' value (-1) does not represent an unsigned 32-bit integer."));

    try {
      waitFor(testDocumentOnNightly().set(map("key", new BsonTimestamp(4294967296L, 1))));
    } catch (Exception e) {
      error = e;
    }
    assertNotNull(error);
    assertTrue(
        error
            .getMessage()
            .contains(
                "The field 'seconds' value (4294967296) does not represent an unsigned 32-bit integer."));

    try {
      waitFor(testDocumentOnNightly().set(map("key", new BsonTimestamp(1, -1))));
    } catch (Exception e) {
      error = e;
    }
    assertNotNull(error);
    assertTrue(
        error
            .getMessage()
            .contains(
                "The field 'increment' value (-1) does not represent an unsigned 32-bit integer."));

    try {
      waitFor(testDocumentOnNightly().set(map("key", new BsonTimestamp(1, 4294967296L))));
    } catch (Exception e) {
      error = e;
    }
    assertNotNull(error);
    assertTrue(
        error
            .getMessage()
            .contains(
                "The field 'increment' value (4294967296) does not represent an unsigned 32-bit integer."));
  }

  @Test
  public void testCanUseTypedAccessors() {
    DocumentReference doc = testDocumentOnNightly();
    Map<String, Object> data =
        map(
            "null",
            null,
            "boolean",
            true,
            "string",
            "string",
            "double",
            0.0,
            "int",
            1L,
            "geoPoint",
            new GeoPoint(1.24, 4.56),
            "blob",
            blob(0, 1, 2),
            "date",
            new Date(),
            "timestamp",
            new Timestamp(100, 123000000),
            "reference",
            doc,
            "array",
            Arrays.asList(1.0, "foo", map("nested", true), null),
            "map",
            map("key", true),
            "vector",
            FieldValue.vector(new double[] {1, 2, 3}),
            "regex",
            new RegexValue("^foo", "i"),
            "int32",
            new Int32Value(1),
            "decimal128",
            new Decimal128Value("1.2e3"),
            "bsonTimestamp",
            new BsonTimestamp(1, 2),
            "bsonObjectId",
            new BsonObjectId("507f191e810c19729de860ea"),
            "bsonBinary",
            BsonBinaryData.fromBytes(1, new byte[] {1, 2, 3}),
            "minKey",
            MinKey.instance(),
            "maxKey",
            MaxKey.instance());

    waitFor(doc.set(data));
    DocumentSnapshot snapshot = waitFor(doc.get());
    assertEquals(data.get("null"), snapshot.get("null"));
    assertEquals(data.get("null"), snapshot.get(FieldPath.fromDotSeparatedPath("null")));
    assertEquals(data.get("boolean"), snapshot.getBoolean("boolean"));
    assertEquals(data.get("string"), snapshot.getString("string"));
    assertEquals(data.get("double"), snapshot.getDouble("double"));
    assertEquals(Long.valueOf(0), snapshot.getLong("double"));
    assertEquals(data.get("int"), snapshot.getLong("int"));
    assertEquals(Double.valueOf(1.0), snapshot.getDouble("int"));
    assertEquals(data.get("geoPoint"), snapshot.getGeoPoint("geoPoint"));
    assertEquals(data.get("blob"), snapshot.getBlob("blob"));
    assertEquals(data.get("date"), snapshot.getDate("date"));
    assertEquals(new Timestamp((Date) data.get("date")), snapshot.getTimestamp("date"));
    assertEquals(data.get("timestamp"), snapshot.getTimestamp("timestamp"));
    Timestamp timestamp = (Timestamp) data.get("timestamp");
    assertEquals(timestamp.toDate(), snapshot.getDate("timestamp"));
    assertTrue(data.get("reference") instanceof DocumentReference);
    assertEquals(((DocumentReference) data.get("reference")).getPath(), doc.getPath());
    assertEquals(data.get("array"), snapshot.get("array"));
    assertEquals(data.get("map"), snapshot.get("map"));
    assertEquals(data.get("vector"), snapshot.getVectorValue("vector"));
    assertEquals(data.get("regex"), snapshot.getRegexValue("regex"));
    assertEquals(data.get("int32"), snapshot.getInt32Value("int32"));
    assertEquals(data.get("decimal128"), snapshot.getDecimal128Value("decimal128"));
    assertEquals(data.get("bsonTimestamp"), snapshot.getBsonTimestamp("bsonTimestamp"));
    assertEquals(data.get("bsonObjectId"), snapshot.getBsonObjectId("bsonObjectId"));
    assertEquals(data.get("bsonBinary"), snapshot.getBsonBinaryData("bsonBinary"));
    assertEquals(data.get("minKey"), snapshot.getMinKey("minKey"));
    assertEquals(data.get("maxKey"), snapshot.getMaxKey("maxKey"));
  }

  @Test
  public void testTypeAccessorsCanReturnNull() {
    DocumentReference doc = testDocumentOnNightly();
    Map<String, Object> data = map();

    waitFor(doc.set(data));
    DocumentSnapshot snapshot = waitFor(doc.get());
    assertNull(snapshot.get("missing"));
    assertNull(snapshot.getBoolean("missing"));
    assertNull(snapshot.getString("missing"));
    assertNull(snapshot.getDouble("missing"));
    assertNull(snapshot.getLong("missing"));
    assertNull(snapshot.getLong("missing"));
    assertNull(snapshot.getDouble("missing"));
    assertNull(snapshot.getGeoPoint("missing"));
    assertNull(snapshot.getBlob("missing"));
    assertNull(snapshot.getDate("missing"));
    assertNull(snapshot.getTimestamp("missing"));
    assertNull(snapshot.getDocumentReference("missing"));
    assertNull(snapshot.getVectorValue("missing"));
    assertNull(snapshot.getRegexValue("missing"));
    assertNull(snapshot.getInt32Value("missing"));
    assertNull(snapshot.getDecimal128Value("missing"));
    assertNull(snapshot.getBsonTimestamp("missing"));
    assertNull(snapshot.getBsonObjectId("missing"));
    assertNull(snapshot.getBsonBinaryData("missing"));
    assertNull(snapshot.getMinKey("missing"));
    assertNull(snapshot.getMaxKey("missing"));
  }

  @Test
  public void snapshotListenerSortsDifferentTypesSameAsServer() throws Exception {
    CollectionReference colRef = testCollectionOnNightly();
    // Document reference needs to be created first to make sure it is using the same firestore
    // instance in creation
    DocumentReference docRef = colRef.document("testDocRef");

    Map<String, Map<String, Object>> testDocs =
        map(
            "null",
            map("value", null),
            "min",
            map("value", MinKey.instance()),
            "boolean",
            map("value", true),
            "nan",
            map("value", Double.NaN),
            "int32",
            map("value", new Int32Value(1)),
            "decimal128",
            map("value", new Decimal128Value("1.2e3")),
            "double",
            map("value", 1.0),
            "int",
            map("value", 1L),
            "timestamp",
            map("value", new Timestamp(100, 123000000)),
            "bsonTimestamp",
            map("value", new BsonTimestamp(1, 2)),
            "string",
            map("value", "a"),
            "bytes",
            map("value", blob(1, 2, 3)),
            "bsonBinary",
            map("value", BsonBinaryData.fromBytes(1, new byte[] {1, 2, 3})),
            "reference",
            map("value", docRef),
            "bsonObjectId",
            map("value", new BsonObjectId("507f191e810c19729de860ea")),
            "geoPoint",
            map("value", new GeoPoint(1.23, 4.56)),
            "regex",
            map("value", new RegexValue("^foo", "i")),
            "array",
            map("value", Arrays.asList(1.0, "foo", map("key", true), null)),
            "vector",
            map("value", FieldValue.vector(new double[] {1, 2, 3})),
            "map",
            map("value", map("key", true)),
            "max",
            map("value", MaxKey.instance()));

    writeTestDocsOnCollection(colRef, testDocs);

    Query orderedQuery = colRef.orderBy("value");
    List<String> expectedDocs =
        Arrays.asList(
            "null",
            "min",
            "boolean",
            "nan",
            "double",
            "int",
            "int32",
            "decimal128",
            "timestamp",
            "bsonTimestamp",
            "string",
            "bytes",
            "bsonBinary",
            "reference",
            "bsonObjectId",
            "geoPoint",
            "regex",
            "array",
            "vector",
            "map",
            "max");

    // Assert that get and snapshot listener requests sort docs in the same, expected order
    assertSDKQueryResultsConsistentWithBackend(colRef, orderedQuery, testDocs, expectedDocs);
  }
}
