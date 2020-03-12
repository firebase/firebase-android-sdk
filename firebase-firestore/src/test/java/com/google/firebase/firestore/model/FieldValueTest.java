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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import com.google.firebase.firestore.model.mutation.FieldMask;
import com.google.firestore.v1.Value;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class FieldValueTest {

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
    ObjectValue old = wrapObject("a", "old");
    ObjectValue mod = setField(old, "a", wrap("mod"));
    assertNotEquals(old, mod);
    assertEquals(wrapObject("a", "old"), old);
    assertEquals(wrapObject("a", "mod"), mod);
  }

  @Test
  public void testAddsNewFields() {
    ObjectValue empty = ObjectValue.emptyObject();
    ObjectValue mod = setField(empty, "a", wrap("mod"));
    assertEquals(wrapObject(), empty);
    assertEquals(wrapObject("a", "mod"), mod);

    ObjectValue old = mod;
    mod = setField(old, "b", wrap(1));
    assertEquals(wrapObject("a", "mod"), old);
    assertEquals(wrapObject("a", "mod", "b", 1), mod);
  }

  @Test
  public void testAddsMultipleNewFields() {
    ObjectValue object = ObjectValue.emptyObject();
    object = setField(object, "a", wrap("a"));
    object = object.toBuilder().set(field("b"), wrap("b")).set(field("c"), wrap("c")).build();

    assertEquals(wrapObject("a", "a", "b", "b", "c", "c"), object);
  }

  @Test
  public void testImplicitlyCreatesObjects() {
    ObjectValue old = wrapObject("a", "old");
    ObjectValue mod = setField(old, "b.c.d", wrap("mod"));

    assertNotEquals(old, mod);
    assertEquals(wrapObject("a", "old"), old);
    assertEquals(wrapObject("a", "old", "b", map("c", map("d", "mod"))), mod);
  }

  @Test
  public void testCanOverwritePrimitivesWithObjects() {
    ObjectValue old = wrapObject("a", map("b", "old"));
    ObjectValue mod = setField(old, "a", map("b", "mod"));
    assertNotEquals(old, mod);
    assertEquals(wrapObject("a", map("b", "old")), old);
    assertEquals(wrapObject("a", map("b", "mod")), mod);
  }

  @Test
  public void testAddsToNestedObjects() {
    ObjectValue old = wrapObject("a", map("b", "old"));
    ObjectValue mod = setField(old, "a.c", wrap("mod"));
    assertNotEquals(old, mod);
    assertEquals(wrapObject("a", map("b", "old")), old);
    assertEquals(wrapObject("a", map("b", "old", "c", "mod")), mod);
  }

  @Test
  public void testDeletesKey() {
    ObjectValue old = wrapObject("a", 1, "b", 2);
    ObjectValue mod = deleteField(old, "a");

    assertNotEquals(old, mod);
    assertEquals(wrapObject("a", 1, "b", 2), old);
    assertEquals(wrapObject("b", 2), mod);

    ObjectValue empty = deleteField(mod, "b");
    assertNotEquals(mod, empty);
    assertEquals(wrapObject("b", 2), mod);
    assertEquals(ObjectValue.emptyObject(), empty);
  }

  @Test
  public void testDeletesHandleMissingKeys() {
    ObjectValue old = wrapObject("a", map("b", 1, "c", 2));
    ObjectValue mod = deleteField(old, "b");
    assertEquals(mod, old);
    assertEquals(wrapObject("a", map("b", 1, "c", 2)), mod);

    mod = deleteField(old, "a.d");
    assertEquals(old, mod);
    assertEquals(wrapObject("a", map("b", 1, "c", 2)), mod);

    mod = deleteField(old, "a.b.c");
    assertEquals(old, mod);
    assertEquals(wrapObject("a", map("b", 1, "c", 2)), mod);
  }

  @Test
  public void testDeletesNestedKeys() {
    Map<String, Object> orig = map("a", map("b", 1, "c", map("d", 2, "e", 3)));
    ObjectValue old = wrapObject(orig);
    ObjectValue mod = deleteField(old, "a.c.d");

    assertNotEquals(mod, old);
    assertEquals(wrapObject(orig), old);

    Map<String, Object> second = map("a", map("b", 1, "c", map("e", 3)));
    assertEquals(wrapObject(second), mod);

    old = mod;
    mod = deleteField(old, "a.c");

    assertNotEquals(old, mod);
    assertEquals(wrapObject(second), old);

    Map<String, Object> third = map("a", map("b", 1));
    assertEquals(wrapObject(third), mod);

    old = mod;
    mod = deleteField(old, "a");

    assertNotEquals(old, mod);
    assertEquals(wrapObject(third), old);
    assertEquals(ObjectValue.emptyObject(), mod);
  }

  @Test
  public void testDeletesMultipleFields() {
    ObjectValue object = wrapObject("a", "a", "b", "b", "c", "c");
    object = object.toBuilder().delete(field("a")).build();
    object = object.toBuilder().delete(field("b")).delete(field("c")).build();

    assertEquals(ObjectValue.emptyObject(), object);
  }

  private ObjectValue setField(ObjectValue objectValue, String fieldPath, Value value) {
    return objectValue.toBuilder().set(field(fieldPath), value).build();
  }

  private ObjectValue setField(
      ObjectValue objectValue, String fieldPath, Map<String, Object> value) {
    return objectValue.toBuilder().set(field(fieldPath), wrapObject(value).getProto()).build();
  }

  private ObjectValue deleteField(ObjectValue objectValue, String fieldPath) {
    return objectValue.toBuilder().delete(field(fieldPath)).build();
  }
}
