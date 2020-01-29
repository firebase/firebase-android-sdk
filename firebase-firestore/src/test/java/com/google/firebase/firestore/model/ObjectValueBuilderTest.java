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

import static com.google.firebase.firestore.Values.map;
import static com.google.firebase.firestore.Values.valueOf;
import static com.google.firebase.firestore.testutil.TestUtil.field;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

import com.google.firebase.firestore.model.protovalue.ObjectValue;
import com.google.firebase.firestore.model.value.FieldValue;
import com.google.firestore.v1.Value;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class ObjectValueBuilderTest {
  private Value fooValue = valueOf("foo");
  private Value barValue = valueOf("bar");
  private Value emptyObject = valueOf(Collections.emptyMap());

  @Test
  public void supportsEmptyBuilders() {
    ObjectValue.Builder builder = ObjectValue.newBuilder();
    ObjectValue object = builder.build();
    assertEquals(ObjectValue.emptyObject(), object);
  }

  @Test
  public void setsSingleField() {
    ObjectValue.Builder builder = ObjectValue.newBuilder();
    builder.set(field("foo"), fooValue);
    ObjectValue object = builder.build();
    assertEquals(wrapObject("foo", fooValue), object);
  }

  @Test
  public void setsEmptyObject() {
    ObjectValue.Builder builder = ObjectValue.newBuilder();
    builder.set(field("foo"), emptyObject);
    ObjectValue object = builder.build();
    assertEquals(wrapObject("foo", emptyObject), object);
  }

  @Test
  public void setsMultipleFields() {
    ObjectValue.Builder builder = ObjectValue.newBuilder();
    builder.set(field("foo"), fooValue);
    builder.set(field("bar"), fooValue);
    ObjectValue object = builder.build();
    assertEquals(wrapObject("foo", fooValue, "bar", fooValue), object);
  }

  @Test
  public void setsNestedField() {
    ObjectValue.Builder builder = ObjectValue.newBuilder();
    builder.set(field("a.b"), fooValue);
    builder.set(field("c.d.e"), fooValue);
    ObjectValue object = builder.build();
    assertEquals(wrapObject("a", map("b", fooValue), "c", map("d", map("e", fooValue))), object);
  }

  @Test
  public void setsTwoFieldsInNestedObject() {
    ObjectValue.Builder builder = ObjectValue.newBuilder();
    builder.set(field("a.b"), fooValue);
    builder.set(field("a.c"), fooValue);
    ObjectValue object = builder.build();
    assertEquals(wrapObject("a", map("b", fooValue, "c", fooValue)), object);
  }

  @Test
  public void setsFieldInNestedObject() {
    ObjectValue.Builder builder = ObjectValue.newBuilder();
    builder.set(field("a"), map("b", fooValue));
    builder.set(field("a.c"), fooValue);
    ObjectValue object = builder.build();
    assertEquals(wrapObject("a", map("b", fooValue, "c", fooValue)), object);
  }

  @Test
  public void setsDeeplyNestedFieldInNestedObject() {
    ObjectValue.Builder builder = ObjectValue.newBuilder();
    builder.set(field("a.b.c.d.e.f"), fooValue);
    ObjectValue object = builder.build();
    assertEquals(
        wrapObject("a", map("b", map("c", map("d", map("e", map("f", fooValue)))))), object);
  }

  @Test
  public void setsNestedFieldMultipleTimes() {
    ObjectValue.Builder builder = ObjectValue.newBuilder();
    builder.set(field("a.c"), fooValue);
    builder.set(field("a"), map("b", fooValue));
    ObjectValue object = builder.build();
    assertEquals(wrapObject("a", map("b", fooValue)), object);
  }

  @Test
  public void setsAndDeletesField() {
    ObjectValue.Builder builder = ObjectValue.newBuilder();
    builder.set(field("foo"), fooValue);
    builder.delete(field("foo"));
    ObjectValue object = builder.build();
    assertEquals(wrapObject(), object);
  }

  @Test
  public void setsAndDeletesNestedField() {
    ObjectValue.Builder builder = ObjectValue.newBuilder();
    builder.set(field("a.b.c"), fooValue);
    builder.set(field("a.b.d"), fooValue);
    builder.set(field("f.g"), fooValue);
    builder.set(field("h"), fooValue);
    builder.delete(field("a.b.c"));
    builder.delete(field("h"));
    ObjectValue object = builder.build();
    assertEquals(wrapObject("a", map("b", map("d", fooValue)), "f", map("g", fooValue)), object);
  }

  @Test
  public void setsSingleFieldInExistingObject() {
    ObjectValue.Builder builder = wrapObject("a", fooValue).toBuilder();
    builder.set(field("b"), fooValue);
    ObjectValue object = builder.build();
    assertEquals(wrapObject("a", fooValue, "b", fooValue), object);
  }

  @Test
  public void overwritesField() {
    ObjectValue.Builder builder = wrapObject("a", fooValue).toBuilder();
    builder.set(field("a"), barValue);
    ObjectValue object = builder.build();
    assertEquals(wrapObject("a", barValue), object);
  }

  @Test
  public void overwritesNestedFields() {
    ObjectValue.Builder builder =
        wrapObject("a", map("b", fooValue, "c", map("d", fooValue))).toBuilder();
    builder.set(field("a.b"), barValue);
    builder.set(field("a.c.d"), barValue);
    ObjectValue object = builder.build();
    assertEquals(wrapObject("a", map("b", barValue, "c", map("d", barValue))), object);
  }

  @Test
  public void overwritesDeeplyNestedField() {
    ObjectValue.Builder builder = wrapObject("a", map("b", fooValue)).toBuilder();
    builder.set(field("a.b.c"), barValue);
    ObjectValue object = builder.build();
    assertEquals(wrapObject("a", map("b", map("c", barValue))), object);
  }

  @Test
  public void mergesExistingObject() {
    ObjectValue.Builder builder = wrapObject("a", map("b", fooValue)).toBuilder();
    builder.set(field("a.c"), fooValue);
    ObjectValue object = builder.build();
    assertEquals(wrapObject("a", map("b", fooValue, "c", fooValue)), object);
  }

  @Test
  public void overwritesNestedObject() {
    ObjectValue.Builder builder =
        wrapObject("a", map("b", map("c", fooValue, "d", fooValue))).toBuilder();
    builder.set(field("a.b"), barValue);
    ObjectValue object = builder.build();
    assertEquals(wrapObject("a", map("b", barValue)), object);
  }

  @Test
  public void replacesNestedObject() {
    Value singleValueObject = valueOf(map("c", barValue));
    ObjectValue.Builder builder = wrapObject("a", map("b", fooValue)).toBuilder();
    builder.set(field("a"), singleValueObject);
    ObjectValue object = builder.build();
    assertEquals(wrapObject("a", map("c", barValue)), object);
  }

  @Test
  public void deletesSingleField() {
    ObjectValue.Builder builder = wrapObject("a", fooValue, "b", fooValue).toBuilder();
    builder.delete(field("a"));
    ObjectValue object = builder.build();
    assertEquals(wrapObject("b", fooValue), object);
  }

  @Test
  public void deletesNestedObject() {
    ObjectValue.Builder builder =
        wrapObject("a", map("b", map("c", fooValue, "d", fooValue), "f", fooValue)).toBuilder();
    builder.delete(field("a.b"));
    ObjectValue object = builder.build();
    assertEquals(wrapObject("a", map("f", fooValue)), object);
  }

  @Test
  public void deletesNonExistingField() {
    ObjectValue.Builder builder = wrapObject("a", fooValue).toBuilder();
    builder.delete(field("b"));
    ObjectValue object = builder.build();
    assertEquals(wrapObject("a", fooValue), object);
  }

  @Test
  public void deletesNonExistingNestedField() {
    ObjectValue.Builder builder = wrapObject("a", map("b", fooValue)).toBuilder();
    builder.delete(field("a.b.c"));
    ObjectValue object = builder.build();
    assertEquals(wrapObject("a", map("b", fooValue)), object);
  }

  /** Creates a new ObjectValue based on key/value argument pairs. */
  private ObjectValue wrapObject(Object... entries) {
    FieldValue object = FieldValue.of(map(entries));
    assertTrue(object instanceof ObjectValue);
    return (ObjectValue) object;
  }
}
