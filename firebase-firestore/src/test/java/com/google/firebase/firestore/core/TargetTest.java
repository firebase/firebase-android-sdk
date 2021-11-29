// Copyright 2021 Google LLC
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

package com.google.firebase.firestore.core;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.firestore.testutil.TestUtil.andFilter;
import static com.google.firebase.firestore.testutil.TestUtil.blob;
import static com.google.firebase.firestore.testutil.TestUtil.bound;
import static com.google.firebase.firestore.testutil.TestUtil.fieldIndex;
import static com.google.firebase.firestore.testutil.TestUtil.filter;
import static com.google.firebase.firestore.testutil.TestUtil.orderBy;
import static com.google.firebase.firestore.testutil.TestUtil.query;
import static com.google.firebase.firestore.testutil.TestUtil.wrap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.firebase.firestore.model.FieldIndex;
import com.google.firebase.firestore.model.Values;
import com.google.firestore.v1.Value;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class TargetTest {

  @Test
  public void emptyQueryBound() {
    Target target = query("c").toTarget();
    FieldIndex index = fieldIndex("c");

    Bound lowerBound = target.getLowerBoundForFilter(index, null);
    verifyBound(lowerBound, true);

    Bound upperBound = target.getUpperBoundForFilter(index, null);
    assertNull(upperBound);
  }

  @Test
  public void equalsQueryBound() {
    CompositeFilter filter = andFilter(filter("foo", "==", "bar"));
    Target target = query("c").filter(filter).toTarget();
    FieldIndex index = fieldIndex("c", "foo", FieldIndex.Segment.Kind.ASCENDING);

    Bound lowerBound = target.getLowerBoundForFilter(index, filter);
    verifyBound(lowerBound, true, "bar");

    Bound upperBound = target.getUpperBoundForFilter(index, filter);
    verifyBound(upperBound, true, "bar");
  }

  @Test
  public void lowerThanQueryBound() {
    CompositeFilter filter = andFilter(filter("foo", "<", "bar"));
    Target target = query("c").filter(filter).toTarget();
    FieldIndex index = fieldIndex("c", "foo", FieldIndex.Segment.Kind.DESCENDING);

    Bound lowerBound = target.getLowerBoundForFilter(index, filter);
    verifyBound(lowerBound, true, "");

    Bound upperBound = target.getUpperBoundForFilter(index, filter);
    verifyBound(upperBound, false, "bar");
  }

  @Test
  public void lowerThanOrEqualsQueryBound() {
    CompositeFilter filter = andFilter(filter("foo", "<=", "bar"));
    Target target = query("c").filter(filter).toTarget();
    FieldIndex index = fieldIndex("c", "foo", FieldIndex.Segment.Kind.ASCENDING);

    Bound lowerBound = target.getLowerBoundForFilter(index, filter);
    verifyBound(lowerBound, true, "");

    Bound upperBound = target.getUpperBoundForFilter(index, filter);
    verifyBound(upperBound, true, "bar");
  }

  @Test
  public void greaterThanQueryBound() {
    CompositeFilter filter = andFilter(filter("foo", ">", "bar"));
    Target target = query("c").filter(filter).toTarget();
    FieldIndex index = fieldIndex("c", "foo", FieldIndex.Segment.Kind.ASCENDING);

    Bound lowerBound = target.getLowerBoundForFilter(index, filter);
    verifyBound(lowerBound, false, "bar");

    Bound upperBound = target.getUpperBoundForFilter(index, filter);
    verifyBound(upperBound, false, blob());
  }

  @Test
  public void greaterThanOrEqualsQueryBound() {
    CompositeFilter filter = andFilter(filter("foo", ">=", "bar"));
    Target target = query("c").filter(filter).toTarget();
    FieldIndex index = fieldIndex("c", "foo", FieldIndex.Segment.Kind.DESCENDING);

    Bound lowerBound = target.getLowerBoundForFilter(index, filter);
    verifyBound(lowerBound, true, "bar");

    Bound upperBound = target.getUpperBoundForFilter(index, filter);
    verifyBound(upperBound, false, blob());
  }

  @Test
  public void arrayContainsQueryBound() {
    CompositeFilter filter = andFilter(filter("foo", "array-contains", "bar"));
    Target target = query("c").filter(filter).toTarget();
    FieldIndex index = fieldIndex("c", "foo", FieldIndex.Segment.Kind.CONTAINS);

    List<Value> arrayValues = target.getArrayValuesForFilter(index, filter);
    assertThat(arrayValues).containsExactly(wrap("bar"));

    Bound lowerBound = target.getLowerBoundForFilter(index, filter);
    verifyBound(lowerBound, true);

    Bound upperBound = target.getUpperBoundForFilter(index, filter);
    assertNull(upperBound);
  }

  @Test
  public void arrayContainsAnyQueryBound() {
    CompositeFilter filter =
        andFilter(filter("foo", "array-contains-any", Arrays.asList("bar", "baz")));
    Target target = query("c").filter(filter).toTarget();
    FieldIndex index = fieldIndex("c", "foo", FieldIndex.Segment.Kind.CONTAINS);

    List<Value> arrayValues = target.getArrayValuesForFilter(index, filter);
    assertThat(arrayValues).containsExactly(wrap("bar"), wrap("baz"));

    Bound lowerBound = target.getLowerBoundForFilter(index, filter);
    verifyBound(lowerBound, true);

    Bound upperBound = target.getUpperBoundForFilter(index, filter);
    assertNull(upperBound);
  }

  @Test
  public void orderByQueryBound() {
    Target target = query("c").orderBy(orderBy("foo")).toTarget();
    FieldIndex index = fieldIndex("c", "foo", FieldIndex.Segment.Kind.ASCENDING);

    Bound lowerBound = target.getLowerBoundForFilter(index, null);
    assertNull(lowerBound);

    Bound upperBound = target.getUpperBoundForFilter(index, null);
    assertNull(upperBound);
  }

  @Test
  public void filterWithOrderByQueryBound() {
    CompositeFilter filter = andFilter(filter("foo", ">", "bar"));
    Target target = query("c").filter(filter).orderBy(orderBy("foo")).toTarget();
    FieldIndex index = fieldIndex("c", "foo", FieldIndex.Segment.Kind.ASCENDING);

    Bound lowerBound = target.getLowerBoundForFilter(index, filter);
    verifyBound(lowerBound, false, "bar");

    Bound upperBound = target.getUpperBoundForFilter(index, filter);
    verifyBound(upperBound, false, blob());
  }

  @Test
  public void startAtQueryBound() {
    Target target =
        query("c").orderBy(orderBy("foo")).startAt(bound(/* inclusive= */ true, "bar")).toTarget();
    FieldIndex index = fieldIndex("c", "foo", FieldIndex.Segment.Kind.ASCENDING);

    Bound lowerBound = target.getLowerBoundForFilter(index, null);
    verifyBound(lowerBound, true, "bar");

    Bound upperBound = target.getUpperBoundForFilter(index, null);
    assertNull(upperBound);
  }

  @Test
  public void startAtWithFilterQueryBound() {
    // Tests that the startAt and the filter get merged to form a narrow bound
    CompositeFilter filter = andFilter(filter("a", ">=", "a1"), filter("b", "==", "b1"));
    Target target =
        query("c")
            .filter(filter)
            .orderBy(orderBy("a"))
            .orderBy(orderBy("b"))
            .startAt(bound(/* inclusive= */ true, "a1", "b1"))
            .toTarget();
    FieldIndex index =
        fieldIndex(
            "c", "a", FieldIndex.Segment.Kind.ASCENDING, "b", FieldIndex.Segment.Kind.ASCENDING);

    Bound lowerBound = target.getLowerBoundForFilter(index, filter);
    verifyBound(lowerBound, true, "a1", "b1");

    Bound upperBound = target.getUpperBoundForFilter(index, filter);
    verifyBound(upperBound, false, blob(), "b1");
  }

  @Test
  public void startAfterWithFilterQueryBound() {
    CompositeFilter filter = andFilter(filter("a", ">=", "a1"), filter("b", "==", "b1"));
    Target target =
        query("c")
            .filter(filter)
            .orderBy(orderBy("a"))
            .orderBy(orderBy("b"))
            .startAt(bound(/* inclusive= */ false, "a2", "b1"))
            .toTarget();
    FieldIndex index =
        fieldIndex(
            "c", "a", FieldIndex.Segment.Kind.ASCENDING, "b", FieldIndex.Segment.Kind.ASCENDING);

    Bound lowerBound = target.getLowerBoundForFilter(index, filter);
    verifyBound(lowerBound, false, "a2", "b1");

    Bound upperBound = target.getUpperBoundForFilter(index, filter);
    verifyBound(upperBound, false, blob(), "b1");
  }

  @Test
  public void startAfterDoesNotChangeBoundIfNotApplicable() {
    CompositeFilter filter = andFilter(filter("a", ">=", "a2"), filter("b", "==", "b2"));
    Target target =
        query("c")
            .filter(filter)
            .orderBy(orderBy("a"))
            .orderBy(orderBy("b"))
            .startAt(bound(/* inclusive= */ false, "a1", "b1"))
            .toTarget();
    FieldIndex index =
        fieldIndex(
            "c", "a", FieldIndex.Segment.Kind.ASCENDING, "b", FieldIndex.Segment.Kind.ASCENDING);

    Bound lowerBound = target.getLowerBoundForFilter(index, filter);
    verifyBound(lowerBound, true, "a2", "b2");

    Bound upperBound = target.getUpperBoundForFilter(index, filter);
    verifyBound(upperBound, false, blob(), "b2");
  }

  @Test
  public void endAtQueryBound() {
    Target target =
        query("c").orderBy(orderBy("foo")).endAt(bound(/* inclusive= */ true, "bar")).toTarget();
    FieldIndex index = fieldIndex("c", "foo", FieldIndex.Segment.Kind.ASCENDING);

    Bound lowerBound = target.getLowerBoundForFilter(index, null);
    assertNull(lowerBound);

    Bound upperBound = target.getUpperBoundForFilter(index, null);
    verifyBound(upperBound, true, "bar");
  }

  @Test
  public void endAtWithFilterQueryBound() {
    // Tests that the endAt and the filter get merged to form a narrow bound
    CompositeFilter filter = andFilter(filter("a", "<=", "a2"), filter("b", "==", "b2"));
    Target target =
        query("c")
            .filter(filter)
            .orderBy(orderBy("a"))
            .orderBy(orderBy("b"))
            .endAt(bound(/* inclusive= */ true, "a1", "b1"))
            .toTarget();
    FieldIndex index =
        fieldIndex(
            "c", "a", FieldIndex.Segment.Kind.ASCENDING, "b", FieldIndex.Segment.Kind.ASCENDING);

    Bound lowerBound = target.getLowerBoundForFilter(index, filter);
    verifyBound(lowerBound, true, "", "b2");

    Bound upperBound = target.getUpperBoundForFilter(index, filter);
    verifyBound(upperBound, true, "a1", "b1");
  }

  @Test
  public void endBeforeWithFilterQueryBound() {
    CompositeFilter filter = andFilter(filter("a", "<=", "a2"), filter("b", "==", "b2"));
    Target target =
        query("c")
            .filter(filter)
            .orderBy(orderBy("a"))
            .orderBy(orderBy("b"))
            .endAt(bound(/* inclusive= */ false, "a1", "b1"))
            .toTarget();
    FieldIndex index =
        fieldIndex(
            "c", "a", FieldIndex.Segment.Kind.ASCENDING, "b", FieldIndex.Segment.Kind.ASCENDING);

    Bound lowerBound = target.getLowerBoundForFilter(index, filter);
    verifyBound(lowerBound, true, "", "b2");

    Bound upperBound = target.getUpperBoundForFilter(index, filter);
    verifyBound(upperBound, false, "a1", "b1");
  }

  @Test
  public void endBeforeDoesNotChangeBoundIfNotApplicable() {
    CompositeFilter filter = andFilter(filter("a", "<=", "a1"), filter("b", "==", "b1"));
    Target target =
        query("c")
            .filter(filter)
            .orderBy(orderBy("a"))
            .orderBy(orderBy("b"))
            .endAt(bound(/* inclusive= */ false, "a2", "b2"))
            .toTarget();
    FieldIndex index =
        fieldIndex(
            "c", "a", FieldIndex.Segment.Kind.ASCENDING, "b", FieldIndex.Segment.Kind.ASCENDING);

    Bound lowerBound = target.getLowerBoundForFilter(index, filter);
    verifyBound(lowerBound, true, "", "b1");

    Bound upperBound = target.getUpperBoundForFilter(index, filter);
    verifyBound(upperBound, true, "a1", "b1");
  }

  @Test
  public void partialIndexMatchQueryBound() {
    CompositeFilter filter = andFilter(filter("a", "==", "a"), filter("b", "==", "b"));
    Target target = query("c").filter(filter).toTarget();
    FieldIndex index = fieldIndex("c", "a", FieldIndex.Segment.Kind.ASCENDING);

    Bound lowerBound = target.getLowerBoundForFilter(index, filter);
    verifyBound(lowerBound, true, "a");

    Bound upperBound = target.getUpperBoundForFilter(index, filter);
    verifyBound(upperBound, true, "a");
  }

  private void verifyBound(Bound bound, boolean inclusive, Object... values) {
    assertEquals("inclusive", inclusive, bound.isInclusive());
    List<Value> position = bound.getPosition();
    assertEquals("size", values.length, position.size());
    for (int i = 0; i < values.length; ++i) {
      Value expectedValue = wrap(values[i]);
      assertTrue(
          String.format(
              "Values should be equal: Expected: %s, Actual: %s",
              Values.canonicalId(expectedValue), Values.canonicalId(position.get(i))),
          Values.equals(position.get(i), expectedValue));
    }
  }
}
