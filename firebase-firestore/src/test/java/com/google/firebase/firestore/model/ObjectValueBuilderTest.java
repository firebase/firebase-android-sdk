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

import static com.google.firebase.firestore.testutil.TestUtil.field;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.testutil.TestUtil.wrap;
import static com.google.firebase.firestore.testutil.TestUtil.wrapObject;
import static junit.framework.TestCase.assertEquals;

import com.google.firebase.firestore.model.value.ObjectValue;
import com.google.firestore.v1.Value;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class ObjectValueBuilderTest {
  private Value fooValue = wrap("foo").getProto();
  private Value barValue = wrap("bar").getProto();
  private Value emptyObject = ObjectValue.emptyObject().getProto();

  @Test
  public void emptyBuilder() {
    ObjectValue.Builder builder = ObjectValue.emptyObject().toBuilder();
    ObjectValue object = builder.build();
    assertEquals(wrapObject(), object);
  }

  @Test
  public void setSingleField() {
    ObjectValue.Builder builder = ObjectValue.emptyObject().toBuilder();
    builder.set(field("foo"), fooValue);
    ObjectValue object = builder.build();
    assertEquals(wrapObject("foo", fooValue), object);
  }

  @Test
  public void setEmptyObject() {
    ObjectValue.Builder builder = ObjectValue.emptyObject().toBuilder();
    builder.set(field("foo"), emptyObject);
    ObjectValue object = builder.build();
    assertEquals(wrapObject("foo", emptyObject), object);
  }

  @Test
  public void setMultipleFields() {
    ObjectValue.Builder builder = ObjectValue.emptyObject().toBuilder();
    builder.set(field("foo"), fooValue);
    builder.set(field("bar"), fooValue);
    ObjectValue object = builder.build();
    assertEquals(wrapObject("foo", fooValue, "bar", fooValue), object);
  }

  @Test
  public void setSuccessorField() {
    ObjectValue.Builder builder = ObjectValue.emptyObject().toBuilder();
    builder.set(field("a"), fooValue);
    builder.set(field("a0"), fooValue);
    ObjectValue object = builder.build();
    assertEquals(wrapObject("a", fooValue, "a0", fooValue), object);
  }

  @Test
  public void setNestedField() {
    ObjectValue.Builder builder = ObjectValue.emptyObject().toBuilder();
    builder.set(field("a.b"), fooValue);
    builder.set(field("c.d.e"), fooValue);
    ObjectValue object = builder.build();
    assertEquals(wrapObject("a", map("b", fooValue), "c", map("d", map("e", fooValue))), object);
  }

  @Test
  public void setTwoFieldsInNestedObject() {
    ObjectValue.Builder builder = ObjectValue.emptyObject().toBuilder();
    builder.set(field("a.b"), fooValue);
    builder.set(field("a.c"), fooValue);
    ObjectValue object = builder.build();
    assertEquals(wrapObject("a", map("b", fooValue, "c", fooValue)), object);
  }

  @Test
  public void setFieldInNestedObject() {
    ObjectValue.Builder builder = ObjectValue.emptyObject().toBuilder();
    builder.set(field("a"), wrapObject("b", fooValue).getProto());
    builder.set(field("a.c"), fooValue);
    ObjectValue object = builder.build();
    assertEquals(wrapObject("a", map("b", fooValue, "c", fooValue)), object);
  }

  @Test
  public void setNestedFieldMultipleTimes() {
    ObjectValue.Builder builder = ObjectValue.emptyObject().toBuilder();
    builder.set(field("a.c"), fooValue);
    builder.set(field("a"), wrapObject("b", fooValue).getProto());
    ObjectValue object = builder.build();
    assertEquals(wrapObject("a", map("b", fooValue)), object);
  }

  @Test
  public void setAndDeleteField() {
    ObjectValue.Builder builder = ObjectValue.emptyObject().toBuilder();
    builder.set(field("foo"), fooValue);
    builder.delete(field("foo"));
    ObjectValue object = builder.build();
    assertEquals(wrapObject(), object);
  }

  @Test
  public void setAndDeleteNestedField() {
    ObjectValue.Builder builder = ObjectValue.emptyObject().toBuilder();
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
  public void setSingleFieldInExistingObject() {
    ObjectValue.Builder builder = wrapObject("a", fooValue).toBuilder();
    builder.set(field("b"), fooValue);
    ObjectValue object = builder.build();
    assertEquals(wrapObject("a", fooValue, "b", fooValue), object);
  }

  @Test
  public void overwriteField() {
    ObjectValue.Builder builder = wrapObject("a", fooValue).toBuilder();
    builder.set(field("a"), barValue);
    ObjectValue object = builder.build();
    assertEquals(wrapObject("a", barValue), object);
  }

  @Test
  public void overwriteNestedFields() {
    ObjectValue.Builder builder =
        wrapObject("a", map("b", fooValue, "c", map("d", fooValue))).toBuilder();
    builder.set(field("a.b"), barValue);
    builder.set(field("a.c.d"), barValue);
    ObjectValue object = builder.build();
    assertEquals(wrapObject("a", map("b", barValue, "c", map("d", barValue))), object);
  }

  @Test
  public void overwriteDeeplyNestedField() {
    ObjectValue.Builder builder = wrapObject("a", map("b", fooValue)).toBuilder();
    builder.set(field("a.b.c"), barValue);
    ObjectValue object = builder.build();
    assertEquals(wrapObject("a", map("b", map("c", barValue))), object);
  }

  @Test
  public void mergeExistingObject() {
    ObjectValue.Builder builder = wrapObject("a", map("b", fooValue)).toBuilder();
    builder.set(field("a.c"), fooValue);
    ObjectValue object = builder.build();
    assertEquals(wrapObject("a", map("b", fooValue, "c", fooValue)), object);
  }

  @Test
  public void overwriteNestedObject() {
    ObjectValue.Builder builder =
        wrapObject("a", map("b", map("c", fooValue, "d", fooValue))).toBuilder();
    builder.set(field("a.b"), barValue);
    ObjectValue object = builder.build();
    assertEquals(wrapObject("a", map("b", barValue)), object);
  }

  @Test
  public void deleteSingleField() {
    ObjectValue.Builder builder = wrapObject("a", fooValue, "b", fooValue).toBuilder();
    builder.delete(field("a"));
    ObjectValue object = builder.build();
    assertEquals(wrapObject("b", fooValue), object);
  }

  @Test
  public void deleteNestedObject() {
    ObjectValue.Builder builder =
        wrapObject("a", map("b", map("c", fooValue, "d", fooValue), "f", fooValue)).toBuilder();
    builder.delete(field("a.b"));
    ObjectValue object = builder.build();
    assertEquals(wrapObject("a", map("f", fooValue)), object);
  }

  @Test
  public void deleteNonExistingField() {
    ObjectValue.Builder builder = wrapObject("a", fooValue).toBuilder();
    builder.delete(field("b"));
    ObjectValue object = builder.build();
    assertEquals(wrapObject("a", fooValue), object);
  }

  @Test
  public void deleteNonExistingNestedField() {
    ObjectValue.Builder builder = wrapObject("a", map("b", fooValue)).toBuilder();
    builder.delete(field("a.b.c"));
    ObjectValue object = builder.build();
    assertEquals(wrapObject("a", map("b", fooValue)), object);
  }
}
