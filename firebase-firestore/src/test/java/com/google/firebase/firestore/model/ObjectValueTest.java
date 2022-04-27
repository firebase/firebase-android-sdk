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

import static com.google.firebase.firestore.testutil.TestUtil.field;
import static com.google.firebase.firestore.testutil.TestUtil.fieldMask;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.testutil.TestUtil.wrap;
import static com.google.firebase.firestore.testutil.TestUtil.wrapObject;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.firebase.firestore.model.mutation.FieldMask;
import com.google.firestore.v1.MapValue;
import com.google.firestore.v1.Value;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class ObjectValueTest {

  private String fooString = "foo";
  private Value fooValue = wrap(fooString);
  private String barString = "bar";
  private Value barValue = wrap(barString);
  private Value emptyObject = Value.newBuilder().setMapValue(MapValue.getDefaultInstance()).build();

  @Test
  public void testSupportsEmptyObjectValues() {
    ObjectValue objectValue = new ObjectValue();
    assertEquals(new ObjectValue(), objectValue);
  }

  @Test
  public void testExtractsFields() {
    ObjectValue obj = wrapObject("foo", map("a", 1, "b", true, "c", "string"));
    assertTrue(Values.isMapValue(obj.get(field("foo"))));
    assertEquals(wrap(1), obj.get(field("foo.a")));
    assertEquals(wrap(true), obj.get(field("foo.b")));
    assertEquals(wrap("string"), obj.get(field("foo.c")));

    assertNull(obj.get(field("foo.a.b")));
    assertNull(obj.get(field("bar")));
    assertNull(obj.get(field("bar.a")));
  }

  @Test
  public void testExtractsFieldMask() {
    ObjectValue val =
        wrapObject(
            "a",
            "b",
            "map",
            map("a", 1, "b", true, "c", "string", "nested", map("d", "e")),
            "emptymap",
            map());
    FieldMask mask = val.getFieldMask();
    assertEquals(fieldMask("a", "map.a", "map.b", "map.c", "map.nested.d", "emptymap"), mask);
  }

  @Test
  public void testOverwritesExistingFields() {
    ObjectValue objectValue = wrapObject("a", "old");
    assertEquals(wrapObject("a", "old"), objectValue);
    objectValue.set(field("a"), wrap("mod"));
    assertEquals(wrapObject("a", "mod"), objectValue);
  }

  @Test
  public void testOverwritesNestedFields() {
    ObjectValue objectValue = wrapObject("a", map("b", fooString, "c", map("d", fooString)));
    objectValue.set(field("a.b"), barValue);
    objectValue.set(field("a.c.d"), barValue);
    assertEquals(wrapObject("a", map("b", barString, "c", map("d", barString))), objectValue);
  }

  @Test
  public void testOverwritesDeeplyNestedField() {
    ObjectValue objectValue = wrapObject("a", map("b", fooString));
    objectValue.set(field("a.b.c"), barValue);
    assertEquals(wrapObject("a", map("b", map("c", barString))), objectValue);
  }

  @Test
  public void testOverwritesNestedObject() {
    ObjectValue objectValue = wrapObject("a", map("b", map("c", fooString, "d", fooString)));
    objectValue.set(field("a.b"), barValue);
    assertEquals(wrapObject("a", map("b", "bar")), objectValue);
  }

  @Test
  public void testReplacesNestedObject() {
    ObjectValue singleValueObject = wrapObject(map("c", barString));
    ObjectValue objectValue = wrapObject("a", map("b", fooString));
    objectValue.set(field("a"), singleValueObject.get(FieldPath.EMPTY_PATH));
    assertEquals(wrapObject("a", map("c", barString)), objectValue);
  }

  @Test
  public void testAddsNewFields() {
    ObjectValue objectValue = new ObjectValue();
    assertEquals(wrapObject(), objectValue);
    objectValue.set(field("a"), wrap("mod"));
    assertEquals(wrapObject("a", "mod"), objectValue);

    objectValue.set(field("b"), wrap(1));
    assertEquals(wrapObject("a", "mod", "b", 1), objectValue);
  }

  @Test
  public void testAddsMultipleNewFields() {
    ObjectValue object = new ObjectValue();
    object.set(field("a"), wrap("a"));
    object.set(field("b"), wrap("b"));
    object.set(field("c"), wrap("c"));

    assertEquals(wrapObject("a", "a", "b", "b", "c", "c"), object);
  }

  @Test
  public void testAddsNestedField() {
    ObjectValue objectValue = new ObjectValue();
    objectValue.set(field("a.b"), fooValue);
    objectValue.set(field("c.d.e"), fooValue);
    assertEquals(
        wrapObject("a", map("b", fooString), "c", map("d", map("e", fooString))), objectValue);
  }

  @Test
  public void testAddsFieldInNestedObject() {
    ObjectValue objectValue = new ObjectValue();
    objectValue.set(field("a"), wrapObject("b", fooString).get(FieldPath.EMPTY_PATH));
    objectValue.set(field("a.c"), fooValue);
    assertEquals(wrapObject("a", map("b", fooString, "c", fooString)), objectValue);
  }

  @Test
  public void testAddsTwoFieldsInNestedObject() {
    ObjectValue objectValue = new ObjectValue();
    objectValue.set(field("a.b"), fooValue);
    objectValue.set(field("a.c"), fooValue);
    assertEquals(wrapObject("a", map("b", fooString, "c", fooString)), objectValue);
  }

  @Test
  public void testAddDeeplyNestedFieldInNestedObject() {
    ObjectValue objectValue = new ObjectValue();
    objectValue.set(field("a.b.c.d.e.f"), fooValue);
    assertEquals(
        wrapObject("a", map("b", map("c", map("d", map("e", map("f", fooString)))))), objectValue);
  }

  @Test
  public void testAddsSingleFieldInExistingObject() {
    ObjectValue objectValue = wrapObject("a", fooString);
    objectValue.set(field("b"), fooValue);
    assertEquals(wrapObject("a", fooString, "b", fooString), objectValue);
  }

  @Test
  public void testSetsNestedFieldMultipleTimes() {
    ObjectValue objectValue = new ObjectValue();
    objectValue.set(field("a.c"), fooValue);
    objectValue.set(field("a"), wrapObject("b", fooString).get(FieldPath.EMPTY_PATH));
    assertEquals(wrapObject("a", map("b", fooString)), objectValue);
  }

  @Test
  public void testImplicitlyCreatesObjects() {
    ObjectValue objectValue = wrapObject("a", "old");
    assertEquals(wrapObject("a", "old"), objectValue);
    objectValue.set(field("b.c.d"), wrap("mod"));
    assertEquals(wrapObject("a", "old", "b", map("c", map("d", "mod"))), objectValue);
  }

  @Test
  public void testCanOverwritePrimitivesWithObjects() {
    ObjectValue objectValue = wrapObject("a", map("b", "old"));
    assertEquals(wrapObject("a", map("b", "old")), objectValue);
    objectValue.set(field("a"), wrapObject(map("b", "mod")).get(FieldPath.EMPTY_PATH));
    assertEquals(wrapObject("a", map("b", "mod")), objectValue);
  }

  @Test
  public void testAddsToNestedObjects() {
    ObjectValue objectValue = wrapObject("a", map("b", "old"));
    assertEquals(wrapObject("a", map("b", "old")), objectValue);
    objectValue.set(field("a.c"), wrap("mod"));
    assertEquals(wrapObject("a", map("b", "old", "c", "mod")), objectValue);
  }

  @Test
  public void testDeletesKey() {
    ObjectValue objectValue = wrapObject("a", 1, "b", 2);
    assertEquals(wrapObject("a", 1, "b", 2), objectValue);

    objectValue.delete(field("a"));
    assertEquals(wrapObject("b", 2), objectValue);

    objectValue.delete(field("b"));
    assertEquals(new ObjectValue(), objectValue);
  }

  @Test
  public void testDeletesHandleMissingKeys() {
    ObjectValue objectValue = wrapObject("a", map("b", 1, "c", 2));
    assertEquals(wrapObject("a", map("b", 1, "c", 2)), objectValue);
    objectValue.delete(field("b"));
    assertEquals(wrapObject("a", map("b", 1, "c", 2)), objectValue);

    objectValue.delete(field("a.d"));
    assertEquals(wrapObject("a", map("b", 1, "c", 2)), objectValue);

    objectValue.delete(field("a.b.c"));
    assertEquals(wrapObject("a", map("b", 1, "c", 2)), objectValue);
  }

  @Test
  public void testDeletesNestedKeys() {
    Map<String, Object> orig = map("a", map("b", 1, "c", map("d", 2, "e", 3)));
    ObjectValue objectValue = wrapObject(orig);
    assertEquals(wrapObject(orig), objectValue);

    objectValue.delete(field("a.c.d"));
    Map<String, Object> second = map("a", map("b", 1, "c", map("e", 3)));
    assertEquals(wrapObject(second), objectValue);

    Map<String, Object> third = map("a", map("b", 1));
    objectValue.delete(field("a.c"));
    assertEquals(wrapObject(third), objectValue);

    objectValue.delete(field("a"));
    assertEquals(new ObjectValue(), objectValue);
  }

  @Test
  public void testDeletesNestedObject() {
    ObjectValue objectValue =
        wrapObject("a", map("b", map("c", fooString, "d", fooString), "f", fooString));
    objectValue.delete(field("a.b"));
    assertEquals(wrapObject("a", map("f", fooString)), objectValue);
  }

  @Test
  public void testAddsAndDeletesField() {
    ObjectValue objectValue = new ObjectValue();
    objectValue.set(field(fooString), fooValue);
    objectValue.delete(field(fooString));
    assertEquals(wrapObject(), objectValue);
  }

  @Test
  public void testAddsAndDeletesNestedField() {
    ObjectValue objectValue = new ObjectValue();
    objectValue.set(field("a.b.c"), fooValue);
    objectValue.set(field("a.b.d"), fooValue);
    objectValue.set(field("f.g"), fooValue);
    objectValue.set(field("h"), fooValue);
    objectValue.delete(field("a.b.c"));
    objectValue.delete(field("h"));
    assertEquals(
        wrapObject("a", map("b", map("d", fooString)), "f", map("g", fooString)), objectValue);
  }

  @Test
  public void testMergesExistingObject() {
    ObjectValue objectValue = wrapObject("a", map("b", fooString));
    objectValue.set(field("a.c"), fooValue);
    assertEquals(wrapObject("a", map("b", fooString, "c", fooString)), objectValue);
  }
}
