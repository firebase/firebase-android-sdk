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

import com.google.firestore.v1.Value;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class ObjectValueBuilderTest {
  private String fooString = "foo";
  private Value fooValue = wrap(fooString);
  private String barString = "bar";
  private Value barValue = wrap(barString);
  private Value emptyObject = ObjectValue.emptyObject().getProto();

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
    assertEquals(wrapObject(fooString, fooString), object);
  }

  @Test
  public void setsEmptyObject() {
    ObjectValue.Builder builder = ObjectValue.newBuilder();
    builder.set(field("foo"), emptyObject);
    ObjectValue object = builder.build();
    assertEquals(wrapObject(fooString, map()), object);
  }

  @Test
  public void setsMultipleFields() {
    ObjectValue.Builder builder = ObjectValue.newBuilder();
    builder.set(field("foo"), fooValue);
    builder.set(field("bar"), fooValue);
    ObjectValue object = builder.build();
    assertEquals(wrapObject(fooString, fooString, "bar", fooString), object);
  }

  @Test
  public void setsNestedField() {
    ObjectValue.Builder builder = ObjectValue.newBuilder();
    builder.set(field("a.b"), fooValue);
    builder.set(field("c.d.e"), fooValue);
    ObjectValue object = builder.build();
    assertEquals(wrapObject("a", map("b", fooString), "c", map("d", map("e", fooString))), object);
  }

  @Test
  public void setsTwoFieldsInNestedObject() {
    ObjectValue.Builder builder = ObjectValue.newBuilder();
    builder.set(field("a.b"), fooValue);
    builder.set(field("a.c"), fooValue);
    ObjectValue object = builder.build();
    assertEquals(wrapObject("a", map("b", fooString, "c", fooString)), object);
  }

  @Test
  public void setsFieldInNestedObject() {
    ObjectValue.Builder builder = ObjectValue.newBuilder();
    builder.set(field("a"), wrapObject("b", fooString).getProto());
    builder.set(field("a.c"), fooValue);
    ObjectValue object = builder.build();
    assertEquals(wrapObject("a", map("b", fooString, "c", fooString)), object);
  }

  @Test
  public void setsDeeplyNestedFieldInNestedObject() {
    ObjectValue.Builder builder = ObjectValue.newBuilder();
    builder.set(field("a.b.c.d.e.f"), fooValue);
    ObjectValue object = builder.build();
    assertEquals(
        wrapObject("a", map("b", map("c", map("d", map("e", map("f", fooString)))))), object);
  }

  @Test
  public void setsNestedFieldMultipleTimes() {
    ObjectValue.Builder builder = ObjectValue.newBuilder();
    builder.set(field("a.c"), fooValue);
    builder.set(field("a"), wrapObject("b", fooString).getProto());
    ObjectValue object = builder.build();
    assertEquals(wrapObject("a", map("b", fooString)), object);
  }

  @Test
  public void setsAndDeletesField() {
    ObjectValue.Builder builder = ObjectValue.newBuilder();
    builder.set(field(fooString), fooValue);
    builder.delete(field(fooString));
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
    assertEquals(wrapObject("a", map("b", map("d", fooString)), "f", map("g", fooString)), object);
  }

  @Test
  public void setsSingleFieldInExistingObject() {
    ObjectValue.Builder builder = wrapObject("a", fooString).toBuilder();
    builder.set(field("b"), fooValue);
    ObjectValue object = builder.build();
    assertEquals(wrapObject("a", fooString, "b", fooString), object);
  }

  @Test
  public void overwritesField() {
    ObjectValue.Builder builder = wrapObject("a", fooString).toBuilder();
    builder.set(field("a"), barValue);
    ObjectValue object = builder.build();
    assertEquals(wrapObject("a", barString), object);
  }

  @Test
  public void overwritesNestedFields() {
    ObjectValue.Builder builder =
        wrapObject("a", map("b", fooString, "c", map("d", fooString))).toBuilder();
    builder.set(field("a.b"), barValue);
    builder.set(field("a.c.d"), barValue);
    ObjectValue object = builder.build();
    assertEquals(wrapObject("a", map("b", barString, "c", map("d", barString))), object);
  }

  @Test
  public void overwritesDeeplyNestedField() {
    ObjectValue.Builder builder = wrapObject("a", map("b", fooString)).toBuilder();
    builder.set(field("a.b.c"), barValue);
    ObjectValue object = builder.build();
    assertEquals(wrapObject("a", map("b", map("c", barString))), object);
  }

  @Test
  public void mergesExistingObject() {
    ObjectValue.Builder builder = wrapObject("a", map("b", fooString)).toBuilder();
    builder.set(field("a.c"), fooValue);
    ObjectValue object = builder.build();
    assertEquals(wrapObject("a", map("b", fooString, "c", fooString)), object);
  }

  @Test
  public void overwritesNestedObject() {
    ObjectValue.Builder builder =
        wrapObject("a", map("b", map("c", fooString, "d", fooString))).toBuilder();
    builder.set(field("a.b"), barValue);
    ObjectValue object = builder.build();
    assertEquals(wrapObject("a", map("b", "bar")), object);
  }

  @Test
  public void replacesNestedObject() {
    ObjectValue singleValueObject = wrapObject(map("c", barString));
    ObjectValue.Builder builder = wrapObject("a", map("b", fooString)).toBuilder();
    builder.set(field("a"), singleValueObject.getProto());
    ObjectValue object = builder.build();
    assertEquals(wrapObject("a", map("c", barString)), object);
  }

  @Test
  public void deletesSingleField() {
    ObjectValue.Builder builder = wrapObject("a", fooString, "b", fooString).toBuilder();
    builder.delete(field("a"));
    ObjectValue object = builder.build();
    assertEquals(wrapObject("b", fooString), object);
  }

  @Test
  public void deletesNestedObject() {
    ObjectValue.Builder builder =
        wrapObject("a", map("b", map("c", fooString, "d", fooString), "f", fooString)).toBuilder();
    builder.delete(field("a.b"));
    ObjectValue object = builder.build();
    assertEquals(wrapObject("a", map("f", fooString)), object);
  }

  @Test
  public void deletesNonExistingField() {
    ObjectValue.Builder builder = wrapObject("a", fooString).toBuilder();
    builder.delete(field("b"));
    ObjectValue object = builder.build();
    assertEquals(wrapObject("a", fooString), object);
  }

  @Test
  public void deletesNonExistingNestedField() {
    ObjectValue.Builder builder = wrapObject("a", map("b", fooString)).toBuilder();
    builder.delete(field("a.b.c"));
    ObjectValue object = builder.build();
    assertEquals(wrapObject("a", map("b", fooString)), object);
  }
}
