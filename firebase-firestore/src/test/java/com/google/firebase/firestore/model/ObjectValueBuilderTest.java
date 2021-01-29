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
import static org.junit.Assert.assertEquals;

import com.google.firestore.v1.MapValue;
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
  private Value emptyObject = Value.newBuilder().setMapValue(MapValue.getDefaultInstance()).build();

  @Test
  public void supportsEmptyobjectValues() {
    ObjectValue objectValue = new ObjectValue();
    assertEquals(new ObjectValue(), objectValue);
  }

  @Test
  public void setsSingleField() {
    ObjectValue objectValue = new ObjectValue();
    objectValue.set(field("foo"), fooValue);
    assertEquals(wrapObject(fooString, fooString), objectValue);
  }

  @Test
  public void setsEmptyObject() {
    ObjectValue objectValue = new ObjectValue();
    objectValue.set(field("foo"), emptyObject);
    assertEquals(wrapObject(fooString, map()), objectValue);
  }

  @Test
  public void setsMultipleFields() {
    ObjectValue objectValue = new ObjectValue();
    objectValue.set(field("foo"), fooValue);
    objectValue.set(field("bar"), fooValue);
    assertEquals(wrapObject(fooString, fooString, "bar", fooString), objectValue);
  }

  @Test
  public void setsNestedField() {
    ObjectValue objectValue = new ObjectValue();
    objectValue.set(field("a.b"), fooValue);
    objectValue.set(field("c.d.e"), fooValue);
    assertEquals(
        wrapObject("a", map("b", fooString), "c", map("d", map("e", fooString))), objectValue);
  }

  @Test
  public void setsTwoFieldsInNestedObject() {
    ObjectValue objectValue = new ObjectValue();
    objectValue.set(field("a.b"), fooValue);
    objectValue.set(field("a.c"), fooValue);
    assertEquals(wrapObject("a", map("b", fooString, "c", fooString)), objectValue);
  }

  @Test
  public void setsFieldInNestedObject() {
    ObjectValue objectValue = new ObjectValue();
    objectValue.set(field("a"), wrapObject("b", fooString).getProto());
    objectValue.set(field("a.c"), fooValue);
    assertEquals(wrapObject("a", map("b", fooString, "c", fooString)), objectValue);
  }

  @Test
  public void setsDeeplyNestedFieldInNestedObject() {
    ObjectValue objectValue = new ObjectValue();
    objectValue.set(field("a.b.c.d.e.f"), fooValue);
    assertEquals(
        wrapObject("a", map("b", map("c", map("d", map("e", map("f", fooString)))))), objectValue);
  }

  @Test
  public void setsNestedFieldMultipleTimes() {
    ObjectValue objectValue = new ObjectValue();
    objectValue.set(field("a.c"), fooValue);
    objectValue.set(field("a"), wrapObject("b", fooString).getProto());
    assertEquals(wrapObject("a", map("b", fooString)), objectValue);
  }

  @Test
  public void setsAndDeletesField() {
    ObjectValue objectValue = new ObjectValue();
    objectValue.set(field(fooString), fooValue);
    objectValue.delete(field(fooString));
    assertEquals(wrapObject(), objectValue);
  }

  @Test
  public void setsAndDeletesNestedField() {
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
  public void setsSingleFieldInExistingObject() {
    ObjectValue objectValue = wrapObject("a", fooString);
    objectValue.set(field("b"), fooValue);
    assertEquals(wrapObject("a", fooString, "b", fooString), objectValue);
  }

  @Test
  public void overwritesField() {
    ObjectValue objectValue = wrapObject("a", fooString);
    objectValue.set(field("a"), barValue);
    assertEquals(wrapObject("a", barString), objectValue);
  }

  @Test
  public void overwritesNestedFields() {
    ObjectValue objectValue = wrapObject("a", map("b", fooString, "c", map("d", fooString)));
    objectValue.set(field("a.b"), barValue);
    objectValue.set(field("a.c.d"), barValue);
    assertEquals(wrapObject("a", map("b", barString, "c", map("d", barString))), objectValue);
  }

  @Test
  public void overwritesDeeplyNestedField() {
    ObjectValue objectValue = wrapObject("a", map("b", fooString));
    objectValue.set(field("a.b.c"), barValue);
    assertEquals(wrapObject("a", map("b", map("c", barString))), objectValue);
  }

  @Test
  public void mergesExistingObject() {
    ObjectValue objectValue = wrapObject("a", map("b", fooString));
    objectValue.set(field("a.c"), fooValue);
    assertEquals(wrapObject("a", map("b", fooString, "c", fooString)), objectValue);
  }

  @Test
  public void overwritesNestedObject() {
    ObjectValue objectValue = wrapObject("a", map("b", map("c", fooString, "d", fooString)));
    objectValue.set(field("a.b"), barValue);
    assertEquals(wrapObject("a", map("b", "bar")), objectValue);
  }

  @Test
  public void replacesNestedObject() {
    ObjectValue singleValueObject = wrapObject(map("c", barString));
    ObjectValue objectValue = wrapObject("a", map("b", fooString));
    objectValue.set(field("a"), singleValueObject.getProto());
    assertEquals(wrapObject("a", map("c", barString)), objectValue);
  }

  @Test
  public void deletesSingleField() {
    ObjectValue objectValue = wrapObject("a", fooString, "b", fooString);
    objectValue.delete(field("a"));
    assertEquals(wrapObject("b", fooString), objectValue);
  }

  @Test
  public void deletesNestedObject() {
    ObjectValue objectValue =
        wrapObject("a", map("b", map("c", fooString, "d", fooString), "f", fooString));
    objectValue.delete(field("a.b"));
    assertEquals(wrapObject("a", map("f", fooString)), objectValue);
  }

  @Test
  public void deletesNonExistingField() {
    ObjectValue objectValue = wrapObject("a", fooString);
    objectValue.delete(field("b"));
    assertEquals(wrapObject("a", fooString), objectValue);
  }

  @Test
  public void deletesNonExistingNestedField() {
    ObjectValue objectValue = wrapObject("a", map("b", fooString));
    objectValue.delete(field("a.b.c"));
    assertEquals(wrapObject("a", map("b", fooString)), objectValue);
  }
}
