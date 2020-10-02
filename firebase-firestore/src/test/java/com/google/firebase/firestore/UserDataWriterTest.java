// Copyright 2019 Google LLC
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

import static com.google.firebase.firestore.testutil.TestUtil.blob;
import static com.google.firebase.firestore.testutil.TestUtil.field;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.testutil.TestUtil.ref;
import static com.google.firebase.firestore.testutil.TestUtil.wrap;
import static com.google.firebase.firestore.testutil.TestUtil.wrapObject;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.model.DatabaseId;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.ObjectValue;
import com.google.firebase.firestore.model.Values;
import com.google.firestore.v1.ArrayValue;
import com.google.firestore.v1.Value;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class UserDataWriterTest {

  private final UserDataWriter writer =
      new UserDataWriter(TestUtil.firestore(), DocumentSnapshot.ServerTimestampBehavior.DEFAULT);

  @Test
  public void testConvertsNullValue() {
    Value value = wrap(null);
    Object convertedValue = convertValue(value);
    assertNull(convertedValue);
  }

  @Test
  public void testConvertsBooleanValue() {
    List<Boolean> testCases = asList(true, false);
    for (Boolean b : testCases) {
      Value value = wrap(b);
      assertValueType(Value.ValueTypeCase.BOOLEAN_VALUE, value);
      Object convertedValue = convertValue(value);
      assertEquals(b, convertedValue);
    }
  }

  @Test
  public void testConvertsIntegerValue() {
    List<Integer> testCases = asList(Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE);
    for (Integer i : testCases) {
      Value value = wrap(i);
      assertValueType(Value.ValueTypeCase.INTEGER_VALUE, value);
      Object convertedValue = convertValue(value);
      assertEquals(i.longValue(), convertedValue);
    }
  }

  @SuppressWarnings("UnnecessaryBoxing")
  @Test
  public void testConvertsLongValue() {
    List<Long> testCases =
        asList(
            Long.MIN_VALUE,
            Long.valueOf(Integer.MIN_VALUE),
            -1L,
            0L,
            1L,
            Long.valueOf(Integer.MAX_VALUE),
            Long.MAX_VALUE);
    for (Long l : testCases) {
      Value value = wrap(l);
      assertValueType(Value.ValueTypeCase.INTEGER_VALUE, value);
      Object convertedValue = convertValue(value);
      assertEquals(l, convertedValue);
    }
  }

  @Test
  public void testConvertsFloatValue() {
    List<Float> testCases =
        asList(
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
      Value value = wrap(f);
      assertValueType(Value.ValueTypeCase.DOUBLE_VALUE, value);
      Object convertedValue = convertValue(value);
      assertEquals(f.doubleValue(), convertedValue);
    }
  }

  @SuppressWarnings("UnnecessaryBoxing")
  @Test
  public void testConvertsDoubleValue() {
    List<Double> testCases =
        asList(
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
      Value value = wrap(d);
      assertValueType(Value.ValueTypeCase.DOUBLE_VALUE, value);
      Object convertedValue = convertValue(value);
      assertEquals(d, convertedValue);
    }
  }

  @Test
  public void testConvertsDateValue() {
    UserDataWriter dateWriter =
        new UserDataWriter(TestUtil.firestore(), DocumentSnapshot.ServerTimestampBehavior.DEFAULT);
    List<Date> testCases = asList(new Date(0), new Date(1356048000000L));
    for (Date d : testCases) {
      Value value = wrap(d);
      assertValueType(Value.ValueTypeCase.TIMESTAMP_VALUE, value);
      Object convertedValue = dateWriter.convertValue(value);
      assertTrue(convertedValue instanceof Timestamp);
      assertEquals(d, ((Timestamp) convertedValue).toDate());
    }
  }

  @Test
  public void testConvertsTimestampValue() {
    List<Timestamp> testCases = asList(new Timestamp(0, 0), new Timestamp(1356048000L, 0));
    for (Timestamp t : testCases) {
      Value value = wrap(t);
      assertValueType(Value.ValueTypeCase.TIMESTAMP_VALUE, value);
      Object convertedValue = convertValue(value);
      assertEquals(t, convertedValue);
    }
  }

  @Test
  public void testConvertsStringValue() {
    List<String> testCases = asList("", "foo");
    for (String s : testCases) {
      Value value = wrap(s);
      Object convertedValue = convertValue(value);
      assertEquals(s, convertedValue);
    }
  }

  @Test
  public void testConvertsBlobValue() {
    List<Blob> testCases = asList(blob(1, 2, 3), blob(1, 2));
    for (Blob b : testCases) {
      Value value = wrap(b);
      Object convertedValue = convertValue(value);
      assertEquals(b, convertedValue);
    }
  }

  @Test
  public void testConvertsResourceName() {
    DatabaseId id = DatabaseId.forProject("project");
    List<DocumentReference> testCases = asList(ref("foo/bar"), ref("foo/baz"));
    for (DocumentReference docRef : testCases) {
      Value value = wrap(docRef);
      assertTrue(Values.isReferenceValue(value));
      assertEquals(
          TestAccessHelper.referenceKey(docRef), DocumentKey.fromName(value.getReferenceValue()));
      assertEquals(id, DatabaseId.fromName(value.getReferenceValue()));
    }
  }

  @Test
  public void testConvertsGeoPointValue() {
    List<GeoPoint> testCases = asList(new GeoPoint(1.24, 4.56), new GeoPoint(-20, 100));
    for (GeoPoint p : testCases) {
      Value value = wrap(p);
      Object convertedValue = convertValue(value);
      assertEquals(p, convertedValue);
    }
  }

  @Test
  public void testConvertsEmptyObjects() {
    assertEquals(wrapObject(), ObjectValue.emptyObject());
  }

  @Test
  public void testConvertsSimpleObjects() {
    // Guava doesn't like null values, so we create a copy of the Immutable map without
    // the null value and then add the null value later.
    Map<String, Object> actual = map("a", "foo", "b", 1, "c", true, "d", null);

    ObjectValue wrappedExpected =
        fromMap(
            "a", wrap("foo"),
            "b", wrap(1L),
            "c", wrap(true),
            "d", wrap(null));

    ObjectValue wrappedActual = wrapObject(actual);
    assertEquals(wrappedActual, wrappedExpected);
  }

  private static ObjectValue fromMap(Object... entries) {
    Map<String, Value> res = new HashMap<>();
    for (int i = 0; i < entries.length; i += 2) {
      res.put((String) entries[i], (Value) entries[i + 1]);
    }
    return ObjectValue.fromMap(res);
  }

  @Test
  public void testConvertsNestedObjects() {
    ObjectValue actual = wrapObject("a", map("b", map("c", "foo"), "d", true));
    ObjectValue.Builder expected = ObjectValue.newBuilder();
    expected.set(field("a.b.c"), wrap("foo"));
    expected.set(field("a.d"), wrap(true));
    assertEquals(expected.build(), actual);
  }

  @Test
  public void testConvertsLists() {
    ArrayValue.Builder expectedArray =
        ArrayValue.newBuilder().addValues(wrap("value")).addValues(wrap(true));
    Value actual = wrap(asList("value", true));
    assertTrue(Values.equals(Value.newBuilder().setArrayValue(expectedArray).build(), actual));
  }

  @Test
  public void testRejectsJavaArrays() {
    String[] array = {"foo", "bar"};
    try {
      wrap(array);
      fail("wrap should have failed");
    } catch (IllegalArgumentException e) {
      assertNotEquals(-1, e.getMessage().indexOf("use Lists instead"));
    }
  }

  private Object convertValue(Value value) {
    return writer.convertValue(value);
  }

  private void assertValueType(Value.ValueTypeCase booleanValue, Value value) {
    assertEquals(booleanValue, value.getValueTypeCase());
  }
}
