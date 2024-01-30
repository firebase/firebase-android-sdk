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

package com.google.firebase.firestore.core;

import static com.google.firebase.firestore.model.DocumentKey.KEY_FIELD_NAME;
import static com.google.firebase.firestore.testutil.TestUtil.andFilters;
import static com.google.firebase.firestore.testutil.TestUtil.bound;
import static com.google.firebase.firestore.testutil.TestUtil.doc;
import static com.google.firebase.firestore.testutil.TestUtil.filter;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.testutil.TestUtil.orFilters;
import static com.google.firebase.firestore.testutil.TestUtil.orderBy;
import static com.google.firebase.firestore.testutil.TestUtil.path;
import static com.google.firebase.firestore.testutil.TestUtil.query;
import static com.google.firebase.firestore.testutil.TestUtil.ref;
import static com.google.firebase.firestore.testutil.TestUtil.testEquality;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Blob;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.MutableDocument;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.testutil.ComparatorTester;
import com.google.firebase.firestore.util.BackgroundQueue;
import com.google.firebase.firestore.util.Executors;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Tests Query */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class QueryTest {
  @Test
  public void testMatchesBasedDocumentKey() {
    ResourcePath queryPath = ResourcePath.fromString("rooms/eros/messages/1");
    MutableDocument doc1 = doc("rooms/eros/messages/1", 0, map("text", "msg1"));
    MutableDocument doc2 = doc("rooms/eros/messages/2", 0, map("text", "msg2"));
    MutableDocument doc3 = doc("rooms/other/messages/1", 0, map("text", "msg3"));

    Query query = Query.atPath(queryPath);
    assertTrue(query.matches(doc1));
    assertFalse(query.matches(doc2));
    assertFalse(query.matches(doc3));
  }

  @Test
  public void testMatchesShallowAncestorQuery() {
    ResourcePath queryPath = ResourcePath.fromString("rooms/eros/messages");
    MutableDocument doc1 = doc("rooms/eros/messages/1", 0, map("text", "msg1"));
    MutableDocument doc1meta = doc("rooms/eros/messages/1/meta/1", 0, map("meta", "meta-value"));
    MutableDocument doc2 = doc("rooms/eros/messages/2", 0, map("text", "msg2"));
    MutableDocument doc3 = doc("rooms/other/messages/1", 0, map("text", "msg3"));

    Query query = Query.atPath(queryPath);
    assertTrue(query.matches(doc1));
    assertFalse(query.matches(doc1meta));
    assertTrue(query.matches(doc2));
    assertFalse(query.matches(doc3));
  }

  @Test
  public void testEmptyFieldsAreAllowedForQueries() {
    ResourcePath queryPath = ResourcePath.fromString("rooms/eros/messages");
    MutableDocument doc1 = doc("rooms/eros/messages/1", 0, map("text", "msg1"));
    MutableDocument doc2 = doc("rooms/eros/messages/2", 0, map());

    Query query = Query.atPath(queryPath).filter(filter("text", "==", "msg1"));
    assertTrue(query.matches(doc1));
    assertFalse(query.matches(doc2));
  }

  @Test
  public void testPrimitiveValueFilter() {
    Query query1 =
        Query.atPath(ResourcePath.fromString("collection")).filter(filter("sort", ">=", 2));
    Query query2 =
        Query.atPath(ResourcePath.fromString("collection")).filter(filter("sort", "<=", 2));

    MutableDocument doc1 = doc("collection/1", 0, map("sort", 1));
    MutableDocument doc2 = doc("collection/2", 0, map("sort", 2));
    MutableDocument doc3 = doc("collection/3", 0, map("sort", 3));
    MutableDocument doc4 = doc("collection/4", 0, map("sort", false));
    MutableDocument doc5 = doc("collection/5", 0, map("sort", "string"));

    assertFalse(query1.matches(doc1));
    assertTrue(query1.matches(doc2));
    assertTrue(query1.matches(doc3));
    assertFalse(query1.matches(doc4));
    assertFalse(query1.matches(doc5));

    assertTrue(query2.matches(doc1));
    assertTrue(query2.matches(doc2));
    assertFalse(query2.matches(doc3));
    assertFalse(query2.matches(doc4));
    assertFalse(query2.matches(doc5));
  }

  @Test
  public void testArrayContainsFilters() {
    Query query =
        Query.atPath(ResourcePath.fromString("collection"))
            .filter(filter("array", "array-contains", 42L));

    // not an array
    MutableDocument document = doc("collection/1", 0, map("array", 1));
    assertFalse(query.matches(document));

    // empty array
    document = doc("collection/1", 0, map("array", asList()));
    assertFalse(query.matches(document));

    // array without element (and make sure it doesn't match in a nested field or a different field)
    document =
        doc(
            "collection/1",
            0,
            map("array", asList(41L, "42", map("a", 42L, "b", asList(42L))), "different", 42L));
    assertFalse(query.matches(document));

    // array with element
    document = doc("collection/1", 0, map("array", asList(1L, "2", 42L, map("a", 1L))));
    assertTrue(query.matches(document));
  }

  @Test
  public void testArrayContainsFiltersWithObjectValues() {
    // Search for arrays containing the object { a: [42] }
    Query query =
        Query.atPath(ResourcePath.fromString("collection"))
            .filter(filter("array", "array-contains", map("a", asList(42))));

    // array without element
    MutableDocument document =
        doc(
            "collection/1",
            0,
            map(
                "array",
                asList(
                    map("a", 42L),
                    map("a", asList(42L, 43L)),
                    map("b", asList(42L)),
                    map("a", asList(42L), "b", 42L))));
    assertFalse(query.matches(document));

    // array with element
    document = doc("collection/1", 0, map("array", asList(1L, "2", 42L, map("a", asList(42L)))));
    assertTrue(query.matches(document));
  }

  @Test
  public void testInFilters() {
    Query query =
        Query.atPath(ResourcePath.fromString("collection"))
            .filter(filter("zip", "in", asList(12345)));

    MutableDocument document = doc("collection/1", 0, map("zip", 12345));
    assertTrue(query.matches(document));

    // Value matches in array.
    document = doc("collection/1", 0, map("zip", asList(12345)));
    assertFalse(query.matches(document));

    // Non-type match.
    document = doc("collection/1", 0, map("zip", "12345"));
    assertFalse(query.matches(document));

    // Nested match.
    document = doc("collection/1", 0, map("zip", asList("12345", map("zip", 12345))));
    assertFalse(query.matches(document));
  }

  @Test
  public void testInFiltersWithObjectValues() {
    Query query =
        Query.atPath(ResourcePath.fromString("collection"))
            .filter(filter("zip", "in", asList(map("a", asList(42)))));

    // Containing object in array.
    MutableDocument document = doc("collection/1", 0, map("zip", asList(map("a", asList(42)))));
    assertFalse(query.matches(document));

    // Containing object.
    document = doc("collection/1", 0, map("zip", map("a", asList(42))));
    assertTrue(query.matches(document));
  }

  @Test
  public void testNotInFilters() {
    Query query =
        Query.atPath(ResourcePath.fromString("collection"))
            .filter(filter("zip", "not-in", asList(12345)));

    // No match.
    MutableDocument document = doc("collection/1", 0, map("zip", 23456));
    assertTrue(query.matches(document));

    // Value matches in array.
    document = doc("collection/1", 0, map("zip", asList(12345)));
    assertTrue(query.matches(document));

    // Non-type match.
    document = doc("collection/1", 0, map("zip", "12345"));
    assertTrue(query.matches(document));

    // Nested match.
    document = doc("collection/1", 0, map("zip", asList("12345", map("zip", 12345))));
    assertTrue(query.matches(document));

    // Null match.
    document = doc("collection/1", 0, map("zip", null));
    assertTrue(query.matches(document));

    // NaN match.
    document = doc("collection/1", 0, map("zip", Double.NaN));
    assertTrue(query.matches(document));
    document = doc("collection/1", 0, map("zip", Float.NaN));
    assertTrue(query.matches(document));

    // Direct match
    document = doc("collection/1", 0, map("zip", 12345));
    assertFalse(query.matches(document));

    // Direct match
    document = doc("collection/1", 0, map("chip", 23456));
    assertFalse(query.matches(document));
  }

  @Test
  public void testNotInFiltersWithObjectValues() {
    Query query =
        Query.atPath(ResourcePath.fromString("collection"))
            .filter(filter("zip", "not-in", asList(map("a", asList(42)))));

    // Containing object in array.
    MutableDocument document = doc("collection/1", 0, map("zip", asList(map("a", asList(42)))));
    assertTrue(query.matches(document));

    // Containing object.
    document = doc("collection/1", 0, map("zip", map("a", asList(42))));
    assertFalse(query.matches(document));
  }

  @Test
  public void testArrayContainsAnyFilters() {
    Query query =
        Query.atPath(ResourcePath.fromString("collection"))
            .filter(filter("zip", "array-contains-any", asList(12345)));

    MutableDocument document = doc("collection/1", 0, map("zip", asList(12345)));
    assertTrue(query.matches(document));

    // Value matches in non-array.
    document = doc("collection/1", 0, map("zip", 12345));
    assertFalse(query.matches(document));

    // Non-type match.
    document = doc("collection/1", 0, map("zip", asList("12345")));
    assertFalse(query.matches(document));

    // Nested match.
    document = doc("collection/1", 0, map("zip", asList("12345", map("zip", asList(12345)))));
    assertFalse(query.matches(document));
  }

  @Test
  public void testArrayContainsAnyFiltersWithObjectValues() {
    Query query =
        Query.atPath(ResourcePath.fromString("collection"))
            .filter(filter("zip", "array-contains-any", asList(map("a", asList(42)))));

    // Containing object in array.
    MutableDocument document = doc("collection/1", 0, map("zip", asList(map("a", asList(42)))));
    assertTrue(query.matches(document));

    // Containing object.
    document = doc("collection/1", 0, map("zip", map("a", asList(42))));
    assertFalse(query.matches(document));
  }

  @Test
  public void testNaNFilter() {
    Query query =
        Query.atPath(ResourcePath.fromString("collection"))
            .filter(filter("sort", "==", Double.NaN));
    MutableDocument doc1 = doc("collection/1", 0, map("sort", Double.NaN));
    MutableDocument doc2 = doc("collection/2", 0, map("sort", 2));
    MutableDocument doc3 = doc("collection/3", 0, map("sort", 3.1));
    MutableDocument doc4 = doc("collection/4", 0, map("sort", false));
    MutableDocument doc5 = doc("collection/5", 0, map("sort", "string"));
    MutableDocument doc6 = doc("collection/6", 0, map("sort", null));

    assertTrue(query.matches(doc1));
    assertFalse(query.matches(doc2));
    assertFalse(query.matches(doc3));
    assertFalse(query.matches(doc4));
    assertFalse(query.matches(doc5));
    assertFalse(query.matches(doc6));

    query =
        Query.atPath(ResourcePath.fromString("collection"))
            .filter(filter("sort", "!=", Double.NaN));
    assertFalse(query.matches(doc1));
    assertTrue(query.matches(doc2));
    assertTrue(query.matches(doc3));
    assertTrue(query.matches(doc4));
    assertTrue(query.matches(doc5));
    assertTrue(query.matches(doc6));
  }

  @Test
  public void testNullFilter() {
    Query query =
        Query.atPath(ResourcePath.fromString("collection")).filter(filter("sort", "==", null));
    MutableDocument doc1 = doc("collection/1", 0, map("sort", null));
    MutableDocument doc2 = doc("collection/2", 0, map("sort", 2));
    MutableDocument doc3 = doc("collection/3", 0, map("sort", 3.1));
    MutableDocument doc4 = doc("collection/4", 0, map("sort", false));
    MutableDocument doc5 = doc("collection/5", 0, map("sort", "string"));
    MutableDocument doc6 = doc("collection/6", 0, map("sort", Double.NaN));

    assertTrue(query.matches(doc1));
    assertFalse(query.matches(doc2));
    assertFalse(query.matches(doc3));
    assertFalse(query.matches(doc4));
    assertFalse(query.matches(doc5));
    assertFalse(query.matches(doc6));

    query = Query.atPath(ResourcePath.fromString("collection")).filter(filter("sort", "!=", null));
    assertFalse(query.matches(doc1));
    assertTrue(query.matches(doc2));
    assertTrue(query.matches(doc3));
    assertTrue(query.matches(doc4));
    assertTrue(query.matches(doc5));
    assertTrue(query.matches(doc6));
  }

  @Test
  public void testComplexObjectFilters() {
    Query query1 =
        Query.atPath(ResourcePath.fromString("collection")).filter(filter("sort", "<=", 2));
    Query query2 =
        Query.atPath(ResourcePath.fromString("collection")).filter(filter("sort", ">=", 2));

    MutableDocument doc1 = doc("collection/1", 0, map("sort", 2));
    MutableDocument doc2 = doc("collection/2", 0, map("sort", asList()));
    MutableDocument doc3 = doc("collection/3", 0, map("sort", asList(1)));
    MutableDocument doc4 = doc("collection/4", 0, map("sort", map("foo", 2)));
    MutableDocument doc5 = doc("collection/5", 0, map("sort", map("foo", "bar")));
    MutableDocument doc6 = doc("collection/6", 0, map("sort", map()));
    MutableDocument doc7 = doc("collection/7", 0, map("sort", asList(3, 1)));

    assertTrue(query1.matches(doc1));
    assertFalse(query1.matches(doc2));
    assertFalse(query1.matches(doc3));
    assertFalse(query1.matches(doc4));
    assertFalse(query1.matches(doc5));
    assertFalse(query1.matches(doc6));
    assertFalse(query1.matches(doc7));

    assertTrue(query2.matches(doc1));
    assertFalse(query2.matches(doc2));
    assertFalse(query2.matches(doc3));
    assertFalse(query2.matches(doc4));
    assertFalse(query2.matches(doc5));
    assertFalse(query2.matches(doc6));
    assertFalse(query2.matches(doc7));
  }

  @Test
  public void testDoesNotRemoveComplexObjectsWithOrderBy() {
    Query query = Query.atPath(ResourcePath.fromString("collection")).orderBy(orderBy("sort"));

    MutableDocument doc1 = doc("collection/1", 0, map("sort", 2));
    MutableDocument doc2 = doc("collection/2", 0, map("sort", asList()));
    MutableDocument doc3 = doc("collection/3", 0, map("sort", asList(1)));
    MutableDocument doc4 = doc("collection/4", 0, map("sort", map("foo", 2)));
    MutableDocument doc5 = doc("collection/5", 0, map("sort", map("foo", "bar")));

    assertTrue(query.matches(doc1));
    assertTrue(query.matches(doc2));
    assertTrue(query.matches(doc3));
    assertTrue(query.matches(doc4));
    assertTrue(query.matches(doc5));
  }

  @Test
  public void testFiltersArrays() {
    Query baseQuery = Query.atPath(ResourcePath.fromString("collection"));
    MutableDocument doc1 = doc("collection/doc", 0, map("tags", asList("foo", 1, true)));
    List<Filter> matchingFilters = asList(filter("tags", "==", asList("foo", 1, true)));

    List<Filter> nonMatchingFilters =
        asList(
            filter("tags", "==", "foo"),
            filter("tags", "==", asList("foo", 1)),
            filter("tags", "==", asList("foo", true, 1)));

    for (Filter filter : matchingFilters) {
      assertTrue(baseQuery.filter(filter).matches(doc1));
    }

    for (Filter filter : nonMatchingFilters) {
      assertFalse(baseQuery.filter(filter).matches(doc1));
    }
  }

  @Test
  public void testFiltersObjects() {
    Query baseQuery = Query.atPath(ResourcePath.fromString("collection"));
    MutableDocument doc1 =
        doc(
            "collection/doc",
            0,
            map("tags", map("foo", "foo", "a", 0, "b", true, "c", Double.NaN)));
    List<Filter> matchingFilters =
        asList(
            filter("tags", "==", map("foo", "foo", "a", 0, "b", true, "c", Double.NaN)),
            filter("tags", "==", map("b", true, "a", 0, "foo", "foo", "c", Double.NaN)),
            filter("tags.foo", "==", "foo"));

    List<Filter> nonMatchingFilters =
        asList(
            filter("tags", "==", "foo"),
            filter("tags", "==", map("foo", "foo", "a", 0, "b", true)));

    for (Filter filter : matchingFilters) {
      assertTrue(baseQuery.filter(filter).matches(doc1));
    }

    for (Filter filter : nonMatchingFilters) {
      assertFalse(baseQuery.filter(filter).matches(doc1));
    }
  }

  @Test
  public void testSortsDocuments() {
    Query query = Query.atPath(ResourcePath.fromString("collection")).orderBy(orderBy("sort"));
    new ComparatorTester(query.comparator())
        .addEqualityGroup(doc("collection/1", 0, map("sort", null)))
        .addEqualityGroup(doc("collection/1", 0, map("sort", false)))
        .addEqualityGroup(doc("collection/1", 0, map("sort", true)))
        .addEqualityGroup(doc("collection/1", 0, map("sort", 1)))
        .addEqualityGroup(doc("collection/2", 0, map("sort", 1))) // by key
        .addEqualityGroup(doc("collection/3", 0, map("sort", 1))) // by key
        .addEqualityGroup(doc("collection/1", 0, map("sort", 1.9)))
        .addEqualityGroup(doc("collection/1", 0, map("sort", 2)))
        .addEqualityGroup(doc("collection/1", 0, map("sort", 2.1)))
        .addEqualityGroup(doc("collection/1", 0, map("sort", "")))
        .addEqualityGroup(doc("collection/1", 0, map("sort", "a")))
        .addEqualityGroup(doc("collection/1", 0, map("sort", "ab")))
        .addEqualityGroup(doc("collection/1", 0, map("sort", "b")))
        .addEqualityGroup(doc("collection/1", 0, map("sort", ref("collection/id1"))))
        .testCompare();
  }

  @Test
  public void testSortsWithMultipleFields() {
    Query query =
        Query.atPath(ResourcePath.fromString("collection"))
            .orderBy(orderBy("sort1"))
            .orderBy(orderBy("sort2"));

    new ComparatorTester(query.comparator())
        .addEqualityGroup(doc("collection/1", 0, map("sort1", 1, "sort2", 1)))
        .addEqualityGroup(doc("collection/1", 0, map("sort1", 1, "sort2", 2)))
        .addEqualityGroup(doc("collection/2", 0, map("sort1", 1, "sort2", 2))) // by key
        .addEqualityGroup(doc("collection/3", 0, map("sort1", 1, "sort2", 2))) // by key
        .addEqualityGroup(doc("collection/1", 0, map("sort1", 1, "sort2", 3)))
        .addEqualityGroup(doc("collection/1", 0, map("sort1", 2, "sort2", 1)))
        .addEqualityGroup(doc("collection/1", 0, map("sort1", 2, "sort2", 2)))
        .addEqualityGroup(doc("collection/2", 0, map("sort1", 2, "sort2", 2))) // by key
        .addEqualityGroup(doc("collection/3", 0, map("sort1", 2, "sort2", 2))) // by key
        .addEqualityGroup(doc("collection/1", 0, map("sort1", 2, "sort2", 3)))
        .testCompare();
  }

  @Test
  public void testSortsDescending() {
    Query query =
        Query.atPath(ResourcePath.fromString("collection"))
            .orderBy(orderBy("sort1", "desc"))
            .orderBy(orderBy("sort2", "desc"));

    new ComparatorTester(query.comparator())
        .addEqualityGroup(doc("collection/1", 0, map("sort1", 2, "sort2", 3)))
        .addEqualityGroup(doc("collection/3", 0, map("sort1", 2, "sort2", 2)))
        .addEqualityGroup(doc("collection/2", 0, map("sort1", 2, "sort2", 2))) // by key
        .addEqualityGroup(doc("collection/1", 0, map("sort1", 2, "sort2", 2))) // by key
        .addEqualityGroup(doc("collection/1", 0, map("sort1", 2, "sort2", 1)))
        .addEqualityGroup(doc("collection/1", 0, map("sort1", 1, "sort2", 3)))
        .addEqualityGroup(doc("collection/3", 0, map("sort1", 1, "sort2", 2)))
        .addEqualityGroup(doc("collection/2", 0, map("sort1", 1, "sort2", 2))) // by key
        .addEqualityGroup(doc("collection/1", 0, map("sort1", 1, "sort2", 2))) // by key
        .addEqualityGroup(doc("collection/1", 0, map("sort1", 1, "sort2", 1)))
        .testCompare();
  }

  @Test
  public void testHashCode() {
    Query q1a =
        Query.atPath(ResourcePath.fromString("foo"))
            .filter(filter("i1", "<", 2))
            .filter(filter("i2", "==", 3));

    // TODO uncomment this when hashcode does not depend on filter order.
    /*
    Query q1b =
        Query.atPath(ResourcePath.fromString("foo"))
            .filter(filter("i2", "==", 3))
            .filter(filter("i1", "<", 2));
    */

    Query q2a = Query.atPath(ResourcePath.fromString("foo"));
    Query q2b = Query.atPath(ResourcePath.fromString("foo"));

    Query q3a = Query.atPath(ResourcePath.fromString("foo/bar"));
    Query q3b = Query.atPath(ResourcePath.fromString("foo/bar"));

    Query q4a =
        Query.atPath(ResourcePath.fromString("foo"))
            .orderBy(orderBy("foo"))
            .orderBy(orderBy("bar"));
    Query q4b =
        Query.atPath(ResourcePath.fromString("foo"))
            .orderBy(orderBy("foo"))
            .orderBy(orderBy("bar"));

    Query q5a =
        Query.atPath(ResourcePath.fromString("foo"))
            .orderBy(orderBy("bar"))
            .orderBy(orderBy("foo"));

    Query q6a =
        Query.atPath(ResourcePath.fromString("foo"))
            .filter(filter("bar", ">", 2))
            .orderBy(orderBy("bar"));

    Query q7a = Query.atPath(ResourcePath.fromString("foo")).limitToFirst(10);

    // TODO: Add test cases with{Lower,Upper}Bound once cursors are implemented.
    testEquality(
        asList(
            asList(q1a.hashCode()),
            asList(q2a.hashCode(), q2b.hashCode()),
            asList(q3a.hashCode(), q3b.hashCode()),
            asList(q4a.hashCode(), q4b.hashCode()),
            asList(q5a.hashCode()),
            asList(q6a.hashCode()),
            asList(q7a.hashCode())));
  }

  @Test
  public void testImplicitOrderBy() {
    Query baseQuery = Query.atPath(path("foo"));
    // Default is ascending
    assertEquals(asList(orderBy(KEY_FIELD_NAME, "asc")), baseQuery.getNormalizedOrderBy());

    // Explicit key ordering is respected
    assertEquals(
        asList(orderBy(KEY_FIELD_NAME, "asc")),
        baseQuery.orderBy(orderBy(KEY_FIELD_NAME, "asc")).getNormalizedOrderBy());
    assertEquals(
        asList(orderBy(KEY_FIELD_NAME, "desc")),
        baseQuery.orderBy(orderBy(KEY_FIELD_NAME, "desc")).getNormalizedOrderBy());
    assertEquals(
        asList(orderBy("foo"), orderBy(KEY_FIELD_NAME, "asc")),
        baseQuery
            .orderBy(orderBy("foo"))
            .orderBy(orderBy(KEY_FIELD_NAME, "asc"))
            .getNormalizedOrderBy());
    assertEquals(
        asList(orderBy("foo"), orderBy(KEY_FIELD_NAME, "desc")),
        baseQuery
            .orderBy(orderBy("foo"))
            .orderBy(orderBy(KEY_FIELD_NAME, "desc"))
            .getNormalizedOrderBy());

    // Inequality filters add order bys
    assertEquals(
        asList(orderBy("foo"), orderBy(KEY_FIELD_NAME, "asc")),
        baseQuery.filter(filter("foo", "<", 5)).getNormalizedOrderBy());

    // Descending order by applies to implicit key ordering
    assertEquals(
        asList(orderBy("foo", "desc"), orderBy(KEY_FIELD_NAME, "desc")),
        baseQuery.orderBy(orderBy("foo", "desc")).getNormalizedOrderBy());
    assertEquals(
        asList(orderBy("foo", "asc"), orderBy("bar", "desc"), orderBy(KEY_FIELD_NAME, "desc")),
        baseQuery
            .orderBy(orderBy("foo", "asc"))
            .orderBy(orderBy("bar", "desc"))
            .getNormalizedOrderBy());
    assertEquals(
        asList(orderBy("foo", "desc"), orderBy("bar", "asc"), orderBy(KEY_FIELD_NAME, "asc")),
        baseQuery
            .orderBy(orderBy("foo", "desc"))
            .orderBy(orderBy("bar", "asc"))
            .getNormalizedOrderBy());
  }

  @Test
  public void testImplicitOrderByInMultipleInequality() {
    Query baseQuery = Query.atPath(path("foo"));
    assertEquals(
        asList(
            orderBy("A", "asc"),
            orderBy("a", "asc"),
            orderBy("aa", "asc"),
            orderBy("b", "asc"),
            orderBy(KEY_FIELD_NAME, "asc")),
        baseQuery
            .filter(filter("a", "<", 5))
            .filter(filter("a", ">=", 5))
            .filter(filter("aa", "<", 5))
            .filter(filter("b", "<", 5))
            .filter(filter("A", "<", 5))
            .getNormalizedOrderBy());

    // numbers
    assertEquals(
        asList(
            orderBy("1", "asc"),
            orderBy("19", "asc"),
            orderBy("2", "asc"),
            orderBy("a", "asc"),
            orderBy(KEY_FIELD_NAME, "asc")),
        baseQuery
            .filter(filter("a", "<", 5))
            .filter(filter("1", "<", 5))
            .filter(filter("2", "<", 5))
            .filter(filter("19", "<", 5))
            .getNormalizedOrderBy());

    // nested fields
    assertEquals(
        asList(
            orderBy("a", "asc"),
            orderBy("a.a", "asc"),
            orderBy("aa", "asc"),
            orderBy(KEY_FIELD_NAME, "asc")),
        baseQuery
            .filter(filter("a", "<", 5))
            .filter(filter("aa", "<", 5))
            .filter(filter("a.a", "<", 5))
            .getNormalizedOrderBy());

    // special characters
    assertEquals(
        asList(
            orderBy("_a", "asc"),
            orderBy("a", "asc"),
            orderBy("a.a", "asc"),
            orderBy(KEY_FIELD_NAME, "asc")),
        baseQuery
            .filter(filter("a", "<", 5))
            .filter(filter("_a", "<", 5))
            .filter(filter("a.a", "<", 5))
            .getNormalizedOrderBy());

    // field name with dot
    assertEquals(
        asList(
            orderBy("a", "asc"),
            orderBy("a.z", "asc"),
            orderBy("`a.a`", "asc"),
            orderBy(KEY_FIELD_NAME, "asc")),
        baseQuery
            .filter(filter("a", "<", 5))
            .filter(filter("`a.a`", "<", 5)) // Field name with dot
            .filter(filter("a.z", "<", 5)) // Nested field
            .getNormalizedOrderBy());

    // composite filter
    assertEquals(
        asList(
            orderBy("a", "asc"),
            orderBy("b", "asc"),
            orderBy("c", "asc"),
            orderBy("d", "asc"),
            orderBy(KEY_FIELD_NAME, "asc")),
        baseQuery
            .filter(filter("a", "<", 5))
            .filter(
                andFilters(
                    orFilters(filter("b", ">=", 1), filter("c", "<=", 1)),
                    orFilters(filter("d", "<=", 1), filter("e", "==", 1))))
            .getNormalizedOrderBy());

    // OrderBy
    assertEquals(
        asList(
            orderBy("z", "asc"),
            orderBy("a", "asc"),
            orderBy("b", "asc"),
            orderBy(KEY_FIELD_NAME, "asc")),
        baseQuery
            .filter(filter("b", "<", 5))
            .filter(filter("a", "<", 5))
            .filter(filter("z", "<", 5))
            .orderBy(orderBy("z"))
            .getNormalizedOrderBy());

    // last explicit order by direction
    assertEquals(
        asList(
            orderBy("z", "desc"),
            orderBy("a", "desc"),
            orderBy("b", "desc"),
            orderBy(KEY_FIELD_NAME, "desc")),
        baseQuery
            .filter(filter("b", "<", 5))
            .filter(filter("a", "<", 5))
            .orderBy(orderBy("z", "desc"))
            .getNormalizedOrderBy());

    assertEquals(
        asList(
            orderBy("z", "desc"),
            orderBy("c", "asc"),
            orderBy("a", "asc"),
            orderBy("b", "asc"),
            orderBy(KEY_FIELD_NAME, "asc")),
        baseQuery
            .filter(filter("b", "<", 5))
            .filter(filter("a", "<", 5))
            .orderBy(orderBy("z", "desc"))
            .orderBy(orderBy("c"))
            .getNormalizedOrderBy());
  }

  @Test
  public void testMatchesAllDocuments() {
    Query baseQuery = Query.atPath(ResourcePath.fromString("collection"));
    assertTrue(baseQuery.matchesAllDocuments());

    Query query = baseQuery.orderBy(orderBy("__name__"));
    assertTrue(query.matchesAllDocuments());

    query = baseQuery.orderBy(orderBy("foo"));
    assertFalse(query.matchesAllDocuments());

    query = baseQuery.filter(filter("foo", "==", "bar"));
    assertFalse(query.matchesAllDocuments());

    query = baseQuery.limitToFirst(1);
    assertFalse(query.matchesAllDocuments());

    query = baseQuery.startAt(bound(true));
    assertFalse(query.matchesAllDocuments());

    query = baseQuery.endAt(bound(true));
    assertFalse(query.matchesAllDocuments());
  }

  @Test
  public void testCanonicalIdsAreStable() {
    // This test aims to ensure that we do not break canonical IDs, as they are used as keys in
    // the TargetCache.

    Query baseQuery = Query.atPath(ResourcePath.fromString("collection"));

    assertCanonicalId(baseQuery, "collection|f:|ob:__name__asc");
    assertCanonicalId(
        baseQuery.filter(filter("a", ">", "a")), "collection|f:a>a|ob:aasc__name__asc");
    assertCanonicalId(
        baseQuery.filter(filter("a", "<=", new GeoPoint(90.0, -90.0))),
        "collection|f:a<=geo(90.0,-90.0)|ob:aasc__name__asc");
    assertCanonicalId(
        baseQuery.filter(filter("a", "<=", new Timestamp(60, 3000))),
        "collection|f:a<=time(60,3000)|ob:aasc__name__asc");
    assertCanonicalId(
        baseQuery.filter(filter("a", ">=", Blob.fromBytes(new byte[] {1, 2, 3}))),
        "collection|f:a>=010203|ob:aasc__name__asc");
    assertCanonicalId(
        baseQuery.filter(filter("a", "==", Arrays.asList(1, 2, 3))),
        "collection|f:a==[1,2,3]|ob:__name__asc");
    assertCanonicalId(
        baseQuery.filter(filter("a", "!=", Arrays.asList(1, 2, 3))),
        "collection|f:a!=[1,2,3]|ob:aasc__name__asc");
    assertCanonicalId(
        baseQuery.filter(filter("a", "==", Double.NaN)), "collection|f:a==NaN|ob:__name__asc");
    assertCanonicalId(
        baseQuery.filter(filter("__name__", "==", ref("collection/id"))),
        "collection|f:__name__==collection/id|ob:__name__asc");
    assertCanonicalId(
        baseQuery.filter(filter("a", "==", map("a", "b", "inner", map("d", "c")))),
        "collection|f:a=={a:b,inner:{d:c}}|ob:__name__asc");
    assertCanonicalId(
        baseQuery.filter(filter("a", "in", Arrays.asList(1, 2, 3))),
        "collection|f:ain[1,2,3]|ob:__name__asc");
    assertCanonicalId(
        baseQuery.filter(filter("a", "not-in", Arrays.asList(1, 2, 3))),
        "collection|f:anot_in[1,2,3]|ob:aasc__name__asc");
    assertCanonicalId(
        baseQuery.filter(filter("a", "array-contains-any", Arrays.asList(1, 2, 3))),
        "collection|f:aarray_contains_any[1,2,3]|ob:__name__asc");
    assertCanonicalId(
        baseQuery.filter(filter("a", "array-contains", "a")),
        "collection|f:aarray_containsa|ob:__name__asc");
    assertCanonicalId(baseQuery.orderBy(orderBy("a")), "collection|f:|ob:aasc__name__asc");
    assertCanonicalId(
        baseQuery
            .orderBy(orderBy("a"))
            .startAt(bound(/* inclusive= */ true, "foo", Arrays.asList(1, 2, 3))),
        "collection|f:|ob:aasc__name__asc|lb:b:foo,[1,2,3]");
    assertCanonicalId(
        baseQuery
            .orderBy(orderBy("a"))
            .endAt(bound(/* inclusive= */ true, "foo", Arrays.asList(1, 2, 3))),
        "collection|f:|ob:aasc__name__asc|ub:a:foo,[1,2,3]");
    assertCanonicalId(baseQuery.limitToFirst(5), "collection|f:|ob:__name__asc|l:5");
    assertCanonicalId(baseQuery.limitToLast(5), "collection|f:|ob:__name__desc|l:5");
  }

  private void assertCanonicalId(Query query, String expectedCanonicalId) {
    assertEquals(expectedCanonicalId, query.toTarget().getCanonicalId());
  }

  @Test
  public void testOrQuery() {
    MutableDocument doc1 = doc("collection/1", 0, map("a", 1, "b", 0));
    MutableDocument doc2 = doc("collection/2", 0, map("a", 2, "b", 1));
    MutableDocument doc3 = doc("collection/3", 0, map("a", 3, "b", 2));
    MutableDocument doc4 = doc("collection/4", 0, map("a", 1, "b", 3));
    MutableDocument doc5 = doc("collection/5", 0, map("a", 1, "b", 1));

    // Two equalities: a==1 || b==1.
    Query query1 =
        query("collection").filter(orFilters(filter("a", "==", 1), filter("b", "==", 1)));
    assertQueryMatches(
        query1,
        /* match */ Arrays.asList(doc1, doc2, doc4, doc5),
        /* not match */ Arrays.asList(doc3));

    // with one inequality: a>2 || b==1.
    Query query2 = query("collection").filter(orFilters(filter("a", ">", 2), filter("b", "==", 1)));
    assertQueryMatches(
        query2,
        /* match */ Arrays.asList(doc2, doc3, doc5),
        /* not match */ Arrays.asList(doc1, doc4));

    // (a==1 && b==0) || (a==3 && b==2)
    Query query3 =
        query("collection")
            .filter(
                orFilters(
                    andFilters(filter("a", "==", 1), filter("b", "==", 0)),
                    andFilters(filter("a", "==", 3), filter("b", "==", 2))));
    assertQueryMatches(
        query3,
        /* match */ Arrays.asList(doc1, doc3),
        /* not match */ Arrays.asList(doc2, doc4, doc5));

    // a==1 && (b==0 || b==3).
    Query query4 =
        query("collection")
            .filter(
                andFilters(
                    filter("a", "==", 1), orFilters(filter("b", "==", 0), filter("b", "==", 3))));
    assertQueryMatches(
        query4,
        /* match */ Arrays.asList(doc1, doc4),
        /* not match */ Arrays.asList(doc2, doc3, doc5));

    // (a==2 || b==2) && (a==3 || b==3)
    Query query5 =
        query("collection")
            .filter(
                andFilters(
                    orFilters(filter("a", "==", 2), filter("b", "==", 2)),
                    orFilters(filter("a", "==", 3), filter("b", "==", 3))));
    assertQueryMatches(
        query5,
        /* match */ Arrays.asList(doc3),
        /* not match */ Arrays.asList(doc1, doc2, doc4, doc5));
  }

  @Test
  public void testSynchronousMatchesOrderBy() {
    List<MutableDocument> docs = new ArrayList<>();

    // Add one hundred documents to the collection, each with many fields (26).
    // These will match the query and order by
    for (int i = 0; i < 100; i++) {
      docs.add(
          doc(
              "collection/" + i,
              0,
              map(
                  "a", 2,
                  "b", 2,
                  "c", 2,
                  "d", 2,
                  "e", 2,
                  "f", 2,
                  "g", 2,
                  "h", 2,
                  "i", 2,
                  "j", 2,
                  "k", 2,
                  "l", 2,
                  "m", 2,
                  "n", 2,
                  "o", 2,
                  "p", 2,
                  "q", 2,
                  "r", 2,
                  "s", 2,
                  "t", 2,
                  "u", 2,
                  "v", 2,
                  "w", 2,
                  "x", 2,
                  "y", 2,
                  "z", 2)));
    }

    // Add the an additional document to the collection, which
    // will match the query but not the order by.
    docs.add(doc("collection/100", 0, map("a", 2)));

    // Create a query that orders by many fields (26).
    // We are testing matching on order by in parallel
    // and we want to have a large set of order bys to
    // force more concurrency.
    Query query =
        query("collection")
            .filter(filter("a", ">", 1))
            .orderBy(orderBy("a"))
            .orderBy(orderBy("b"))
            .orderBy(orderBy("c"))
            .orderBy(orderBy("d"))
            .orderBy(orderBy("e"))
            .orderBy(orderBy("f"))
            .orderBy(orderBy("g"))
            .orderBy(orderBy("h"))
            .orderBy(orderBy("i"))
            .orderBy(orderBy("j"))
            .orderBy(orderBy("k"))
            .orderBy(orderBy("l"))
            .orderBy(orderBy("m"))
            .orderBy(orderBy("n"))
            .orderBy(orderBy("o"))
            .orderBy(orderBy("p"))
            .orderBy(orderBy("q"))
            .orderBy(orderBy("r"))
            .orderBy(orderBy("s"))
            .orderBy(orderBy("t"))
            .orderBy(orderBy("u"))
            .orderBy(orderBy("v"))
            .orderBy(orderBy("w"))
            .orderBy(orderBy("x"))
            .orderBy(orderBy("y"))
            .orderBy(orderBy("z"));

    // We're going to emulate the multi-threaded document matching performed in
    // SQLiteRemoteDocumentCache.getAll(...), where `query.matches(doc)` is performed
    // for many different docs concurrently on the BackgroundQueue.
    Iterator<MutableDocument> iterator = docs.iterator();
    BackgroundQueue backgroundQueue = new BackgroundQueue();
    Map<DocumentKey, Boolean> results = new HashMap<>();

    while (iterator.hasNext()) {
      MutableDocument doc = iterator.next();
      // Only put the processing in the backgroundQueue if there are more documents
      // in the list. This behavior matches SQLiteRemoteDocumentCache.getAll(...)
      Executor executor = iterator.hasNext() ? backgroundQueue : Executors.DIRECT_EXECUTOR;
      executor.execute(
          () -> {
            // We call query.matches() to indirectly test query.matchesOrderBy()
            boolean result = query.matches(doc);

            // We will include a synchronized block in our command to simulate
            // the implementation in SQLiteRemoteDocumentCache.getAll(...)
            synchronized (results) {
              results.put(doc.getKey(), result);
            }
          });
    }

    backgroundQueue.drain();

    Assert.assertEquals(101, results.keySet().size());
    for (DocumentKey key : results.keySet()) {
      // Only for document 100 do we expect the match to be false
      // otherwise it will be true.
      if (key.compareTo(DocumentKey.fromPathString("collection/100")) == 0) {
        Assert.assertEquals(false, results.get(key));
      } else {
        Assert.assertEquals(true, results.get(key));
      }
    }
  }

  @Test
  public void testGetOrderByReturnsUnmodifiableList() {
    Query query =
        query("collection")
            .filter(filter("a", ">", 1))
            .orderBy(orderBy("a"))
            .orderBy(orderBy("b"))
            .orderBy(orderBy("c"))
            .orderBy(orderBy("d"))
            .orderBy(orderBy("e"))
            .orderBy(orderBy("f"));

    List<OrderBy> orderByList = query.getNormalizedOrderBy();

    assertThrows(UnsupportedOperationException.class, () -> orderByList.add(orderBy("g")));
  }

  @Test
  public void testOrderByForAggregateAndNonAggregate() {
    Query col = query("collection");

    // Build two identical queries
    Query query1 = col.filter(filter("foo", ">", 1));
    Query query2 = col.filter(filter("foo", ">", 1));

    // Compute an aggregate and non-aggregate target from the queries
    Target aggregateTarget = query1.toAggregateTarget();
    Target target = query2.toTarget();

    assertEquals(aggregateTarget.getOrderBy().size(), 0);

    assertEquals(target.getOrderBy().size(), 2);
    assertEquals(target.getOrderBy().get(0).getDirection(), OrderBy.Direction.ASCENDING);
    assertEquals(target.getOrderBy().get(0).getField().toString(), "foo");
    assertEquals(target.getOrderBy().get(1).getDirection(), OrderBy.Direction.ASCENDING);
    assertEquals(target.getOrderBy().get(1).getField().toString(), "__name__");
  }

  @Test
  public void testGeneratedOrderBysNotAffectedByPreviouslyMemoizedTargets() {
    Query col = query("collection");

    // Build two identical queries
    Query query1 = col.filter(filter("foo", ">", 1));
    Query query2 = col.filter(filter("foo", ">", 1));

    // query1 - first to aggregate target, then to non-aggregate target
    Target aggregateTarget1 = query1.toAggregateTarget();
    Target target1 = query1.toTarget();

    // query2 - first to non-aggregate target, then to aggregate target
    Target target2 = query2.toTarget();
    Target aggregateTarget2 = query2.toAggregateTarget();

    assertEquals(aggregateTarget1.getOrderBy().size(), 0);

    assertEquals(aggregateTarget2.getOrderBy().size(), 0);

    assertEquals(target1.getOrderBy().size(), 2);
    assertEquals(target1.getOrderBy().get(0).getDirection(), OrderBy.Direction.ASCENDING);
    assertEquals(target1.getOrderBy().get(0).getField().toString(), "foo");
    assertEquals(target1.getOrderBy().get(1).getDirection(), OrderBy.Direction.ASCENDING);
    assertEquals(target1.getOrderBy().get(1).getField().toString(), "__name__");

    assertEquals(target2.getOrderBy().size(), 2);
    assertEquals(target2.getOrderBy().get(0).getDirection(), OrderBy.Direction.ASCENDING);
    assertEquals(target2.getOrderBy().get(0).getField().toString(), "foo");
    assertEquals(target2.getOrderBy().get(1).getDirection(), OrderBy.Direction.ASCENDING);
    assertEquals(target2.getOrderBy().get(1).getField().toString(), "__name__");
  }

  private void assertQueryMatches(
      Query query, List<MutableDocument> matching, List<MutableDocument> nonMatching) {
    for (MutableDocument doc : matching) {
      assertTrue(query.matches(doc));
    }
    for (MutableDocument doc : nonMatching) {
      assertFalse(query.matches(doc));
    }
  }
}
