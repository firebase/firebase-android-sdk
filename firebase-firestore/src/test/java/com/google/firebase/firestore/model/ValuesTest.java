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

package com.google.firebase.firestore.model;

import static com.google.firebase.firestore.model.Values.getLowerBound;
import static com.google.firebase.firestore.model.Values.getUpperBound;
import static com.google.firebase.firestore.testutil.TestUtil.blob;
import static com.google.firebase.firestore.testutil.TestUtil.dbId;
import static com.google.firebase.firestore.testutil.TestUtil.key;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.testutil.TestUtil.ref;
import static com.google.firebase.firestore.testutil.TestUtil.wrapRef;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.testing.EqualsTester;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.BsonBinaryData;
import com.google.firebase.firestore.BsonObjectId;
import com.google.firebase.firestore.BsonTimestamp;
import com.google.firebase.firestore.Decimal128Value;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.Int32Value;
import com.google.firebase.firestore.MaxKey;
import com.google.firebase.firestore.MinKey;
import com.google.firebase.firestore.RegexValue;
import com.google.firebase.firestore.model.Values.MapRepresentation;
import com.google.firebase.firestore.testutil.ComparatorTester;
import com.google.firebase.firestore.testutil.TestUtil;
import com.google.firestore.v1.Value;
import com.google.protobuf.ByteString;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.TimeZone;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class ValuesTest {
  private final Date date1;
  private final Date date2;

  public ValuesTest() {
    // Create a couple date objects for use in tests.
    Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    calendar.set(2016, 5, 20, 10, 20, 0);
    date1 = calendar.getTime();

    calendar.set(2016, 10, 21, 15, 32, 0);
    date2 = calendar.getTime();
  }

  @Test
  public void testValueEquality() {
    GeoPoint geoPoint1 = new GeoPoint(1, 0);
    GeoPoint geoPoint2 = new GeoPoint(0, 2);
    Timestamp timestamp1 = new Timestamp(date1);
    Timestamp timestamp2 = new Timestamp(date2);

    BsonObjectId objectId1 = new BsonObjectId("507f191e810c19729de860ea");
    BsonObjectId objectId2 = new BsonObjectId("507f191e810c19729de860eb");

    BsonBinaryData binaryData1 = BsonBinaryData.fromBytes(1, new byte[] {1, 2});
    BsonBinaryData binaryData2 = BsonBinaryData.fromBytes(1, new byte[] {1, 2, 3});
    BsonBinaryData binaryData3 = BsonBinaryData.fromBytes(2, new byte[] {1, 2});

    BsonTimestamp bsonTimestamp1 = new BsonTimestamp(1, 2);
    BsonTimestamp bsonTimestamp2 = new BsonTimestamp(1, 3);
    BsonTimestamp bsonTimestamp3 = new BsonTimestamp(2, 2);

    Int32Value int32Value1 = new Int32Value(1);
    Int32Value int32Value2 = new Int32Value(2);

    Decimal128Value decimal128Value1 = new Decimal128Value("-1.2e3");
    Decimal128Value decimal128Value2 = new Decimal128Value("0.0");
    Decimal128Value decimal128Value3 = new Decimal128Value("1.2e-3");

    RegexValue regexValue1 = new RegexValue("^foo", "i");
    RegexValue regexValue2 = new RegexValue("^foo", "m");
    RegexValue regexValue3 = new RegexValue("^bar", "i");

    MinKey minKey = MinKey.instance();
    MaxKey maxKey = MaxKey.instance();

    new EqualsTester()
        .addEqualityGroup(wrap(true), wrap(true))
        .addEqualityGroup(wrap(false), wrap(false))
        .addEqualityGroup(wrap((Object) null), wrap(Values.NULL_VALUE))
        .addEqualityGroup(
            wrap(0.0 / 0.0),
            wrap(Double.longBitsToDouble(0x7ff8000000000000L)),
            wrap(Values.NAN_VALUE))
        // -0.0 and 0.0 compareTo the same but are not equal.
        .addEqualityGroup(wrap(-0.0))
        .addEqualityGroup(wrap(0.0))
        .addEqualityGroup(wrap(1), wrap(1))
        // Doubles and Longs aren't equal.
        .addEqualityGroup(wrap(1.0), wrap(1.0))
        .addEqualityGroup(wrap(1.1), wrap(1.1))
        .addEqualityGroup(wrap(blob(0, 1, 2)), wrap(blob(0, 1, 2)))
        .addEqualityGroup(wrap(blob(0, 1)))
        .addEqualityGroup(wrap("string"), wrap("string"))
        .addEqualityGroup(wrap("strin"))
        // latin small letter e + combining acute accent
        .addEqualityGroup(wrap("e\u0301b"))
        // latin small letter e with acute accent
        .addEqualityGroup(wrap("\u00e9a"))
        .addEqualityGroup(wrap(date1), wrap(timestamp1))
        .addEqualityGroup(wrap(timestamp2))
        // NOTE: ServerTimestamps can't be parsed via wrap().
        .addEqualityGroup(
            wrap(ServerTimestamps.valueOf(new Timestamp(date1), null)),
            wrap(ServerTimestamps.valueOf(new Timestamp(date1), null)))
        .addEqualityGroup(wrap(ServerTimestamps.valueOf(new Timestamp(date2), null)))
        .addEqualityGroup(wrap(geoPoint1), wrap(new GeoPoint(1, 0)))
        .addEqualityGroup(wrap(geoPoint2))
        .addEqualityGroup(wrap(ref("coll/doc1")), wrap(wrapRef(dbId("project"), key("coll/doc1"))))
        .addEqualityGroup(wrap(wrapRef(dbId("project", "bar"), key("coll/doc2"))))
        .addEqualityGroup(wrap(wrapRef(dbId("project", "baz"), key("coll/doc2"))))
        .addEqualityGroup(wrap(Arrays.asList("foo", "bar")), wrap(Arrays.asList("foo", "bar")))
        .addEqualityGroup(wrap(Arrays.asList("foo", "bar", "baz")))
        .addEqualityGroup(wrap(Arrays.asList("foo")))
        .addEqualityGroup(wrap(FieldValue.vector(new double[] {})))
        .addEqualityGroup(wrap(FieldValue.vector(new double[] {1, 2.3, -4})))
        .addEqualityGroup(wrap(map("bar", 1, "foo", 2)), wrap(map("foo", 2, "bar", 1)))
        .addEqualityGroup(wrap(map("bar", 2, "foo", 1)))
        .addEqualityGroup(wrap(map("bar", 1)))
        .addEqualityGroup(wrap(map("foo", 1)))
        .addEqualityGroup(wrap(new BsonObjectId("507f191e810c19729de860ea")), wrap(objectId1))
        .addEqualityGroup(wrap(new BsonObjectId("507f191e810c19729de860eb")), wrap(objectId2))
        .addEqualityGroup(wrap(BsonBinaryData.fromBytes(1, new byte[] {1, 2})), wrap(binaryData1))
        .addEqualityGroup(
            wrap(BsonBinaryData.fromBytes(1, new byte[] {1, 2, 3})), wrap(binaryData2))
        .addEqualityGroup(wrap(BsonBinaryData.fromBytes(2, new byte[] {1, 2})), wrap(binaryData3))
        .addEqualityGroup(wrap(new BsonTimestamp(1, 2)), wrap(bsonTimestamp1))
        .addEqualityGroup(wrap(new BsonTimestamp(1, 3)), wrap(bsonTimestamp2))
        .addEqualityGroup(wrap(new BsonTimestamp(2, 2)), wrap(bsonTimestamp3))
        .addEqualityGroup(wrap(new Int32Value(1)), wrap(int32Value1))
        .addEqualityGroup(wrap(new Int32Value(2)), wrap(int32Value2))
        .addEqualityGroup(wrap(new Decimal128Value("-1.2e3")), wrap(decimal128Value1))
        .addEqualityGroup(wrap(new Decimal128Value("0.0")), wrap(decimal128Value2))
        .addEqualityGroup(wrap(new Decimal128Value("1.2e-3")), wrap(decimal128Value3))
        .addEqualityGroup(wrap(new RegexValue("^foo", "i")), wrap(regexValue1))
        .addEqualityGroup(wrap(new RegexValue("^foo", "m")), wrap(regexValue2))
        .addEqualityGroup(wrap(new RegexValue("^bar", "i")), wrap(regexValue3))
        .addEqualityGroup(wrap(MinKey.instance()), wrap(minKey))
        .addEqualityGroup(wrap(MaxKey.instance()), wrap(maxKey))
        .testEquals();
  }

  @Test
  public void testValueOrdering() {
    new ComparatorTester()
        // do not test for compatibility with equals(): +0/-0 break it.
        .permitInconsistencyWithEquals()

        // null first
        .addEqualityGroup(wrap((Object) null))

        // MinKey is after null
        .addEqualityGroup(wrap(MinKey.instance()))

        // booleans
        .addEqualityGroup(wrap(false))
        .addEqualityGroup(wrap(true))

        // 64-bit and 32-bit numbers order together numerically.
        .addEqualityGroup(wrap(Double.NaN), wrap(new Decimal128Value("NaN")))
        .addEqualityGroup(wrap(Double.NEGATIVE_INFINITY), wrap(new Decimal128Value("-Infinity")))
        .addEqualityGroup(wrap(-Double.MAX_VALUE))
        .addEqualityGroup(wrap(Long.MIN_VALUE), wrap(new Decimal128Value("-9223372036854775808")))
        .addEqualityGroup(
            wrap(new Int32Value(-2147483648)),
            wrap(Integer.MIN_VALUE),
            wrap(new Decimal128Value("-2147483648")))
        // Note: decimal 128 would have equality issue with other number types if the value doesn't
        // have a 2's complement representation, e.g, 1.1. This is expected.
        .addEqualityGroup(wrap(-1.5), wrap(new Decimal128Value("-1.5")))
        .addEqualityGroup(wrap(-1.0), wrap(new Decimal128Value("-1.0")))
        .addEqualityGroup(wrap(-Double.MIN_NORMAL))
        .addEqualityGroup(wrap(-Double.MIN_VALUE))
        // Zeros all compare the same.
        .addEqualityGroup(
            wrap(-0.0),
            wrap(0.0),
            wrap(0L),
            wrap(new Int32Value(0)),
            wrap(new Decimal128Value("0")),
            wrap(new Decimal128Value("0.0")),
            wrap(new Decimal128Value("-0")),
            wrap(new Decimal128Value("-0.0")),
            wrap(new Decimal128Value("+0")),
            wrap(new Decimal128Value("+0.0")))
        .addEqualityGroup(wrap(Double.MIN_VALUE))
        .addEqualityGroup(wrap(Double.MIN_NORMAL))
        .addEqualityGroup(wrap(0.5), wrap(new Decimal128Value("0.5")))
        // Doubles, Longs, Int32Values compareTo() the same.
        .addEqualityGroup(
            wrap(1.0),
            wrap(1L),
            wrap(new Int32Value(1)),
            wrap(new Decimal128Value("1")),
            wrap(new Decimal128Value("1.0")))
        .addEqualityGroup(wrap(1.1))
        .addEqualityGroup(
            wrap(new Int32Value(2147483647)),
            wrap(Integer.MAX_VALUE),
            wrap(new Decimal128Value("2.147483647e9")))
        .addEqualityGroup(wrap(Long.MAX_VALUE))
        .addEqualityGroup(wrap(Double.MAX_VALUE))
        .addEqualityGroup(wrap(Double.POSITIVE_INFINITY), wrap(new Decimal128Value("Infinity")))

        // dates
        .addEqualityGroup(wrap(date1))
        .addEqualityGroup(wrap(date2))

        // bson timestamps
        .addEqualityGroup(wrap(new BsonTimestamp(123, 4)))
        .addEqualityGroup(wrap(new BsonTimestamp(123, 5)))
        .addEqualityGroup(wrap(new BsonTimestamp(124, 0)))

        // server timestamps come after all concrete timestamps.
        .addEqualityGroup(wrap(ServerTimestamps.valueOf(new Timestamp(date1), null)))
        .addEqualityGroup(wrap(ServerTimestamps.valueOf(new Timestamp(date2), null)))

        // strings
        .addEqualityGroup(wrap(""))
        .addEqualityGroup(wrap("\000\ud7ff\ue000\uffff"))
        .addEqualityGroup(wrap("(╯°□°）╯︵ ┻━┻"))
        .addEqualityGroup(wrap("a"))
        .addEqualityGroup(wrap("abc def"))
        // latin small letter e + combining acute accent + latin small letter b
        .addEqualityGroup(wrap("e\u0301b"))
        .addEqualityGroup(wrap("æ"))
        // latin small letter e with acute accent + latin small letter a
        .addEqualityGroup(wrap("\u00e9a"))

        // blobs
        .addEqualityGroup(wrap(blob()))
        .addEqualityGroup(wrap(blob(0)))
        .addEqualityGroup(wrap(blob(0, 1, 2, 3, 4)))
        .addEqualityGroup(wrap(blob(0, 1, 2, 4, 3)))
        .addEqualityGroup(wrap(blob(255)))

        // bson binary data
        .addEqualityGroup(
            wrap(BsonBinaryData.fromBytes(1, new byte[] {})),
            wrap(BsonBinaryData.fromByteString(1, ByteString.EMPTY)))
        .addEqualityGroup(wrap(BsonBinaryData.fromBytes(1, new byte[] {0})))
        .addEqualityGroup(wrap(BsonBinaryData.fromBytes(5, new byte[] {1, 2})))
        .addEqualityGroup(wrap(BsonBinaryData.fromBytes(5, new byte[] {1, 2, 3})))
        .addEqualityGroup(wrap(BsonBinaryData.fromBytes(7, new byte[] {1})))

        // resource names
        .addEqualityGroup(wrap(wrapRef(dbId("p1", "d1"), key("c1/doc1"))))
        .addEqualityGroup(wrap(wrapRef(dbId("p1", "d1"), key("c1/doc2"))))
        .addEqualityGroup(wrap(wrapRef(dbId("p1", "d1"), key("c10/doc1"))))
        .addEqualityGroup(wrap(wrapRef(dbId("p1", "d1"), key("c2/doc1"))))
        .addEqualityGroup(wrap(wrapRef(dbId("p1", "d2"), key("c1/doc1"))))
        .addEqualityGroup(wrap(wrapRef(dbId("p2", "d1"), key("c1/doc1"))))

        // bson object id
        .addEqualityGroup(wrap(new BsonObjectId("507f191e810c19729de860ea")))
        .addEqualityGroup(wrap(new BsonObjectId("507f191e810c19729de860eb")))
        // latin small letter e + combining acute accent + latin small letter b
        .addEqualityGroup(wrap(new BsonObjectId("e\u0301b")))
        .addEqualityGroup(wrap(new BsonObjectId("æ")))
        // latin small letter e with acute accent + latin small letter a
        .addEqualityGroup(wrap(new BsonObjectId("\u00e9a")))

        // geo points
        .addEqualityGroup(wrap(new GeoPoint(-90, -180)))
        .addEqualityGroup(wrap(new GeoPoint(-90, 0)))
        .addEqualityGroup(wrap(new GeoPoint(-90, 180)))
        .addEqualityGroup(wrap(new GeoPoint(0, -180)))
        .addEqualityGroup(wrap(new GeoPoint(0, 0)))
        .addEqualityGroup(wrap(new GeoPoint(0, 180)))
        .addEqualityGroup(wrap(new GeoPoint(1, -180)))
        .addEqualityGroup(wrap(new GeoPoint(1, 0)))
        .addEqualityGroup(wrap(new GeoPoint(1, 180)))
        .addEqualityGroup(wrap(new GeoPoint(90, -180)))
        .addEqualityGroup(wrap(new GeoPoint(90, 0)))
        .addEqualityGroup(wrap(new GeoPoint(90, 180)))

        // regex
        .addEqualityGroup(wrap(new RegexValue("^foo", "i")))
        .addEqualityGroup(wrap(new RegexValue("^foo", "m")))
        .addEqualityGroup(wrap(new RegexValue("^zoo", "i")))
        // latin small letter e + combining acute accent + latin small letter b
        .addEqualityGroup(wrap(new RegexValue("e\u0301b", "i")))
        .addEqualityGroup(wrap(new RegexValue("æ", "i")))
        // latin small letter e with acute accent + latin small letter a
        .addEqualityGroup(wrap(new RegexValue("\u00e9a", "i")))

        // arrays
        .addEqualityGroup(wrap(Arrays.asList("bar")))
        .addEqualityGroup(wrap(Arrays.asList("foo", 1)))
        .addEqualityGroup(wrap(Arrays.asList("foo", 2)))
        .addEqualityGroup(wrap(Arrays.asList("foo", "0")))

        // vector
        .addEqualityGroup(wrap(FieldValue.vector(new double[] {})))
        .addEqualityGroup(wrap(FieldValue.vector(new double[] {100})))
        .addEqualityGroup(wrap(FieldValue.vector(new double[] {1, 2, 3})))
        .addEqualityGroup(wrap(FieldValue.vector(new double[] {1, 3, 2})))

        // objects
        .addEqualityGroup(wrap(map("bar", 0)))
        .addEqualityGroup(wrap(map("bar", 0, "foo", 1)))
        .addEqualityGroup(wrap(map("foo", 1)))
        .addEqualityGroup(wrap(map("foo", 2)))
        .addEqualityGroup(wrap(map("foo", "0")))

        // MaxKey is last
        .addEqualityGroup(wrap(MaxKey.instance()))
        .testCompare();
  }

  @Test
  public void testLowerBound() {
    new ComparatorTester()
        // lower bound of null is null
        .addEqualityGroup(wrap(getLowerBound(TestUtil.wrap((Object) null))), wrap((Object) null))

        // lower bound of MinKey is MinKey
        .addEqualityGroup(
            wrap(getLowerBound(TestUtil.wrap(MinKey.instance()))), wrap(MinKey.instance()))

        // booleans
        .addEqualityGroup(wrap(false), wrap(getLowerBound(TestUtil.wrap(true))))
        .addEqualityGroup(wrap(true))

        // numbers
        // Note: 32-bit,64-bit integers and 128-bit decimals shares the same lower bound
        .addEqualityGroup(
            wrap(getLowerBound(TestUtil.wrap(1.0))),
            wrap(Double.NaN),
            wrap(getLowerBound(TestUtil.wrap(new Int32Value(1)))),
            wrap(getLowerBound(TestUtil.wrap(new Decimal128Value("1")))))
        .addEqualityGroup(wrap(Double.NEGATIVE_INFINITY))
        .addEqualityGroup(wrap(Long.MIN_VALUE))

        // dates
        .addEqualityGroup(wrap(getLowerBound(TestUtil.wrap(date1))))
        .addEqualityGroup(wrap(date1))

        // bson timestamps
        .addEqualityGroup(
            wrap(getLowerBound(TestUtil.wrap(new BsonTimestamp(4294967295L, 4294967295L)))),
            wrap(new BsonTimestamp(0, 0)))
        .addEqualityGroup(wrap(new BsonTimestamp(1, 1)))

        // strings
        .addEqualityGroup(wrap(getLowerBound(TestUtil.wrap("foo"))), wrap(""))
        .addEqualityGroup(wrap("\000"))

        // blobs
        .addEqualityGroup(wrap(getLowerBound(TestUtil.wrap(blob(1, 2, 3)))), wrap(blob()))
        .addEqualityGroup(wrap(blob(0)))

        // bson binary data
        .addEqualityGroup(
            wrap(getLowerBound(TestUtil.wrap(BsonBinaryData.fromBytes(128, new byte[] {1, 2, 3})))),
            wrap(BsonBinaryData.fromBytes(0, new byte[] {})),
            wrap(BsonBinaryData.fromByteString((byte) 0, ByteString.EMPTY)))
        .addEqualityGroup(wrap(BsonBinaryData.fromBytes(0, new byte[] {0})))

        // resource names
        .addEqualityGroup(
            wrap(getLowerBound(wrapRef(dbId("foo", "bar"), key("x/y")))),
            wrap(wrapRef(dbId("", ""), key(""))))
        .addEqualityGroup(wrap(wrapRef(dbId("", ""), key("a/a"))))

        // bson object ids
        .addEqualityGroup(
            wrap(getLowerBound(TestUtil.wrap(new BsonObjectId("zzz")))), wrap(new BsonObjectId("")))
        .addEqualityGroup(wrap(new BsonObjectId("a")))

        // geo points
        .addEqualityGroup(
            wrap(getLowerBound(TestUtil.wrap(new GeoPoint(-90, 0)))), wrap(new GeoPoint(-90, -180)))
        .addEqualityGroup(wrap(new GeoPoint(-90, 0)))

        // regular expressions
        .addEqualityGroup(
            wrap(getLowerBound(TestUtil.wrap(new RegexValue("^foo", "i")))),
            wrap(new RegexValue("", "")))
        .addEqualityGroup(wrap(new RegexValue("^foo", "i")))

        // arrays
        .addEqualityGroup(
            wrap(getLowerBound(TestUtil.wrap(Collections.singletonList(false)))),
            wrap(Collections.emptyList()))
        .addEqualityGroup(wrap(Collections.singletonList(false)))

        // vectors
        .addEqualityGroup(
            wrap(
                getLowerBound(
                    TestUtil.wrap(
                        map("__type__", "__vector__", "value", Collections.singletonList(1.0))))),
            wrap(map("__type__", "__vector__", "value", new LinkedList<Double>())))
        .addEqualityGroup(
            wrap(map("__type__", "__vector__", "value", Collections.singletonList(1.0))))

        // objects
        .addEqualityGroup(wrap(getLowerBound(TestUtil.wrap(map("foo", "bar")))), wrap(map()))

        // maxKey
        .addEqualityGroup(wrap(MaxKey.instance()))
        .testCompare();
  }

  @Test
  public void testUpperBound() {
    new ComparatorTester()
        // null first
        .addEqualityGroup(wrap((Object) null))

        // upper value of null is MinKey
        .addEqualityGroup(
            wrap(getUpperBound(TestUtil.wrap((Object) null))), wrap(MinKey.instance()))

        // upper value of MinKey is boolean `false`
        .addEqualityGroup(wrap(false), wrap(getUpperBound(TestUtil.wrap(MinKey.instance()))))

        // booleans
        .addEqualityGroup(wrap(true))
        .addEqualityGroup(wrap(getUpperBound(TestUtil.wrap(false))), wrap(Double.NaN))

        // numbers
        .addEqualityGroup(wrap(new Int32Value(2147483647))) // largest int32 value
        .addEqualityGroup(wrap(Long.MAX_VALUE))
        .addEqualityGroup(wrap(Double.POSITIVE_INFINITY))
        .addEqualityGroup(
            wrap(getUpperBound(TestUtil.wrap(0))),
            wrap(getUpperBound(TestUtil.wrap(new Int32Value(0)))),
            wrap(getUpperBound(TestUtil.wrap(new Decimal128Value("-0.0")))))

        // dates
        .addEqualityGroup(wrap(date1))
        .addEqualityGroup(wrap(getUpperBound(TestUtil.wrap(date1))))

        // bson timestamps
        .addEqualityGroup(
            wrap(new BsonTimestamp(4294967295L, 4294967295L))) // largest bson timestamp value
        .addEqualityGroup(wrap(getUpperBound(TestUtil.wrap(new BsonTimestamp(1, 1)))))

        // strings
        .addEqualityGroup(wrap("\000"))
        .addEqualityGroup(wrap(getUpperBound(TestUtil.wrap("\000"))))

        // blobs
        .addEqualityGroup(wrap(blob(255)))
        .addEqualityGroup(wrap(getUpperBound(TestUtil.wrap(blob(255)))))

        // bson binary data
        .addEqualityGroup(wrap(BsonBinaryData.fromBytes(128, new byte[] {1, 2})))
        .addEqualityGroup(
            wrap(getUpperBound(TestUtil.wrap(BsonBinaryData.fromBytes(0, new byte[] {})))))

        // resource names
        .addEqualityGroup(wrap(wrapRef(dbId("", ""), key("a/a"))))
        .addEqualityGroup(wrap(getUpperBound(wrapRef(dbId("", ""), key("a/a")))))

        // bson object ids
        .addEqualityGroup(wrap(new BsonObjectId("zzz")))
        .addEqualityGroup(wrap(getUpperBound(TestUtil.wrap(new BsonObjectId("a")))))

        // geo points
        .addEqualityGroup(wrap(new GeoPoint(90, 180)))
        .addEqualityGroup(wrap(getUpperBound(TestUtil.wrap(new GeoPoint(90, 180)))))

        // regular expressions
        .addEqualityGroup(wrap(new RegexValue("^foo", "i")))
        .addEqualityGroup(wrap(getUpperBound(TestUtil.wrap(new RegexValue("", "")))))

        // arrays
        .addEqualityGroup(wrap(Collections.singletonList(false)))
        .addEqualityGroup(wrap(getUpperBound(TestUtil.wrap(Collections.singletonList(false)))))

        // vectors
        .addEqualityGroup(
            wrap(map("__type__", "__vector__", "value", Collections.singletonList(1.0))))
        .addEqualityGroup(
            wrap(
                getUpperBound(
                    TestUtil.wrap(
                        map("__type__", "__vector__", "value", Collections.singletonList(1.0))))))

        // objects
        .addEqualityGroup(wrap(map("a", "b")))

        // upper value of objects is MaxKey
        .addEqualityGroup(
            wrap(getUpperBound(TestUtil.wrap(map("a", "b")))), wrap(MaxKey.instance()))
        .testCompare();
  }

  @Test
  public void testCanonicalIds() {
    assertCanonicalId(TestUtil.wrap(null), "null");
    assertCanonicalId(TestUtil.wrap(true), "true");
    assertCanonicalId(TestUtil.wrap(false), "false");
    assertCanonicalId(TestUtil.wrap(1), "1");
    assertCanonicalId(TestUtil.wrap(1.0), "1.0");
    assertCanonicalId(TestUtil.wrap(new Timestamp(30, 1000)), "time(30,1000)");
    assertCanonicalId(TestUtil.wrap("a"), "a");
    assertCanonicalId(TestUtil.wrap(blob(1, 2, 3)), "010203");
    assertCanonicalId(wrapRef(dbId("p1", "d1"), key("c1/doc1")), "c1/doc1");
    assertCanonicalId(TestUtil.wrap(new GeoPoint(30, 60)), "geo(30.0,60.0)");
    assertCanonicalId(TestUtil.wrap(Arrays.asList(1, 2, 3)), "[1,2,3]");
    assertCanonicalId(TestUtil.wrap(map("a", 1, "b", 2, "c", "3")), "{a:1,b:2,c:3}");
    assertCanonicalId(
        TestUtil.wrap(map("a", Arrays.asList("b", map("c", new GeoPoint(30, 60))))),
        "{a:[b,{c:geo(30.0,60.0)}]}");

    assertCanonicalId(TestUtil.wrap(new RegexValue("a", "b")), "{__regex__:{options:b,pattern:a}}");

    assertCanonicalId(TestUtil.wrap(new BsonObjectId("foo")), "{__oid__:foo}");
    assertCanonicalId(
        TestUtil.wrap(new BsonTimestamp(1, 2)), "{__request_timestamp__:{increment:2,seconds:1}}");
    assertCanonicalId((TestUtil.wrap(new Int32Value(1))), "{__int__:1}");
    assertCanonicalId(TestUtil.wrap(new Decimal128Value("1.2e3")), "{__decimal128__:1.2e3}");
    assertCanonicalId(
        TestUtil.wrap(BsonBinaryData.fromBytes(1, new byte[] {1, 2, 3})), "{__binary__:01010203}");
    assertCanonicalId(
        TestUtil.wrap(BsonBinaryData.fromBytes(128, new byte[] {1, 2, 3})),
        "{__binary__:80010203}");
    assertCanonicalId(TestUtil.wrap(MinKey.instance()), "{__min__:null}");
    assertCanonicalId(TestUtil.wrap(MaxKey.instance()), "{__max__:null}");
  }

  @Test
  public void testObjectCanonicalIdsIgnoreSortOrder() {
    assertCanonicalId(TestUtil.wrap(map("a", 1, "b", 2, "c", "3")), "{a:1,b:2,c:3}");
    assertCanonicalId(TestUtil.wrap(map("c", 3, "b", 2, "a", "1")), "{a:1,b:2,c:3}");
  }

  private void assertCanonicalId(Value proto, String expectedCanonicalId) {
    assertEquals(expectedCanonicalId, Values.canonicalId(proto));
  }

  @Test
  public void DetectsBsonTypesCorrectly() {
    Value minKeyValue = TestUtil.wrap(MinKey.instance());
    Value maxKeyValue = TestUtil.wrap(MaxKey.instance());
    Value int32Value = TestUtil.wrap(new Int32Value(1));
    Value decimal128 = TestUtil.wrap(new Decimal128Value("1.2e3"));
    Value regexValue = TestUtil.wrap(new RegexValue("^foo", "i"));
    Value bsonTimestampValue = TestUtil.wrap(new BsonTimestamp(1, 2));
    Value bsonObjectIdValue = TestUtil.wrap(new BsonObjectId("foo"));
    Value bsonBinaryDataValue1 = TestUtil.wrap(BsonBinaryData.fromBytes(1, new byte[] {}));
    Value bsonBinaryDataValue2 = TestUtil.wrap(BsonBinaryData.fromBytes(1, new byte[] {1, 2, 4}));

    assertTrue(Values.isMinKey(minKeyValue));
    assertFalse(Values.isMinKey(maxKeyValue));
    assertFalse(Values.isMinKey(int32Value));
    assertFalse(Values.isMinKey(decimal128));
    assertFalse(Values.isMinKey(regexValue));
    assertFalse(Values.isMinKey(bsonTimestampValue));
    assertFalse(Values.isMinKey(bsonObjectIdValue));
    assertFalse(Values.isMinKey(bsonBinaryDataValue1));
    assertFalse(Values.isMinKey(bsonBinaryDataValue2));

    assertFalse(Values.isMaxKey(minKeyValue));
    assertTrue(Values.isMaxKey(maxKeyValue));
    assertFalse(Values.isMaxKey(int32Value));
    assertFalse(Values.isMaxKey(decimal128));
    assertFalse(Values.isMaxKey(regexValue));
    assertFalse(Values.isMaxKey(bsonTimestampValue));
    assertFalse(Values.isMaxKey(bsonObjectIdValue));
    assertFalse(Values.isMaxKey(bsonBinaryDataValue1));
    assertFalse(Values.isMaxKey(bsonBinaryDataValue2));

    assertFalse(Values.isInt32Value(minKeyValue));
    assertFalse(Values.isInt32Value(maxKeyValue));
    assertTrue(Values.isInt32Value(int32Value));
    assertFalse(Values.isInt32Value(decimal128));
    assertFalse(Values.isInt32Value(regexValue));
    assertFalse(Values.isInt32Value(bsonTimestampValue));
    assertFalse(Values.isInt32Value(bsonObjectIdValue));
    assertFalse(Values.isInt32Value(bsonBinaryDataValue1));
    assertFalse(Values.isInt32Value(bsonBinaryDataValue2));

    assertFalse(Values.isDecimal128Value(minKeyValue));
    assertFalse(Values.isDecimal128Value(maxKeyValue));
    assertFalse(Values.isDecimal128Value(int32Value));
    assertTrue(Values.isDecimal128Value(decimal128));
    assertFalse(Values.isDecimal128Value(regexValue));
    assertFalse(Values.isDecimal128Value(bsonTimestampValue));
    assertFalse(Values.isDecimal128Value(bsonObjectIdValue));
    assertFalse(Values.isDecimal128Value(bsonBinaryDataValue1));
    assertFalse(Values.isDecimal128Value(bsonBinaryDataValue2));

    assertFalse(Values.isRegexValue(minKeyValue));
    assertFalse(Values.isRegexValue(maxKeyValue));
    assertFalse(Values.isRegexValue(int32Value));
    assertFalse(Values.isRegexValue(decimal128));
    assertTrue(Values.isRegexValue(regexValue));
    assertFalse(Values.isRegexValue(bsonTimestampValue));
    assertFalse(Values.isRegexValue(bsonObjectIdValue));
    assertFalse(Values.isRegexValue(bsonBinaryDataValue1));
    assertFalse(Values.isRegexValue(bsonBinaryDataValue2));

    assertFalse(Values.isBsonTimestamp(minKeyValue));
    assertFalse(Values.isBsonTimestamp(maxKeyValue));
    assertFalse(Values.isBsonTimestamp(int32Value));
    assertFalse(Values.isBsonTimestamp(decimal128));
    assertFalse(Values.isBsonTimestamp(regexValue));
    assertTrue(Values.isBsonTimestamp(bsonTimestampValue));
    assertFalse(Values.isBsonTimestamp(bsonObjectIdValue));
    assertFalse(Values.isBsonTimestamp(bsonBinaryDataValue1));
    assertFalse(Values.isBsonTimestamp(bsonBinaryDataValue2));

    assertFalse(Values.isBsonObjectId(minKeyValue));
    assertFalse(Values.isBsonObjectId(maxKeyValue));
    assertFalse(Values.isBsonObjectId(int32Value));
    assertFalse(Values.isBsonObjectId(decimal128));
    assertFalse(Values.isBsonObjectId(regexValue));
    assertFalse(Values.isBsonObjectId(bsonTimestampValue));
    assertTrue(Values.isBsonObjectId(bsonObjectIdValue));
    assertFalse(Values.isBsonObjectId(bsonBinaryDataValue1));
    assertFalse(Values.isBsonObjectId(bsonBinaryDataValue2));

    assertFalse(Values.isBsonBinaryData(minKeyValue));
    assertFalse(Values.isBsonBinaryData(maxKeyValue));
    assertFalse(Values.isBsonBinaryData(int32Value));
    assertFalse(Values.isBsonBinaryData(decimal128));
    assertFalse(Values.isBsonBinaryData(regexValue));
    assertFalse(Values.isBsonBinaryData(bsonTimestampValue));
    assertFalse(Values.isBsonBinaryData(bsonObjectIdValue));
    assertTrue(Values.isBsonBinaryData(bsonBinaryDataValue1));
    assertTrue(Values.isBsonBinaryData(bsonBinaryDataValue2));

    assertEquals(Values.detectMapRepresentation(minKeyValue), MapRepresentation.MIN_KEY);
    assertEquals(Values.detectMapRepresentation(maxKeyValue), MapRepresentation.MAX_KEY);
    assertEquals(Values.detectMapRepresentation(int32Value), MapRepresentation.INT32);
    assertEquals(Values.detectMapRepresentation(decimal128), MapRepresentation.DECIMAL128);
    assertEquals(Values.detectMapRepresentation(regexValue), MapRepresentation.REGEX);
    assertEquals(
        Values.detectMapRepresentation(bsonTimestampValue), MapRepresentation.BSON_TIMESTAMP);
    assertEquals(
        Values.detectMapRepresentation(bsonObjectIdValue), MapRepresentation.BSON_OBJECT_ID);
    assertEquals(
        Values.detectMapRepresentation(bsonBinaryDataValue1), MapRepresentation.BSON_BINARY);
    assertEquals(
        Values.detectMapRepresentation(bsonBinaryDataValue2), MapRepresentation.BSON_BINARY);
  }

  /** Small helper class that uses ProtoValues for equals() and compareTo(). */
  static class EqualsWrapper implements Comparable<EqualsWrapper> {
    final Value proto;

    EqualsWrapper(Value proto) {
      this.proto = proto;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof EqualsWrapper && Values.equals(proto, ((EqualsWrapper) o).proto);
    }

    @Override
    public int hashCode() {
      return proto.hashCode();
    }

    @Override
    public int compareTo(EqualsWrapper o) {
      return Values.compare(proto, o.proto);
    }
  }

  private EqualsWrapper wrap(Value value) {
    return new EqualsWrapper(value);
  }

  private EqualsWrapper wrap(Object entry) {
    return new EqualsWrapper(TestUtil.wrap(entry));
  }
}
