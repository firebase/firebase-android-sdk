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
import static com.google.firebase.firestore.testutil.TestUtil.filter;
import static com.google.firebase.firestore.testutil.TestUtil.orFilter;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.firebase.firestore.Filter;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class FilterTest {

  /** Helper method to get unique filters */
  FieldFilter filterName(String name) {
    return filter("name", "==", name);
  }

  private final FieldFilter A = filterName("A");
  private final FieldFilter B = filterName("B");
  private final FieldFilter C = filterName("C");
  private final FieldFilter D = filterName("D");
  private final FieldFilter E = filterName("E");
  private final FieldFilter F = filterName("F");
  private final FieldFilter G = filterName("G");
  private final FieldFilter H = filterName("H");
  private final FieldFilter I = filterName("I");

  @Test
  public void testFieldFilterMembers() {
    FieldFilter f = filter("foo", "==", "bar");
    assertEquals("foo", f.getField().toString());
    assertEquals("bar", f.getValue().getStringValue());
    assertEquals(FieldFilter.Operator.EQUAL, f.getOperator());
  }

  @Test
  public void testFieldFilterAssociativity() {
    FieldFilter f = filter("foo", "==", "bar");
    assertEquals(f, f.applyAssociativity());
  }

  @Test
  public void testFieldFilterDistributionOverFieldFilter() {
    FieldFilter filter1 = filter("key1", "==", "value1");
    FieldFilter filter2 = filter("key2", "==", "value2");
    assertThat(andFilter(filter1, filter2)).isEqualTo(filter1.applyDistribution(filter2));
    assertThat(andFilter(filter2, filter1)).isEqualTo(filter2.applyDistribution(filter1));
  }

  @Test
  public void testFieldFilterDistributionOverAndFilter() {
    // (f1 & f2) & f3 = (f1 & f2 & f3)
    FieldFilter filter1 = filter("key1", "==", "value1");
    FieldFilter filter2 = filter("key2", "==", "value2");
    FieldFilter filter3 = filter("key3", "==", "value3");
    CompositeFilter oneAndTwo = andFilter(filter1, filter2);
    CompositeFilter expected = andFilter(filter1, filter2, filter3);
    assertThat(expected).isEqualTo(filter3.applyDistribution(oneAndTwo));
  }

  @Test
  public void testFieldFilterDistributionOverOrFilter() {
    // (f1 | f2) & f3 = (f3 & f1) | (f3 & f2)
    FieldFilter f1 = filter("key1", "==", "value1");
    FieldFilter f2 = filter("key2", "==", "value2");
    FieldFilter f3 = filter("key3", "==", "value3");
    CompositeFilter oneAndTwo = orFilter(f1, f2);
    CompositeFilter expected = orFilter(andFilter(f3, f1), andFilter(f3, f2));
    assertThat(expected).isEqualTo(f3.applyDistribution(oneAndTwo));
  }

  @Test
  public void testCompositeFilterMembers() {
    FieldFilter f1 = filter("key1", "==", "value1");
    FieldFilter f2 = filter("key2", "==", "value2");
    FieldFilter f3 = filter("key3", "==", "value3");
    CompositeFilter andFilter = andFilter(f1, f2, f3);
    assertTrue(andFilter.isAnd());
    assertEquals(andFilter.getFilters(), Arrays.asList(f1, f2, f3));
    CompositeFilter orFilter = orFilter(f1, f2, f3);
    assertTrue(orFilter.isOr());
    assertEquals(andFilter.getFilters(), Arrays.asList(f1, f2, f3));
  }

  @Test
  public void testCompositeFilterNestedChecks() {
    FieldFilter f1 = filter("key1", "==", "value1");
    FieldFilter f2 = filter("key2", "==", "value2");
    FieldFilter f3 = filter("key3", "==", "value3");
    CompositeFilter andFilter1 = andFilter(f1, f2, f3);
    assertTrue(andFilter1.isFlatAndFilter());
    assertFalse(andFilter1.containsCompositeFilters());
    CompositeFilter orFilter1 = orFilter(f1, f2, f3);
    assertFalse(orFilter1.isFlatAndFilter());
    assertFalse(orFilter1.containsCompositeFilters());
    CompositeFilter andFilter2 = andFilter(f1, andFilter1);
    assertFalse(andFilter2.isFlatAndFilter());
    assertTrue(andFilter2.containsCompositeFilters());
    CompositeFilter orFilter2 = orFilter(f1, andFilter1);
    assertFalse(orFilter2.isFlatAndFilter());
    assertTrue(orFilter2.containsCompositeFilters());
  }

  @Test
  public void testCompositeFilterAssociativity() {
    // AND(AND(X)) --> X
    CompositeFilter cf1 = andFilter(andFilter(A));
    assertEquals(A, cf1.applyAssociativity());

    // OR(OR(X)) --> X
    CompositeFilter cf2 = orFilter(orFilter(A));
    assertEquals(A, cf2.applyAssociativity());

    // (A | (B) | ((C) | (D | E)) | (F | (G & (H & I))) --> A | B | C | D | E | F | (G & H & I)
    Filter complexFilter =
        orFilter(
            A,
            andFilter(B),
            orFilter(orFilter(C), orFilter(D, E)),
            orFilter(F, andFilter(G, andFilter(H, I))));
    Filter expectedResult = orFilter(A, B, C, D, E, F, andFilter(G, H, I));
    assertThat(complexFilter.applyAssociativity()).isEqualTo(expectedResult);
  }

  // The following four tests cover:
  // AND distribution for AND filter and AND filter.
  // AND distribution for OR filter and AND filter.
  // AND distribution for AND filter and OR filter.
  // AND distribution for OR filter and OR filter.
  @Test
  public void testAndFilterDistributionWithAndFilter() {
    // (A & B) , (C & D) --> (A & B & C & D)
    CompositeFilter expectedResult = andFilter(A, B, C, D);
    assertThat(andFilter(A, B).applyDistribution(andFilter(C, D))).isEqualTo(expectedResult);
  }

  @Test
  public void testAndFilterDistributionWithOrFilter() {
    // (A & B) , (C | D) --> (A & B & C) | (A & B & D)
    CompositeFilter expectedResult = orFilter(andFilter(A, B, C), andFilter(A, B, D));
    assertThat(andFilter(A, B).applyDistribution(orFilter(C, D))).isEqualTo(expectedResult);
  }

  @Test
  public void testOrFilterDistributionWithAndFilter() {
    // (A | B) , (C & D) --> (C & D & A) | (C & D & B)
    CompositeFilter expectedResult = orFilter(andFilter(C, D, A), andFilter(C, D, B));
    assertThat(orFilter(A, B).applyDistribution(andFilter(C, D))).isEqualTo(expectedResult);
  }

  @Test
  public void testOrFilterDistributionWithOrFilter() {
    // (A | B) , (C | D) --> (A & C) | (A & D) | (B & C) | (B & D)
    CompositeFilter expectedResult =
        orFilter(andFilter(A, C), andFilter(A, D), andFilter(B, C), andFilter(B, D));
    assertThat(orFilter(A, B).applyDistribution(orFilter(C, D))).isEqualTo(expectedResult);
  }

  @Test
  public void testFieldFilterComputeDnf() {
    assertThat(A.computeDnf()).isEqualTo(A);
  }

  @Test
  public void testComputeDnfFlatAndFilter() {
    CompositeFilter cf = andFilter(A, B, C);
    assertThat(cf.computeDnf()).isEqualTo(cf);
  }

  @Test
  public void testComputeDnfFlatOrFilter() {
    CompositeFilter cf = orFilter(A, B, C);
    assertThat(cf.computeDnf()).isEqualTo(cf);
  }

  @Test
  public void testComputeDnf1() {
    // A & (B | C) --> (A & B) | (A & C)
    CompositeFilter cf = andFilter(A, orFilter(B, C));
    CompositeFilter expectedResult = orFilter(andFilter(A, B), andFilter(A, C));
    assertThat(cf.computeDnf()).isEqualTo(expectedResult);
  }

  @Test
  public void testComputeDnf2() {
    // ((A)) & (B & C) --> A & B & C
    CompositeFilter cf = andFilter(andFilter(andFilter(A)), andFilter(B, C));
    CompositeFilter expectedResult = andFilter(A, B, C);
    assertThat(cf.computeDnf()).isEqualTo(expectedResult);
  }

  @Test
  public void testComputeDnf3() {
    // A | (B & C)
    CompositeFilter cf = orFilter(A, andFilter(B, C));
    assertThat(cf.computeDnf()).isEqualTo(cf);
  }

  @Test
  public void testComputeDnf4() {
    // A | (B & C) | ( ((D)) | (E | F) | (G & H) ) --> A | (B & C) | D | E | F | (G & H)
    CompositeFilter cf =
        orFilter(
            A, andFilter(B, C), orFilter(andFilter(orFilter(D)), orFilter(E, F), andFilter(G, H)));
    CompositeFilter expectedResult = orFilter(A, andFilter(B, C), D, E, F, andFilter(G, H));
    assertThat(cf.computeDnf()).isEqualTo(expectedResult);
  }

  @Test
  public void testComputeDnf5() {
    //    A & (B | C) & ( ((D)) & (E | F) & (G & H) )
    // -> A & (B | C) & D & (E | F) & G & H
    // -> ((A & B) | (A & C)) & D & (E | F) & G & H
    // -> ((A & B & D) | (A & C & D)) & (E|F) & G & H
    // -> ((A & B & D & E) | (A & B & D & F) | (A & C & D & E) | (A & C & D & F)) & G & H
    // -> ((A&B&D&E&G) | (A & B & D & F & G) | (A & C & D & E & G) | (A & C & D & F & G)) & H
    // -> (A&B&D&E&G&H) | (A&B&D&F&G&H) | (A & C & D & E & G & H) | (A & C & D & F & G & H)
    CompositeFilter cf =
        andFilter(
            A, orFilter(B, C), andFilter(andFilter(orFilter(D)), orFilter(E, F), andFilter(G, H)));
    CompositeFilter expectedResult =
        orFilter(
            andFilter(D, E, G, H, A, B),
            andFilter(D, F, G, H, A, B),
            andFilter(D, E, G, H, A, C),
            andFilter(D, F, G, H, A, C));
    assertThat(cf.computeDnf()).isEqualTo(expectedResult);
  }

  @Test
  public void testComputeDnf6() {
    // A & (B | (C & (D | (E & F))))
    // -> A & (B | (C & D) | (C & E & F))
    // -> (A & B) | (A & C & D) | (A & C & E & F)
    CompositeFilter cf = andFilter(A, orFilter(B, andFilter(C, orFilter(D, andFilter(E, F)))));
    CompositeFilter expectedResult =
        orFilter(andFilter(A, B), andFilter(C, D, A), andFilter(E, F, C, A));
    assertThat(cf.computeDnf()).isEqualTo(expectedResult);
  }

  @Test
  public void testComputeDnf7() {
    // ( (A|B) & (C|D) ) | ( (E|F) & (G|H) )
    // -> (A&C)|(A&D)|(B&C)(B&D)|(E&G)|(E&H)|(F&G)|(F&H)
    CompositeFilter cf =
        orFilter(
            andFilter(orFilter(A, B), orFilter(C, D)), andFilter(orFilter(E, F), orFilter(G, H)));
    CompositeFilter expectedResult =
        orFilter(
            andFilter(A, C),
            andFilter(A, D),
            andFilter(B, C),
            andFilter(B, D),
            andFilter(E, G),
            andFilter(E, H),
            andFilter(F, G),
            andFilter(F, H));
    assertThat(cf.computeDnf()).isEqualTo(expectedResult);
  }

  @Test
  public void testComputeDnf8() {
    // ( (A&B) | (C&D) ) & ( (E&F) | (G&H) )
    // -> A&B&E&F | A&B&G&H | C&D&E&F | C&D&G&H
    CompositeFilter cf =
        andFilter(
            orFilter(andFilter(A, B), andFilter(C, D)), orFilter(andFilter(E, F), andFilter(G, H)));
    CompositeFilter expectedResult =
        orFilter(
            andFilter(E, F, A, B),
            andFilter(G, H, A, B),
            andFilter(E, F, C, D),
            andFilter(G, H, C, D));
    assertThat(cf.computeDnf()).isEqualTo(expectedResult);
  }
}
