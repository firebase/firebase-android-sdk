// Copyright 2023 Google LLC
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class FilterTest {

  @Test
  public void equalTo() {
    Filter filter = Filter.equalTo("x", "v");
    assertEquals(filter, filter);
    assertEquals(filter, Filter.equalTo(FieldPath.of("x"), "v"));
    assertNotEquals(filter, Filter.equalTo("x", "z"));
    assertNotEquals(filter, Filter.equalTo("y", "v"));
    assertNotEquals(filter, Filter.notEqualTo("x", "v"));
  }

  @Test
  public void notEqualTo() {
    Filter filter = Filter.notEqualTo("x", "v");
    assertEquals(filter, filter);
    assertEquals(filter, Filter.notEqualTo(FieldPath.of("x"), "v"));
    assertNotEquals(filter, Filter.notEqualTo("x", "z"));
    assertNotEquals(filter, Filter.notEqualTo("y", "v"));
    assertNotEquals(filter, Filter.equalTo("x", "v"));
  }

  @Test
  public void greaterThan() {
    Filter filter = Filter.greaterThan("x", "v");
    assertEquals(filter, filter);
    assertEquals(filter, Filter.greaterThan(FieldPath.of("x"), "v"));
    assertNotEquals(filter, Filter.greaterThan("x", "z"));
    assertNotEquals(filter, Filter.greaterThan("y", "v"));
    assertNotEquals(filter, Filter.lessThan("x", "v"));
  }

  @Test
  public void greaterThanOrEqualTo() {
    Filter filter = Filter.greaterThanOrEqualTo("x", "v");
    assertEquals(filter, filter);
    assertEquals(filter, Filter.greaterThanOrEqualTo(FieldPath.of("x"), "v"));
    assertNotEquals(filter, Filter.greaterThanOrEqualTo("x", "z"));
    assertNotEquals(filter, Filter.greaterThanOrEqualTo("y", "v"));
    assertNotEquals(filter, Filter.lessThanOrEqualTo("x", "v"));
  }

  @Test
  public void lessThan() {
    Filter filter = Filter.lessThan("x", "v");
    assertEquals(filter, filter);
    assertEquals(filter, Filter.lessThan(FieldPath.of("x"), "v"));
    assertNotEquals(filter, Filter.lessThan("x", "z"));
    assertNotEquals(filter, Filter.lessThan("y", "v"));
    assertNotEquals(filter, Filter.greaterThan("x", "v"));
  }

  @Test
  public void lessThanOrEqualTo() {
    Filter filter = Filter.lessThanOrEqualTo("x", "v");
    assertEquals(filter, filter);
    assertEquals(filter, Filter.lessThanOrEqualTo(FieldPath.of("x"), "v"));
    assertNotEquals(filter, Filter.lessThanOrEqualTo("x", "z"));
    assertNotEquals(filter, Filter.lessThanOrEqualTo("y", "v"));
    assertNotEquals(filter, Filter.greaterThanOrEqualTo("x", "v"));
  }

  @Test
  public void arrayContains() {
    Filter filter = Filter.arrayContains("x", "v");
    assertEquals(filter, filter);
    assertEquals(filter, Filter.arrayContains(FieldPath.of("x"), "v"));
    assertNotEquals(filter, Filter.arrayContains("x", "z"));
    assertNotEquals(filter, Filter.arrayContains("y", "v"));
    assertNotEquals(filter, Filter.equalTo("x", "v"));
  }

  @Test
  public void arrayContainsAny() {
    Filter filter = Filter.arrayContainsAny("x", ImmutableList.of("v1", "v2"));
    assertEquals(filter, filter);
    assertEquals(filter, Filter.arrayContainsAny(FieldPath.of("x"), ImmutableList.of("v1", "v2")));
    assertNotEquals(filter, Filter.arrayContainsAny("x", ImmutableList.of("v2", "v1")));
    assertNotEquals(filter, Filter.arrayContainsAny("x", ImmutableList.of("v2", "v3")));
    assertNotEquals(filter, Filter.arrayContainsAny("y", ImmutableList.of("v1", "v2")));
    assertNotEquals(filter, Filter.equalTo("x", "v"));
  }

  @Test
  public void inArray() {
    Filter filter = Filter.inArray("x", ImmutableList.of("v1", "v2"));
    assertEquals(filter, filter);
    assertEquals(filter, Filter.inArray(FieldPath.of("x"), ImmutableList.of("v1", "v2")));
    assertNotEquals(filter, Filter.inArray("x", ImmutableList.of("v2", "v1")));
    assertNotEquals(filter, Filter.inArray("x", ImmutableList.of("v2", "v3")));
    assertNotEquals(filter, Filter.inArray("y", ImmutableList.of("v1", "v2")));
    assertNotEquals(filter, Filter.notInArray("x", ImmutableList.of("v1", "v2")));
  }

  @Test
  public void notInArray() {
    Filter filter = Filter.notInArray("x", ImmutableList.of("v1", "v2"));
    assertEquals(filter, filter);
    assertEquals(filter, Filter.notInArray(FieldPath.of("x"), ImmutableList.of("v1", "v2")));
    assertNotEquals(filter, Filter.notInArray("x", ImmutableList.of("v2", "v1")));
    assertNotEquals(filter, Filter.notInArray("x", ImmutableList.of("v2", "v3")));
    assertNotEquals(filter, Filter.notInArray("y", ImmutableList.of("v1", "v2")));
    assertNotEquals(filter, Filter.inArray("x", ImmutableList.of("v1", "v2")));
  }

  @Test
  public void or() {
    Filter filter =
        Filter.or(
            Filter.inArray("x", ImmutableList.of("v1", "v2")),
            Filter.inArray("y", ImmutableList.of("v3", "v4")));
    assertEquals(
        filter,
        Filter.or(
            Filter.inArray(FieldPath.of("x"), ImmutableList.of("v1", "v2")),
            Filter.inArray(FieldPath.of("y"), ImmutableList.of("v3", "v4"))));
    assertNotEquals(
        filter,
        Filter.and(
            Filter.inArray("x", ImmutableList.of("v1", "v2")),
            Filter.inArray("y", ImmutableList.of("v3", "v4"))));
    assertNotEquals(
        filter,
        Filter.or(
            Filter.inArray("y", ImmutableList.of("v3", "v4")),
            Filter.inArray("x", ImmutableList.of("v1", "v2"))));
  }

  @Test
  public void and() {
    Filter filter =
        Filter.and(
            Filter.inArray("x", ImmutableList.of("v1", "v2")),
            Filter.inArray("y", ImmutableList.of("v3", "v4")));
    assertEquals(
        filter,
        Filter.and(
            Filter.inArray(FieldPath.of("x"), ImmutableList.of("v1", "v2")),
            Filter.inArray(FieldPath.of("y"), ImmutableList.of("v3", "v4"))));
    assertNotEquals(
        filter,
        Filter.or(
            Filter.inArray("x", ImmutableList.of("v1", "v2")),
            Filter.inArray("y", ImmutableList.of("v3", "v4"))));
    assertNotEquals(
        filter,
        Filter.and(
            Filter.inArray("y", ImmutableList.of("v3", "v4")),
            Filter.inArray("x", ImmutableList.of("v1", "v2"))));
  }
}
