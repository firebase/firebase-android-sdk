// Copyright 2022 Google LLC
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

import static com.google.firebase.firestore.testutil.TestUtil.andFilters;
import static com.google.firebase.firestore.testutil.TestUtil.filter;
import static com.google.firebase.firestore.testutil.TestUtil.orFilters;
import static com.google.firebase.firestore.testutil.TestUtil.query;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class FilterTest {
  /** Helper method to get unique filters */
  FieldFilter nameFilter(String name) {
    return filter("name", "==", name);
  }

  private final FieldFilter A = nameFilter("A");
  private final FieldFilter B = nameFilter("B");
  private final FieldFilter C = nameFilter("C");
  private final FieldFilter D = nameFilter("D");

  @Test
  public void testFieldFilterMembers() {
    FieldFilter f = filter("foo", "==", "bar");
    assertEquals("foo", f.getField().toString());
    assertEquals("bar", f.getValue().getStringValue());
    assertEquals(FieldFilter.Operator.EQUAL, f.getOperator());
  }

  @Test
  public void testCompositeFilterMembers() {
    CompositeFilter andFilter = andFilters(A, B, C);
    assertTrue(andFilter.isConjunction());
    assertEquals(andFilter.getFilters(), Arrays.asList(A, B, C));

    CompositeFilter orFilter = orFilters(A, B, C);
    assertTrue(orFilter.isDisjunction());
    assertEquals(orFilter.getFilters(), Arrays.asList(A, B, C));
  }

  @Test
  public void testCompositeFilterNestedChecks() {
    CompositeFilter andFilter1 = andFilters(A, B, C);
    assertTrue(andFilter1.isFlat());
    assertTrue(andFilter1.isConjunction());
    assertFalse(andFilter1.isDisjunction());
    assertTrue(andFilter1.isFlatConjunction());

    CompositeFilter orFilter1 = orFilters(A, B, C);
    assertFalse(orFilter1.isConjunction());
    assertTrue(orFilter1.isDisjunction());
    assertTrue(orFilter1.isFlat());
    assertFalse(orFilter1.isFlatConjunction());

    CompositeFilter andFilter2 = andFilters(D, andFilter1);
    assertTrue(andFilter2.isConjunction());
    assertFalse(andFilter2.isDisjunction());
    assertFalse(andFilter2.isFlat());
    assertFalse(andFilter2.isFlatConjunction());

    CompositeFilter orFilter2 = orFilters(D, andFilter1);
    assertFalse(orFilter2.isConjunction());
    assertTrue(orFilter2.isDisjunction());
    assertFalse(orFilter2.isFlat());
    assertFalse(orFilter2.isFlatConjunction());
  }

  @Test
  public void testCanonicalIdOfFlatConjunctions() {
    Query q1 = query("col").filter(A).filter(B).filter(C);
    Query q2 = query("col").filter(andFilters(A, B, C));
    assertEquals(q1.getCanonicalId(), q2.getCanonicalId());
  }
}
