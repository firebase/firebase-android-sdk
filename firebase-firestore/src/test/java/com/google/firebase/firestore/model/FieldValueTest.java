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

import static com.google.firebase.firestore.testutil.TestUtil.blob;
import static com.google.firebase.firestore.testutil.TestUtil.dbId;
import static com.google.firebase.firestore.testutil.TestUtil.field;
import static com.google.firebase.firestore.testutil.TestUtil.key;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.testutil.TestUtil.ref;
import static com.google.firebase.firestore.testutil.TestUtil.wrap;
import static com.google.firebase.firestore.testutil.TestUtil.wrapObject;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.os.Parcel;
import com.google.common.testing.EqualsTester;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Blob;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.TestAccessHelper;
import com.google.firebase.firestore.model.value.ArrayValue;
import com.google.firebase.firestore.model.value.BlobValue;
import com.google.firebase.firestore.model.value.BooleanValue;
import com.google.firebase.firestore.model.value.DoubleValue;
import com.google.firebase.firestore.model.value.FieldValue;
import com.google.firebase.firestore.model.value.GeoPointValue;
import com.google.firebase.firestore.model.value.IntegerValue;
import com.google.firebase.firestore.model.value.NullValue;
import com.google.firebase.firestore.model.value.ObjectValue;
import com.google.firebase.firestore.model.value.ReferenceValue;
import com.google.firebase.firestore.model.value.ServerTimestampValue;
import com.google.firebase.firestore.model.value.StringValue;
import com.google.firebase.firestore.model.value.TimestampValue;
import com.google.firebase.firestore.testutil.ComparatorTester;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class FieldValueTest {
  private final Date date1;
  private final Date date2;

  public FieldValueTest() {
    // Create a couple date objects for use in tests.
    Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    calendar.set(2016, 5, 20, 10, 20, 0);
    date1 = calendar.getTime();

    calendar.set(2016, 10, 21, 15, 32, 0);
    date2 = calendar.getTime();
  }

  @Test
  public void testIntegerValueConversion() {
    List<Integer> testCases = Arrays.asList(Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE);
    for (Integer i : testCases) {
      FieldValue value = wrap(i);
      assertTrue(value instanceof IntegerValue);
      assertEquals(i.longValue(), value.value());
    }
  }

  @Test
  public void testLongValueConversion() {
    List<Long> testCases =
        Arrays.asList(
            Long.MIN_VALUE,
            Long.valueOf(Integer.MIN_VALUE),
            -1L,
            0L,
            1L,
            Long.valueOf(Integer.MAX_VALUE),
            Long.MAX_VALUE);
    for (Long l : testCases) {
      FieldValue value = wrap(l);
      assertTrue(value instanceof IntegerValue);
      assertEquals(l, value.value());
    }
  }

  @Test
  public void testFloatValueConversion() {
    List<Float> testCases =
        Arrays.asList(
            -Float.MAX_VALUE,
            Long.MIN_VALUE * 1.0f,
            -1.1f,
            -Float.MIN_VALUE,
            -0.0f,
            0.0f,
            Float.MIN_VALUE,
            Float.MIN_NORMAL,
            Long.MAX_VALUE * 1.0f,
            Float.MAX_VALUE);
    for (Float f : testCases) {
      FieldValue value = wrap(f);
      assertTrue(value instanceof DoubleValue);
      assertEquals(f.doubleValue(), value.value());
    }
  }

  @Test
  public void testDoubleValueConversion() {
    List<Double> testCases =
        Arrays.asList(
            Double.POSITIVE_INFINITY,
            -Double.MAX_VALUE,
            Double.valueOf(-Float.MAX_VALUE),
            Long.MIN_VALUE * 1.0,
            -1.1,
            Double.valueOf(-Float.MIN_VALUE),
            -Double.MIN_VALUE,
            -0.0,
            0.0,
            Double.valueOf(Float.MIN_VALUE),
            Double.MIN_VALUE,
            Double.valueOf(Float.MIN_NORMAL),
            Double.MIN_NORMAL,
            Long.MAX_VALUE * 1.0,
            Double.valueOf(Float.MAX_VALUE),
            Double.MAX_VALUE,
            Double.POSITIVE_INFINITY,
            Double.NaN);
    for (Double d : testCases) {
      FieldValue value = wrap(d);
      assertTrue(value instanceof DoubleValue);
      assertEquals(d, value.value());
    }
  }

  @Test
  public void testNullValueConversion() {
    FieldValue value = wrap(null);
    assertTrue(value instanceof NullValue);
    assertEquals(value.value(), null);
  }

  @Test
  public void testBooleanValueConversion() {
    List<Boolean> testCases = Arrays.asList(true, false);
    for (Boolean b : testCases) {
      FieldValue value = wrap(b);
      assertTrue(value instanceof BooleanValue);
      assertEquals(b, value.value());
    }
  }

  @Test
  public void testDateValueConversion() {
    List<Date> testCases = Arrays.asList(new Date(0), new Date(1356048000000L));
    for (Date d : testCases) {
      FieldValue value = wrap(d);
      assertTrue(value instanceof TimestampValue);
      Timestamp timestamp = (Timestamp) value.value();
      assertEquals(d, timestamp.toDate());
    }
  }

  @Test
  public void testTimestampValueConversion() {
    List<Timestamp> testCases = Arrays.asList(new Timestamp(0, 0), new Timestamp(1356048000L, 0));
    for (Timestamp d : testCases) {
      FieldValue value = wrap(d);
      assertTrue(value instanceof TimestampValue);
      assertTrue(value.value() instanceof Timestamp);
      assertEquals(d, value.value());
    }
  }

  @Test
  public void testGeoPointValueConversion() {
    List<GeoPoint> testCases = Arrays.asList(new GeoPoint(1.24, 4.56), new GeoPoint(-20, 100));
    for (GeoPoint p : testCases) {
      FieldValue value = wrap(p);
      assertTrue(value instanceof GeoPointValue);
      assertEquals(p, value.value());
    }
  }

  @Test
  public void testBlobValueConversion() {
    List<Blob> testCases = Arrays.asList(blob(1, 2, 3), blob(1, 2));
    for (Blob b : testCases) {
      FieldValue value = wrap(b);
      assertTrue(value instanceof BlobValue);
      assertEquals(b, value.value());
    }
  }

  @Test
  public void testResourceNameConversion() {
    DatabaseId id = DatabaseId.forProject("project");
    List<DocumentReference> testCases = Arrays.asList(ref("foo/bar"), ref("foo/baz"));
    for (DocumentReference docRef : testCases) {
      FieldValue value = wrap(docRef);
      assertTrue(value instanceof ReferenceValue);
      ReferenceValue ref = (ReferenceValue) value;
      assertEquals(TestAccessHelper.referenceKey(docRef), ref.value());
      assertEquals(id, ref.getDatabaseId());
    }
  }

  @Test
  public void testWrapsEmptyObjects() {
    assertEquals(wrap(new TreeMap<String, FieldValue>()), ObjectValue.emptyObject());
  }

  @Test
  public void testWrapsSimpleObjects() {
    // Guava doesn't like null values, so we create a copy of the Immutable map without
    // the null value and then add the null value later.
    Map<String, Object> actual = map("a", "foo", "b", 1, "c", true, "d", null);

    Map<String, FieldValue> expected =
        map(
            "a", StringValue.valueOf("foo"),
            "b", IntegerValue.valueOf(1L),
            "c", BooleanValue.valueOf(true),
            "d", NullValue.nullValue());

    FieldValue wrappedActual = wrapObject(actual);
    ObjectValue wrappedExpected = ObjectValue.fromMap(expected);
    assertEquals(wrappedActual, wrappedExpected);
  }

  static ObjectValue fromMap(Object... entries) {
    Map<String, FieldValue> res = new HashMap<>();
    for (int i = 0; i < entries.length; i += 2) {
      res.put((String) entries[i], (FieldValue) entries[i + 1]);
    }
    return ObjectValue.fromMap(res);
  }

  @Test
  public void testWrapsNestedObjects() {
    FieldValue actual = wrapObject("a", map("b", map("c", "foo"), "d", true));
    ObjectValue expected =
        fromMap(
            "a",
            fromMap(
                "b", fromMap("c", StringValue.valueOf("foo")), "d", BooleanValue.valueOf(true)));
    assertEquals(expected, actual);
  }

  @Test
  public void testExtractsFields() {
    FieldValue val = wrapObject("foo", map("a", 1, "b", true, "c", "string"));
    assertTrue(val instanceof ObjectValue);
    ObjectValue obj = (ObjectValue) val;
    assertTrue(obj.get(field("foo")) instanceof ObjectValue);
    assertEquals(wrap(1), obj.get(field("foo.a")));
    assertEquals(wrap(true), obj.get(field("foo.b")));
    assertEquals(wrap("string"), obj.get(field("foo.c")));

    assertNull(obj.get(field("foo.a.b")));
    assertNull(obj.get(field("bar")));
    assertNull(obj.get(field("bar.a")));
  }

  @Test
  public void testOverwritesExistingFields() {
    ObjectValue old = wrapObject("a", "old");
    ObjectValue mod = old.set(field("a"), wrap("mod"));
    assertNotEquals(old, mod);
    assertEquals(wrapObject("a", "old"), old);
    assertEquals(wrapObject("a", "mod"), mod);
  }

  @Test
  public void testAddsNewFields() {
    ObjectValue empty = ObjectValue.emptyObject();
    ObjectValue mod = empty.set(field("a"), wrap("mod"));
    assertEquals(wrap(new TreeMap<String, FieldValue>()), empty);
    assertEquals(wrapObject("a", "mod"), mod);

    ObjectValue old = mod;
    mod = old.set(field("b"), wrap(1));
    assertEquals(wrapObject("a", "mod"), old);
    assertEquals(wrapObject("a", "mod", "b", 1), mod);
  }

  @Test
  public void testImplicitlyCreatesObjects() {
    ObjectValue old = wrapObject("a", "old");
    ObjectValue mod = old.set(field("b.c.d"), wrap("mod"));

    assertNotEquals(old, mod);
    assertEquals(wrapObject("a", "old"), old);
    assertEquals(wrapObject("a", "old", "b", map("c", map("d", "mod"))), mod);
  }

  @Test
  public void testCanOverwritePrimitivesWithObjects() {
    ObjectValue old = wrapObject("a", map("b", "old"));
    ObjectValue mod = old.set(field("a"), wrapObject("b", "mod"));
    assertNotEquals(old, mod);
    assertEquals(wrapObject("a", map("b", "old")), old);
    assertEquals(wrapObject("a", map("b", "mod")), mod);
  }

  @Test
  public void testAddsToNestedObjects() {
    ObjectValue old = wrapObject("a", map("b", "old"));
    ObjectValue mod = old.set(field("a.c"), wrap("mod"));
    assertNotEquals(old, mod);
    assertEquals(wrapObject("a", map("b", "old")), old);
    assertEquals(wrapObject("a", map("b", "old", "c", "mod")), mod);
  }

  @Test
  public void testDeletesKey() {
    ObjectValue old = wrapObject("a", 1, "b", 2);
    ObjectValue mod = old.delete(field("a"));

    assertNotEquals(old, mod);
    assertEquals(wrapObject("a", 1, "b", 2), old);
    assertEquals(wrapObject("b", 2), mod);

    ObjectValue empty = mod.delete(field("b"));
    assertNotEquals(mod, empty);
    assertEquals(wrapObject("b", 2), mod);
    assertEquals(ObjectValue.emptyObject(), empty);
  }

  @Test
  public void testDeletesHandleMissingKeys() {
    ObjectValue old = wrapObject("a", map("b", 1, "c", 2));
    ObjectValue mod = old.delete(field("b"));
    assertEquals(mod, old);
    assertEquals(wrapObject("a", map("b", 1, "c", 2)), mod);

    mod = old.delete(field("a.d"));
    assertEquals(mod, old);
    assertEquals(wrapObject("a", map("b", 1, "c", 2)), mod);

    mod = old.delete(field("a.b.c"));
    assertEquals(mod, old);
    assertEquals(wrapObject("a", map("b", 1, "c", 2)), mod);
  }

  @Test
  public void testDeletesNestedKeys() {
    Map<String, Object> orig = map("a", map("b", 1, "c", map("d", 2, "e", 3)));
    ObjectValue old = wrapObject(orig);
    ObjectValue mod = old.delete(field("a.c.d"));

    assertNotEquals(mod, old);
    assertEquals(wrapObject(orig), old);

    Map<String, Object> second = map("a", map("b", 1, "c", map("e", 3)));
    assertEquals(wrapObject(second), mod);

    old = mod;
    mod = old.delete(field("a.c"));

    assertNotEquals(old, mod);
    assertEquals(wrapObject(second), old);

    Map<String, Object> third = map("a", map("b", 1));
    assertEquals(wrapObject(third), mod);

    old = mod;
    mod = old.delete(field("a"));

    assertNotEquals(old, mod);
    assertEquals(wrapObject(third), old);
    assertEquals(ObjectValue.emptyObject(), mod);
  }

  @Test
  public void testArrays() {
    ArrayValue expected =
        ArrayValue.fromList(
            Arrays.asList(StringValue.valueOf("value"), BooleanValue.valueOf(true)));
    FieldValue actual = wrap(Arrays.asList("value", true));
    assertEquals(expected, actual);
  }

  @Test
  public void testArraysFail() {
    String[] array = {"foo", "bar"};
    try {
      wrap(array);
      fail("wrap should have failed");
    } catch (IllegalArgumentException e) {
      assertNotEquals(-1, e.getMessage().indexOf("use Lists instead"));
    }
  }

  @Test
  public void testValueEquality() {
    new EqualsTester()
        .addEqualityGroup(wrap(true), BooleanValue.valueOf(true))
        .addEqualityGroup(wrap(false), BooleanValue.valueOf(false))
        .addEqualityGroup(wrap(null), NullValue.nullValue())
        .addEqualityGroup(
            wrap(0.0 / 0.0), wrap(Double.longBitsToDouble(0x7ff8000000000000L)), DoubleValue.NaN)
        // -0.0 and 0.0 compareTo the same but are not equal.
        .addEqualityGroup(wrap(-0.0))
        .addEqualityGroup(wrap(0.0))
        .addEqualityGroup(wrap(1), IntegerValue.valueOf(1L))
        // Doubles and Longs aren't equal.
        .addEqualityGroup(wrap(1.0), DoubleValue.valueOf(1.0))
        .addEqualityGroup(wrap(1.1), DoubleValue.valueOf(1.1))
        .addEqualityGroup(wrap(blob(0, 1, 2)), BlobValue.valueOf(blob(0, 1, 2)))
        .addEqualityGroup(wrap(blob(0, 1)))
        .addEqualityGroup(wrap("string"), StringValue.valueOf("string"))
        .addEqualityGroup(StringValue.valueOf("strin"))
        // latin small letter e + combining acute accent
        .addEqualityGroup(StringValue.valueOf("e\u0301b"))
        // latin small letter e with acute accent
        .addEqualityGroup(StringValue.valueOf("\u00e9a"))
        .addEqualityGroup(wrap(date1), TimestampValue.valueOf(new Timestamp(date1)))
        .addEqualityGroup(TimestampValue.valueOf(new Timestamp(date2)))
        // NOTE: ServerTimestampValues can't be parsed via wrap().
        .addEqualityGroup(
            new ServerTimestampValue(new Timestamp(date1), null),
            new ServerTimestampValue(new Timestamp(date1), null))
        .addEqualityGroup(new ServerTimestampValue(new Timestamp(date2), null))
        .addEqualityGroup(wrap(new GeoPoint(0, 1)), GeoPointValue.valueOf(new GeoPoint(0, 1)))
        .addEqualityGroup(GeoPointValue.valueOf(new GeoPoint(1, 0)))
        .addEqualityGroup(
            wrap(ref("coll/doc1")), ReferenceValue.valueOf(dbId("project"), key("coll/doc1")))
        .addEqualityGroup(ReferenceValue.valueOf(dbId("project", "bar"), key("coll/doc2")))
        .addEqualityGroup(ReferenceValue.valueOf(dbId("project", "baz"), key("coll/doc2")))
        .addEqualityGroup(wrap(Arrays.asList("foo", "bar")), wrap(Arrays.asList("foo", "bar")))
        .addEqualityGroup(wrap(Arrays.asList("foo", "bar", "baz")))
        .addEqualityGroup(wrap(Arrays.asList("foo")))
        .addEqualityGroup(wrapObject(map("bar", 1, "foo", 2)), wrapObject(map("foo", 2, "bar", 1)))
        .addEqualityGroup(wrapObject(map("bar", 2, "foo", 1)))
        .addEqualityGroup(wrapObject(map("bar", 1)))
        .addEqualityGroup(wrapObject(map("foo", 1)))
        .testEquals();
  }

  @Test
  public void testValueOrdering() {
    new ComparatorTester()
        // do not test for compatibility with equals(): +0/-0 break it.
        .permitInconsistencyWithEquals()

        // null first
        .addEqualityGroup(wrap(null))

        // booleans
        .addEqualityGroup(wrap(false))
        .addEqualityGroup(wrap(true))

        // numbers
        .addEqualityGroup(wrap(Double.NaN))
        .addEqualityGroup(wrap(Double.NEGATIVE_INFINITY))
        .addEqualityGroup(wrap(-Double.MAX_VALUE))
        .addEqualityGroup(wrap(Long.MIN_VALUE))
        .addEqualityGroup(wrap(-1.1))
        .addEqualityGroup(wrap(-1.0))
        .addEqualityGroup(wrap(-Double.MIN_NORMAL))
        .addEqualityGroup(wrap(-Double.MIN_VALUE))
        // Zeros all compare the same.
        .addEqualityGroup(wrap(-0.0), wrap(0.0), wrap(0L))
        .addEqualityGroup(wrap(Double.MIN_VALUE))
        .addEqualityGroup(wrap(Double.MIN_NORMAL))
        .addEqualityGroup(wrap(0.1))
        // Doubles and Longs compareTo() the same.
        .addEqualityGroup(wrap(1.0), wrap(1L))
        .addEqualityGroup(wrap(1.1))
        .addEqualityGroup(wrap(Long.MAX_VALUE))
        .addEqualityGroup(wrap(Double.MAX_VALUE))
        .addEqualityGroup(wrap(Double.POSITIVE_INFINITY))

        // dates
        .addEqualityGroup(wrap(date1))
        .addEqualityGroup(wrap(date2))

        // server timestamps come after all concrete timestamps.
        // NOTE: server timestamps can't be parsed with wrap().
        .addEqualityGroup(new ServerTimestampValue(new Timestamp(date1), null))
        .addEqualityGroup(new ServerTimestampValue(new Timestamp(date2), null))

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

        // resource names
        .addEqualityGroup(ReferenceValue.valueOf(dbId("p1", "d1"), key("c1/doc1")))
        .addEqualityGroup(ReferenceValue.valueOf(dbId("p1", "d1"), key("c1/doc2")))
        .addEqualityGroup(ReferenceValue.valueOf(dbId("p1", "d1"), key("c10/doc1")))
        .addEqualityGroup(ReferenceValue.valueOf(dbId("p1", "d1"), key("c2/doc1")))
        .addEqualityGroup(ReferenceValue.valueOf(dbId("p1", "d2"), key("c1/doc1")))
        .addEqualityGroup(ReferenceValue.valueOf(dbId("p2", "d1"), key("c1/doc1")))

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

        // arrays
        .addEqualityGroup(wrap(Arrays.asList("bar")))
        .addEqualityGroup(wrap(Arrays.asList("foo", 1)))
        .addEqualityGroup(wrap(Arrays.asList("foo", 2)))
        .addEqualityGroup(wrap(Arrays.asList("foo", "0")))

        // objects
        .addEqualityGroup(wrapObject(map("bar", 0)))
        .addEqualityGroup(wrapObject(map("bar", 0, "foo", 1)))
        .addEqualityGroup(wrapObject(map("foo", 1)))
        .addEqualityGroup(wrapObject(map("foo", 2)))
        .addEqualityGroup(wrapObject(map("foo", "0")))
        .testCompare();
  }

  @Test
  public void testTimestampParcelable() {
    Timestamp timestamp = new Timestamp(1234L, 4567);

    // Write the Timestamp into the Parcel and then rewind the data position for reading.
    Parcel parcel = Parcel.obtain();
    timestamp.writeToParcel(parcel, 0);
    parcel.setDataPosition(0);

    Timestamp recreated = Timestamp.CREATOR.createFromParcel(parcel);
    assertEquals(timestamp, recreated);

    parcel.recycle();
  }
}
