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

package com.google.firebase.firestore;

import static com.google.firebase.firestore.Filter.and;
import static com.google.firebase.firestore.Filter.arrayContains;
import static com.google.firebase.firestore.Filter.arrayContainsAny;
import static com.google.firebase.firestore.Filter.equalTo;
import static com.google.firebase.firestore.Filter.inArray;
import static com.google.firebase.firestore.Filter.or;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.checkQueryAndPipelineResultsMatch;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.getBackendEdition;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.nullList;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.pipelineSnapshotToIds;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.pipelineSnapshotToValues;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testCollection;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testCollectionWithDocs;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testFirestore;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitFor;
import static com.google.firebase.firestore.testutil.TestUtil.expectError;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.Task;
import com.google.common.collect.Lists;
import com.google.firebase.firestore.Query.Direction;
import com.google.firebase.firestore.testutil.IntegrationTestUtil;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class QueryToPipelineTest {

  @Before
  public void setUp() {
    assumeTrue(getBackendEdition() == IntegrationTestUtil.BackendEdition.ENTERPRISE);
  }

  @After
  public void tearDown() {
    IntegrationTestUtil.tearDown();
  }

  @Test
  public void testLimitQueries() {
    CollectionReference collection =
        testCollectionWithDocs(
            map(
                "a", map("k", "a"),
                "b", map("k", "b"),
                "c", map("k", "c")));

    Query query = collection.limit(2);
    FirebaseFirestore db = collection.firestore;
    Pipeline.Snapshot set = waitFor(db.pipeline().createFrom(query).execute());
    List<Map<String, Object>> data = pipelineSnapshotToValues(set);
    assertEquals(asList(map("k", "a"), map("k", "b")), data);
  }

  @Test
  public void testLimitQueriesUsingDescendingSortOrder() {
    CollectionReference collection =
        testCollectionWithDocs(
            map(
                "a", map("k", "a", "sort", 0),
                "b", map("k", "b", "sort", 1),
                "c", map("k", "c", "sort", 1),
                "d", map("k", "d", "sort", 2)));

    Query query = collection.limit(2).orderBy("sort", Direction.DESCENDING);
    FirebaseFirestore db = collection.firestore;
    Pipeline.Snapshot set = waitFor(db.pipeline().createFrom(query).execute());

    List<Map<String, Object>> data = pipelineSnapshotToValues(set);
    assertEquals(asList(map("k", "d", "sort", 2L), map("k", "c", "sort", 1L)), data);
  }

  @Test
  public void testLimitToLastMustAlsoHaveExplicitOrderBy() {
    CollectionReference collection = testCollectionWithDocs(map());
    FirebaseFirestore db = collection.firestore;

    Query query = collection.limitToLast(2);
    expectError(
        () -> waitFor(db.pipeline().createFrom(query).execute()),
        "limitToLast() queries require specifying at least one orderBy() clause");
  }

  @Test
  public void testLimitToLastQueriesWithCursors() {
    CollectionReference collection =
        testCollectionWithDocs(
            map(
                "a", map("k", "a", "sort", 0),
                "b", map("k", "b", "sort", 1),
                "c", map("k", "c", "sort", 1),
                "d", map("k", "d", "sort", 2)));

    Query query = collection.limitToLast(3).orderBy("sort").endBefore(2);
    FirebaseFirestore db = collection.firestore;

    Pipeline.Snapshot set = waitFor(db.pipeline().createFrom(query).execute());
    List<Map<String, Object>> data = pipelineSnapshotToValues(set);
    assertEquals(
        asList(map("k", "a", "sort", 0L), map("k", "b", "sort", 1L), map("k", "c", "sort", 1L)),
        data);

    query = collection.limitToLast(3).orderBy("sort").endAt(1);
    set = waitFor(db.pipeline().createFrom(query).execute());
    data = pipelineSnapshotToValues(set);
    assertEquals(
        asList(map("k", "a", "sort", 0L), map("k", "b", "sort", 1L), map("k", "c", "sort", 1L)),
        data);

    query = collection.limitToLast(3).orderBy("sort").startAt(2);
    set = waitFor(db.pipeline().createFrom(query).execute());
    data = pipelineSnapshotToValues(set);
    assertEquals(asList(map("k", "d", "sort", 2L)), data);

    query = collection.limitToLast(3).orderBy("sort").startAfter(0);
    set = waitFor(db.pipeline().createFrom(query).execute());
    data = pipelineSnapshotToValues(set);
    assertEquals(
        asList(map("k", "b", "sort", 1L), map("k", "c", "sort", 1L), map("k", "d", "sort", 2L)),
        data);

    query = collection.limitToLast(3).orderBy("sort").startAfter(-1);
    set = waitFor(db.pipeline().createFrom(query).execute());
    data = pipelineSnapshotToValues(set);
    assertEquals(
        asList(map("k", "b", "sort", 1L), map("k", "c", "sort", 1L), map("k", "d", "sort", 2L)),
        data);
  }

  @Test
  public void testNotInRemovesExistenceFilter() {
    CollectionReference collection =
        testCollectionWithDocs(
            map(
                "doc1", map("field", 2),
                "doc2", map("field", 1),
                "doc3", map()));

    Query query = collection.whereNotIn("field", asList(1));
    FirebaseFirestore db = collection.firestore;
    Pipeline.Snapshot set = waitFor(db.pipeline().createFrom(query).execute());
    List<String> ids = pipelineSnapshotToIds(set);
    assertEquals(asList("doc3", "doc1"), ids);
  }

  @Test
  public void testNotEqualRemovesExistenceFilter() {
    CollectionReference collection =
        testCollectionWithDocs(
            map(
                "doc1", map("field", 2),
                "doc2", map("field", 1),
                "doc3", map()));

    Query query = collection.whereNotEqualTo("field", 1);
    FirebaseFirestore db = collection.firestore;
    Pipeline.Snapshot set = waitFor(db.pipeline().createFrom(query).execute());
    List<String> ids = pipelineSnapshotToIds(set);
    assertEquals(asList("doc3", "doc1"), ids);
  }

  @Test
  public void testInequalityMaintainsExistenceFilter() {
    CollectionReference collection =
        testCollectionWithDocs(
            map(
                "doc1", map("field", 0),
                "doc2", map()));

    Query query = collection.whereLessThan("field", 1);
    FirebaseFirestore db = collection.firestore;
    Pipeline.Snapshot set = waitFor(db.pipeline().createFrom(query).execute());
    List<String> ids = pipelineSnapshotToIds(set);
    assertEquals(asList("doc1"), ids);
  }

  @Test
  public void testExplicitOrderMaintainsExistenceFilter() {
    CollectionReference collection =
        testCollectionWithDocs(
            map(
                "doc1", map("field", 1),
                "doc2", map()));

    Query query = collection.orderBy("field");
    FirebaseFirestore db = collection.firestore;
    Pipeline.Snapshot set = waitFor(db.pipeline().createFrom(query).execute());
    List<String> ids = pipelineSnapshotToIds(set);
    assertEquals(asList("doc1"), ids);
  }

  @Test
  public void testKeyOrderIsDescendingForDescendingInequality() {
    CollectionReference collection =
        testCollectionWithDocs(
            map(
                "a", map("foo", 42),
                "b", map("foo", 42.0),
                "c", map("foo", 42),
                "d", map("foo", 21),
                "e", map("foo", 21.0),
                "f", map("foo", 66),
                "g", map("foo", 66.0)));

    Query query = collection.whereGreaterThan("foo", 21.0).orderBy("foo", Direction.DESCENDING);
    FirebaseFirestore db = collection.firestore;
    Pipeline.Snapshot result = waitFor(db.pipeline().createFrom(query).execute());
    assertEquals(asList("g", "f", "c", "b", "a"), pipelineSnapshotToIds(result));
  }

  @Test
  public void testUnaryFilterQueries() {
    CollectionReference collection =
        testCollectionWithDocs(
            map(
                "a", map("null", null, "nan", Double.NaN),
                "b", map("null", null, "nan", 0),
                "c", map("null", false, "nan", Double.NaN)));
    FirebaseFirestore db = collection.firestore;
    Pipeline.Snapshot results =
        waitFor(
            db.pipeline()
                .createFrom(collection.whereEqualTo("null", null).whereEqualTo("nan", Double.NaN))
                .execute());
    assertEquals(1, results.getResults().size());
    PipelineResult result = results.getResults().get(0);
    // Can't use assertEquals() since NaN != NaN.
    assertEquals(null, result.get("null"));
    assertTrue(((Double) result.get("nan")).isNaN());
  }

  @Test
  public void testFilterOnInfinity() {
    CollectionReference collection =
        testCollectionWithDocs(
            map(
                "a", map("inf", Double.POSITIVE_INFINITY),
                "b", map("inf", Double.NEGATIVE_INFINITY)));
    FirebaseFirestore db = collection.firestore;
    Pipeline.Snapshot results =
        waitFor(
            db.pipeline()
                .createFrom(collection.whereEqualTo("inf", Double.POSITIVE_INFINITY))
                .execute());
    assertEquals(1, results.getResults().size());
    assertEquals(asList(map("inf", Double.POSITIVE_INFINITY)), pipelineSnapshotToValues(results));
  }

  @Test
  public void testCanExplicitlySortByDocumentId() {
    Map<String, Map<String, Object>> testDocs =
        map(
            "a", map("key", "a"),
            "b", map("key", "b"),
            "c", map("key", "c"));
    CollectionReference collection = testCollectionWithDocs(testDocs);
    FirebaseFirestore db = collection.firestore;
    // Ideally this would be descending to validate it's different than
    // the default, but that requires an extra index
    Pipeline.Snapshot docs =
        waitFor(db.pipeline().createFrom(collection.orderBy(FieldPath.documentId())).execute());
    assertEquals(
        asList(testDocs.get("a"), testDocs.get("b"), testDocs.get("c")),
        pipelineSnapshotToValues(docs));
  }

  @Test
  public void testCanQueryByDocumentId() {
    Map<String, Map<String, Object>> testDocs =
        map(
            "aa", map("key", "aa"),
            "ab", map("key", "ab"),
            "ba", map("key", "ba"),
            "bb", map("key", "bb"));
    CollectionReference collection = testCollectionWithDocs(testDocs);
    FirebaseFirestore db = collection.firestore;
    Pipeline.Snapshot docs =
        waitFor(
            db.pipeline()
                .createFrom(collection.whereEqualTo(FieldPath.documentId(), "ab"))
                .execute());
    assertEquals(singletonList(testDocs.get("ab")), pipelineSnapshotToValues(docs));

    docs =
        waitFor(
            db.pipeline()
                .createFrom(
                    collection
                        .whereGreaterThan(FieldPath.documentId(), "aa")
                        .whereLessThanOrEqualTo(FieldPath.documentId(), "ba"))
                .execute());
    assertEquals(asList(testDocs.get("ab"), testDocs.get("ba")), pipelineSnapshotToValues(docs));
  }

  @Test
  public void testCanQueryByDocumentIdUsingRefs() {
    Map<String, Map<String, Object>> testDocs =
        map(
            "aa", map("key", "aa"),
            "ab", map("key", "ab"),
            "ba", map("key", "ba"),
            "bb", map("key", "bb"));
    CollectionReference collection = testCollectionWithDocs(testDocs);
    FirebaseFirestore db = collection.firestore;
    Pipeline.Snapshot docs =
        waitFor(
            db.pipeline()
                .createFrom(
                    collection.whereEqualTo(FieldPath.documentId(), collection.document("ab")))
                .execute());
    assertEquals(singletonList(testDocs.get("ab")), pipelineSnapshotToValues(docs));

    docs =
        waitFor(
            db.pipeline()
                .createFrom(
                    collection
                        .whereGreaterThan(FieldPath.documentId(), collection.document("aa"))
                        .whereLessThanOrEqualTo(FieldPath.documentId(), collection.document("ba")))
                .execute());
    assertEquals(asList(testDocs.get("ab"), testDocs.get("ba")), pipelineSnapshotToValues(docs));
  }

  @Test
  public void testCanQueryWithAndWithoutDocumentKey() {
    CollectionReference collection = testCollection();
    FirebaseFirestore db = collection.firestore;
    collection.add(map());
    Task<Pipeline.Snapshot> query1 =
        db.pipeline()
            .createFrom(collection.orderBy(FieldPath.documentId(), Direction.ASCENDING))
            .execute();
    Task<Pipeline.Snapshot> query2 = db.pipeline().createFrom(collection).execute();

    waitFor(query1);
    waitFor(query2);

    assertEquals(
        pipelineSnapshotToValues(query1.getResult()), pipelineSnapshotToValues(query2.getResult()));
  }

  @Test
  public void testQueriesCanUseNotEqualFilters() {
    // These documents are ordered by value in "zip" since the notEquals filter is an inequality,
    // which results in documents being sorted by value.
    Map<String, Object> docA = map("zip", Double.NaN);
    Map<String, Object> docB = map("zip", 91102L);
    Map<String, Object> docC = map("zip", 98101L);
    Map<String, Object> docD = map("zip", "98101");
    Map<String, Object> docE = map("zip", asList(98101L));
    Map<String, Object> docF = map("zip", asList(98101L, 98102L));
    Map<String, Object> docG = map("zip", asList("98101", map("zip", 98101L)));
    Map<String, Object> docH = map("zip", map("code", 500L));
    Map<String, Object> docI = map("code", 500L);
    Map<String, Object> docJ = map("zip", null);

    Map<String, Map<String, Object>> allDocs =
        map(
            "i", docI, "j", docJ, "a", docA, "b", docB, "c", docC, "d", docD, "e", docE, "f", docF,
            "g", docG, "h", docH);
    CollectionReference collection = testCollectionWithDocs(allDocs);
    FirebaseFirestore db = collection.firestore;

    // Search for zips not matching 98101.
    Map<String, Map<String, Object>> expectedDocsMap = new LinkedHashMap<>(allDocs);
    expectedDocsMap.remove("c");

    Pipeline.Snapshot snapshot =
        waitFor(db.pipeline().createFrom(collection.whereNotEqualTo("zip", 98101L)).execute());
    assertEquals(Lists.newArrayList(expectedDocsMap.values()), pipelineSnapshotToValues(snapshot));

    // With objects.
    expectedDocsMap = new LinkedHashMap<>(allDocs);
    expectedDocsMap.remove("h");
    snapshot =
        waitFor(
            db.pipeline()
                .createFrom(collection.whereNotEqualTo("zip", map("code", 500)))
                .execute());
    assertEquals(Lists.newArrayList(expectedDocsMap.values()), pipelineSnapshotToValues(snapshot));

    // With Null.
    expectedDocsMap = new LinkedHashMap<>(allDocs);
    expectedDocsMap.remove("j");
    snapshot = waitFor(db.pipeline().createFrom(collection.whereNotEqualTo("zip", null)).execute());
    assertEquals(Lists.newArrayList(expectedDocsMap.values()), pipelineSnapshotToValues(snapshot));

    // With NaN.
    expectedDocsMap = new LinkedHashMap<>(allDocs);
    expectedDocsMap.remove("a");
    snapshot =
        waitFor(db.pipeline().createFrom(collection.whereNotEqualTo("zip", Double.NaN)).execute());
    assertEquals(Lists.newArrayList(expectedDocsMap.values()), pipelineSnapshotToValues(snapshot));
  }

  @Test
  public void testQueriesCanUseNotEqualFiltersWithDocIds() {
    Map<String, String> docA = map("key", "aa");
    Map<String, String> docB = map("key", "ab");
    Map<String, String> docC = map("key", "ba");
    Map<String, String> docD = map("key", "bb");
    Map<String, Map<String, Object>> testDocs =
        map(
            "aa", docA,
            "ab", docB,
            "ba", docC,
            "bb", docD);
    CollectionReference collection = testCollectionWithDocs(testDocs);
    FirebaseFirestore db = collection.firestore;
    Pipeline.Snapshot docs =
        waitFor(
            db.pipeline()
                .createFrom(collection.whereNotEqualTo(FieldPath.documentId(), "aa"))
                .execute());
    assertEquals(asList(docB, docC, docD), pipelineSnapshotToValues(docs));
  }

  @Test
  public void testQueriesCanUseArrayContainsFilters() {
    Map<String, Object> docA = map("array", asList(42L));
    Map<String, Object> docB = map("array", asList("a", 42L, "c"));
    Map<String, Object> docC = map("array", asList(41.999, "42", map("a", asList(42))));
    Map<String, Object> docD = map("array", asList(42L), "array2", asList("bingo"));
    Map<String, Object> docE = map("array", nullList());
    Map<String, Object> docF = map("array", asList(Double.NaN));
    CollectionReference collection =
        testCollectionWithDocs(
            map("a", docA, "b", docB, "c", docC, "d", docD, "e", docE, "f", docF));
    FirebaseFirestore db = collection.firestore;

    // Search for "array" to contain 42
    Pipeline.Snapshot snapshot =
        waitFor(db.pipeline().createFrom(collection.whereArrayContains("array", 42L)).execute());
    assertEquals(asList(docA, docB, docD), pipelineSnapshotToValues(snapshot));

    snapshot =
        waitFor(
            db.pipeline().createFrom(collection.whereArrayContains("array", Double.NaN)).execute());
    assertEquals(asList(docF), pipelineSnapshotToValues(snapshot));
  }

  @Test
  public void testQueriesCanUseInFilters() {
    Map<String, Object> docA = map("zip", 98101L);
    Map<String, Object> docB = map("zip", 91102L);
    Map<String, Object> docC = map("zip", 98103L);
    Map<String, Object> docD = map("zip", asList(98101L));
    Map<String, Object> docE = map("zip", asList("98101", map("zip", 98101L)));
    Map<String, Object> docF = map("zip", map("code", 500L));
    Map<String, Object> docG = map("zip", asList(98101L, 98102L));
    Map<String, Object> docH = map("zip", null);
    Map<String, Object> docI = map("zip", Double.NaN);

    CollectionReference collection =
        testCollectionWithDocs(
            map(
                "a", docA, "b", docB, "c", docC, "d", docD, "e", docE, "f", docF, "g", docG, "h",
                docH, "i", docI));
    FirebaseFirestore db = collection.firestore;

    // Search for zips matching 98101, 98103, or [98101, 98102].
    Pipeline.Snapshot snapshot =
        waitFor(
            db.pipeline()
                .createFrom(
                    collection.whereIn("zip", asList(98101L, 98103L, asList(98101L, 98102L))))
                .execute());
    assertEquals(asList(docA, docC, docG), pipelineSnapshotToValues(snapshot));

    // With objects.
    snapshot =
        waitFor(
            db.pipeline()
                .createFrom(collection.whereIn("zip", asList(map("code", 500L))))
                .execute());
    assertEquals(asList(docF), pipelineSnapshotToValues(snapshot));

    // With null.
    snapshot = waitFor(db.pipeline().createFrom(collection.whereIn("zip", nullList())).execute());
    assertEquals(asList(docH), pipelineSnapshotToValues(snapshot));

    // With null and a value.
    List<Object> inputList = nullList();
    inputList.add(98101L);
    snapshot = waitFor(db.pipeline().createFrom(collection.whereIn("zip", inputList)).execute());
    assertEquals(asList(docA, docH), pipelineSnapshotToValues(snapshot));

    // With NaN.
    snapshot =
        waitFor(db.pipeline().createFrom(collection.whereIn("zip", asList(Double.NaN))).execute());
    assertEquals(asList(docI), pipelineSnapshotToValues(snapshot));

    // With NaN and a value.
    snapshot =
        waitFor(
            db.pipeline()
                .createFrom(collection.whereIn("zip", asList(Double.NaN, 98101L)))
                .execute());
    assertEquals(asList(docA, docI), pipelineSnapshotToValues(snapshot));
  }

  @Test
  public void testQueriesCanUseInFiltersWithDocIds() {
    Map<String, String> docA = map("key", "aa");
    Map<String, String> docB = map("key", "ab");
    Map<String, String> docC = map("key", "ba");
    Map<String, String> docD = map("key", "bb");
    Map<String, Map<String, Object>> testDocs =
        map(
            "aa", docA,
            "ab", docB,
            "ba", docC,
            "bb", docD);
    CollectionReference collection = testCollectionWithDocs(testDocs);
    FirebaseFirestore db = collection.firestore;
    Pipeline.Snapshot docs =
        waitFor(
            db.pipeline()
                .createFrom(collection.whereIn(FieldPath.documentId(), asList("aa", "ab")))
                .execute());
    assertEquals(asList(docA, docB), pipelineSnapshotToValues(docs));
  }

  @Test
  public void testQueriesCanUseNotInFilters() {
    // These documents are ordered by value in "zip" since the notEquals filter is an inequality,
    // which results in documents being sorted by value.
    Map<String, Object> docA = map("zip", Double.NaN);
    Map<String, Object> docB = map("zip", 91102L);
    Map<String, Object> docC = map("zip", 98101L);
    Map<String, Object> docD = map("zip", 98103L);
    Map<String, Object> docE = map("zip", asList(98101L));
    Map<String, Object> docF = map("zip", asList(98101L, 98102L));
    Map<String, Object> docG = map("zip", asList("98101", map("zip", 98101L)));
    Map<String, Object> docH = map("zip", map("code", 500L));
    Map<String, Object> docI = map("code", 500L);
    Map<String, Object> docJ = map("zip", null);

    Map<String, Map<String, Object>> allDocs =
        map(
            "i", docI, "j", docJ, "a", docA, "b", docB, "c", docC, "d", docD, "e", docE, "f", docF,
            "g", docG, "h", docH);
    CollectionReference collection = testCollectionWithDocs(allDocs);
    FirebaseFirestore db = collection.firestore;

    // Search for zips not matching 98101, 98103, or [98101, 98102].
    Map<String, Map<String, Object>> expectedDocsMap = new LinkedHashMap<>(allDocs);
    expectedDocsMap.remove("c");
    expectedDocsMap.remove("d");
    expectedDocsMap.remove("f");

    Pipeline.Snapshot snapshot =
        waitFor(
            db.pipeline()
                .createFrom(
                    collection.whereNotIn("zip", asList(98101L, 98103L, asList(98101L, 98102L))))
                .execute());
    assertEquals(Lists.newArrayList(expectedDocsMap.values()), pipelineSnapshotToValues(snapshot));

    // With objects.
    expectedDocsMap = new LinkedHashMap<>(allDocs);
    expectedDocsMap.remove("h");
    snapshot =
        waitFor(
            db.pipeline()
                .createFrom(collection.whereNotIn("zip", asList(map("code", 500L))))
                .execute());
    assertEquals(Lists.newArrayList(expectedDocsMap.values()), pipelineSnapshotToValues(snapshot));

    // With Null.
    expectedDocsMap = new LinkedHashMap<>(allDocs);
    expectedDocsMap.remove("j");
    snapshot =
        waitFor(db.pipeline().createFrom(collection.whereNotIn("zip", nullList())).execute());
    assertEquals(Lists.newArrayList(expectedDocsMap.values()), pipelineSnapshotToValues(snapshot));

    // With NaN.
    expectedDocsMap = new LinkedHashMap<>(allDocs);
    expectedDocsMap.remove("a");
    snapshot =
        waitFor(
            db.pipeline().createFrom(collection.whereNotIn("zip", asList(Double.NaN))).execute());
    assertEquals(Lists.newArrayList(expectedDocsMap.values()), pipelineSnapshotToValues(snapshot));

    // With NaN and a number.
    expectedDocsMap = new LinkedHashMap<>(allDocs);
    expectedDocsMap.remove("a");
    expectedDocsMap.remove("c");
    snapshot =
        waitFor(
            db.pipeline()
                .createFrom(collection.whereNotIn("zip", asList(Float.NaN, 98101L)))
                .execute());
    assertEquals(Lists.newArrayList(expectedDocsMap.values()), pipelineSnapshotToValues(snapshot));
  }

  @Test
  public void testQueriesCanUseNotInFiltersWithDocIds() {
    Map<String, String> docA = map("key", "aa");
    Map<String, String> docB = map("key", "ab");
    Map<String, String> docC = map("key", "ba");
    Map<String, String> docD = map("key", "bb");
    Map<String, Map<String, Object>> testDocs =
        map(
            "aa", docA,
            "ab", docB,
            "ba", docC,
            "bb", docD);
    CollectionReference collection = testCollectionWithDocs(testDocs);
    FirebaseFirestore db = collection.firestore;
    Pipeline.Snapshot docs =
        waitFor(
            db.pipeline()
                .createFrom(collection.whereNotIn(FieldPath.documentId(), asList("aa", "ab")))
                .execute());
    assertEquals(asList(docC, docD), pipelineSnapshotToValues(docs));
  }

  @Test
  public void testQueriesCanUseArrayContainsAnyFilters() {
    Map<String, Object> docA = map("array", asList(42L));
    Map<String, Object> docB = map("array", asList("a", 42L, "c"));
    Map<String, Object> docC = map("array", asList(41.999, "42", map("a", asList(42))));
    Map<String, Object> docD = map("array", asList(42L), "array2", asList("bingo"));
    Map<String, Object> docE = map("array", asList(43L));
    Map<String, Object> docF = map("array", asList(map("a", 42L)));
    Map<String, Object> docH = map("array", nullList());
    Map<String, Object> docI = map("array", asList(Double.NaN));

    CollectionReference collection =
        testCollectionWithDocs(
            map(
                "a", docA, "b", docB, "c", docC, "d", docD, "e", docE, "f", docF, "h", docH, "i",
                docI));
    FirebaseFirestore db = collection.firestore;

    // Search for "array" to contain [42, 43].
    Pipeline pipeline =
        db.pipeline().createFrom(collection.whereArrayContainsAny("array", asList(42L, 43L)));
    Pipeline.Snapshot snapshot = waitFor(pipeline.execute());
    assertEquals(asList(docA, docB, docD, docE), pipelineSnapshotToValues(snapshot));

    // With objects.
    pipeline =
        db.pipeline().createFrom(collection.whereArrayContainsAny("array", asList(map("a", 42L))));
    snapshot = waitFor(pipeline.execute());
    assertEquals(asList(docF), pipelineSnapshotToValues(snapshot));

    // With null.
    pipeline = db.pipeline().createFrom(collection.whereArrayContainsAny("array", nullList()));
    snapshot = waitFor(pipeline.execute());
    assertEquals(asList(docH), pipelineSnapshotToValues(snapshot));

    // With null and a value.
    List<Object> inputList = nullList();
    inputList.add(43L);
    pipeline = db.pipeline().createFrom(collection.whereArrayContainsAny("array", inputList));
    snapshot = waitFor(pipeline.execute());
    assertEquals(asList(docE, docH), pipelineSnapshotToValues(snapshot));

    // With NaN.
    pipeline =
        db.pipeline().createFrom(collection.whereArrayContainsAny("array", asList(Double.NaN)));
    snapshot = waitFor(pipeline.execute());
    assertEquals(asList(docI), pipelineSnapshotToValues(snapshot));

    // With NaN and a value.
    pipeline =
        db.pipeline()
            .createFrom(collection.whereArrayContainsAny("array", asList(Double.NaN, 43L)));
    snapshot = waitFor(pipeline.execute());
    assertEquals(asList(docE, docI), pipelineSnapshotToValues(snapshot));
  }

  @Test
  public void testCollectionGroupQueries() {
    FirebaseFirestore db = testFirestore();
    // Use .document() to get a random collection group name to use but ensure it starts with 'b'
    // for predictable ordering.
    String collectionGroup = "b" + db.collection("foo").document().getId();

    String[] docPaths =
        new String[] {
          "abc/123/${collectionGroup}/cg-doc1",
          "abc/123/${collectionGroup}/cg-doc2",
          "${collectionGroup}/cg-doc3",
          "${collectionGroup}/cg-doc4",
          "def/456/${collectionGroup}/cg-doc5",
          "${collectionGroup}/virtual-doc/nested-coll/not-cg-doc",
          "x${collectionGroup}/not-cg-doc",
          "${collectionGroup}x/not-cg-doc",
          "abc/123/${collectionGroup}x/not-cg-doc",
          "abc/123/x${collectionGroup}/not-cg-doc",
          "abc/${collectionGroup}"
        };
    WriteBatch batch = db.batch();
    for (String path : docPaths) {
      batch.set(db.document(path.replace("${collectionGroup}", collectionGroup)), map("x", 1));
    }
    waitFor(batch.commit());

    Pipeline.Snapshot snapshot =
        waitFor(db.pipeline().createFrom(db.collectionGroup(collectionGroup)).execute());
    assertEquals(
        asList("cg-doc1", "cg-doc2", "cg-doc3", "cg-doc4", "cg-doc5"),
        pipelineSnapshotToIds(snapshot));
  }

  @Test
  public void testCollectionGroupQueriesWithStartAtEndAtWithArbitraryDocumentIds() {
    FirebaseFirestore db = testFirestore();
    // Use .document() to get a random collection group name to use but ensure it starts with 'b'
    // for predictable ordering.
    String collectionGroup = "b" + db.collection("foo").document().getId();

    String[] docPaths =
        new String[] {
          "a/a/${collectionGroup}/cg-doc1",
          "a/b/a/b/${collectionGroup}/cg-doc2",
          "a/b/${collectionGroup}/cg-doc3",
          "a/b/c/d/${collectionGroup}/cg-doc4",
          "a/c/${collectionGroup}/cg-doc5",
          "${collectionGroup}/cg-doc6",
          "a/b/nope/nope"
        };
    WriteBatch batch = db.batch();
    for (String path : docPaths) {
      batch.set(db.document(path.replace("${collectionGroup}", collectionGroup)), map("x", 1));
    }
    waitFor(batch.commit());

    Pipeline.Snapshot snapshot =
        waitFor(
            db.pipeline()
                .createFrom(
                    db.collectionGroup(collectionGroup)
                        .orderBy(FieldPath.documentId())
                        .startAt("a/b")
                        .endAt("a/b0"))
                .execute());
    assertEquals(asList("cg-doc2", "cg-doc3", "cg-doc4"), pipelineSnapshotToIds(snapshot));

    snapshot =
        waitFor(
            db.pipeline()
                .createFrom(
                    db.collectionGroup(collectionGroup)
                        .orderBy(FieldPath.documentId())
                        .startAfter("a/b")
                        .endBefore("a/b/" + collectionGroup + "/cg-doc3"))
                .execute());
    assertEquals(asList("cg-doc2"), pipelineSnapshotToIds(snapshot));
  }

  @Test
  public void testCollectionGroupQueriesWithWhereFiltersOnArbitraryDocumentIds() {
    FirebaseFirestore db = testFirestore();
    // Use .document() to get a random collection group name to use but ensure it starts with 'b'
    // for predictable ordering.
    String collectionGroup = "b" + db.collection("foo").document().getId();

    String[] docPaths =
        new String[] {
          "a/a/${collectionGroup}/cg-doc1",
          "a/b/a/b/${collectionGroup}/cg-doc2",
          "a/b/${collectionGroup}/cg-doc3",
          "a/b/c/d/${collectionGroup}/cg-doc4",
          "a/c/${collectionGroup}/cg-doc5",
          "${collectionGroup}/cg-doc6",
          "a/b/nope/nope"
        };
    WriteBatch batch = db.batch();
    for (String path : docPaths) {
      batch.set(db.document(path.replace("${collectionGroup}", collectionGroup)), map("x", 1));
    }
    waitFor(batch.commit());

    Pipeline.Snapshot snapshot =
        waitFor(
            db.pipeline()
                .createFrom(
                    db.collectionGroup(collectionGroup)
                        .whereGreaterThanOrEqualTo(FieldPath.documentId(), "a/b")
                        .whereLessThanOrEqualTo(FieldPath.documentId(), "a/b0"))
                .execute());
    assertEquals(asList("cg-doc2", "cg-doc3", "cg-doc4"), pipelineSnapshotToIds(snapshot));

    snapshot =
        waitFor(
            db.pipeline()
                .createFrom(
                    db.collectionGroup(collectionGroup)
                        .whereGreaterThan(FieldPath.documentId(), "a/b")
                        .whereLessThan(
                            FieldPath.documentId(), "a/b/" + collectionGroup + "/cg-doc3"))
                .execute());
    assertEquals(asList("cg-doc2"), pipelineSnapshotToIds(snapshot));
  }

  @Test
  public void testOrQueries() {
    Map<String, Map<String, Object>> testDocs =
        map(
            "doc1", map("a", 1, "b", 0),
            "doc2", map("a", 2, "b", 1),
            "doc3", map("a", 3, "b", 2),
            "doc4", map("a", 1, "b", 3),
            "doc5", map("a", 1, "b", 1));
    CollectionReference collection = testCollectionWithDocs(testDocs);

    // Two equalities: a==1 || b==1.
    checkQueryAndPipelineResultsMatch(
        collection.where(or(equalTo("a", 1), equalTo("b", 1))), "doc1", "doc2", "doc4", "doc5");

    // (a==1 && b==0) || (a==3 && b==2)
    checkQueryAndPipelineResultsMatch(
        collection.where(
            or(and(equalTo("a", 1), equalTo("b", 0)), and(equalTo("a", 3), equalTo("b", 2)))),
        "doc1",
        "doc3");

    // a==1 && (b==0 || b==3).
    checkQueryAndPipelineResultsMatch(
        collection.where(and(equalTo("a", 1), or(equalTo("b", 0), equalTo("b", 3)))),
        "doc1",
        "doc4");

    // (a==2 || b==2) && (a==3 || b==3)
    checkQueryAndPipelineResultsMatch(
        collection.where(
            and(or(equalTo("a", 2), equalTo("b", 2)), or(equalTo("a", 3), equalTo("b", 3)))),
        "doc3");

    // Test with limits without orderBy (the __name__ ordering is the tie breaker).
    checkQueryAndPipelineResultsMatch(
        collection.where(or(equalTo("a", 2), equalTo("b", 1))).limit(1), "doc2");
  }

  @Test
  public void testOrQueriesWithIn() {
    Map<String, Map<String, Object>> testDocs =
        map(
            "doc1", map("a", 1, "b", 0),
            "doc2", map("b", 1),
            "doc3", map("a", 3, "b", 2),
            "doc4", map("a", 1, "b", 3),
            "doc5", map("a", 1),
            "doc6", map("a", 2));
    CollectionReference collection = testCollectionWithDocs(testDocs);

    // a==2 || b in [2,3]
    checkQueryAndPipelineResultsMatch(
        collection.where(or(equalTo("a", 2), inArray("b", asList(2, 3)))), "doc3", "doc4", "doc6");
  }

  @Test
  public void testOrQueriesWithArrayMembership() {
    Map<String, Map<String, Object>> testDocs =
        map(
            "doc1", map("a", 1, "b", asList(0)),
            "doc2", map("b", asList(1)),
            "doc3", map("a", 3, "b", asList(2, 7)),
            "doc4", map("a", 1, "b", asList(3, 7)),
            "doc5", map("a", 1),
            "doc6", map("a", 2));
    CollectionReference collection = testCollectionWithDocs(testDocs);

    // a==2 || b array-contains 7
    checkQueryAndPipelineResultsMatch(
        collection.where(or(equalTo("a", 2), arrayContains("b", 7))), "doc3", "doc4", "doc6");

    // a==2 || b array-contains-any [0, 3]
    checkQueryAndPipelineResultsMatch(
        collection.where(or(equalTo("a", 2), arrayContainsAny("b", asList(0, 3)))),
        "doc1",
        "doc4",
        "doc6");
  }

  @Test
  public void testMultipleInOps() {
    Map<String, Map<String, Object>> testDocs =
        map(
            "doc1", map("a", 1, "b", 0),
            "doc2", map("b", 1),
            "doc3", map("a", 3, "b", 2),
            "doc4", map("a", 1, "b", 3),
            "doc5", map("a", 1),
            "doc6", map("a", 2));
    CollectionReference collection = testCollectionWithDocs(testDocs);

    // Two IN operations on different fields with disjunction.
    Query query1 = collection.where(or(inArray("a", asList(2, 3)), inArray("b", asList(0, 2))));
    checkQueryAndPipelineResultsMatch(query1, "doc1", "doc3", "doc6");

    // Two IN operations on the same field with disjunction.
    // a IN [0,3] || a IN [0,2] should union them (similar to: a IN [0,2,3]).
    Query query2 = collection.where(or(inArray("a", asList(0, 3)), inArray("a", asList(0, 2))));
    checkQueryAndPipelineResultsMatch(query2, "doc3", "doc6");
  }

  @Test
  public void testUsingInWithArrayContainsAny() {
    Map<String, Map<String, Object>> testDocs =
        map(
            "doc1", map("a", 1, "b", asList(0)),
            "doc2", map("b", asList(1)),
            "doc3", map("a", 3, "b", asList(2, 7), "c", 10),
            "doc4", map("a", 1, "b", asList(3, 7)),
            "doc5", map("a", 1),
            "doc6", map("a", 2, "c", 20));
    CollectionReference collection = testCollectionWithDocs(testDocs);

    Query query1 =
        collection.where(or(inArray("a", asList(2, 3)), arrayContainsAny("b", asList(0, 7))));
    checkQueryAndPipelineResultsMatch(query1, "doc1", "doc3", "doc4", "doc6");

    Query query2 =
        collection.where(
            or(
                and(inArray("a", asList(2, 3)), equalTo("c", 10)),
                arrayContainsAny("b", asList(0, 7))));
    checkQueryAndPipelineResultsMatch(query2, "doc1", "doc3", "doc4");
  }

  @Test
  public void testUsingInWithArrayContains() {
    Map<String, Map<String, Object>> testDocs =
        map(
            "doc1", map("a", 1, "b", asList(0)),
            "doc2", map("b", asList(1)),
            "doc3", map("a", 3, "b", asList(2, 7)),
            "doc4", map("a", 1, "b", asList(3, 7)),
            "doc5", map("a", 1),
            "doc6", map("a", 2));
    CollectionReference collection = testCollectionWithDocs(testDocs);

    Query query1 = collection.where(or(inArray("a", asList(2, 3)), arrayContains("b", 3)));
    checkQueryAndPipelineResultsMatch(query1, "doc3", "doc4", "doc6");

    Query query2 = collection.where(and(inArray("a", asList(2, 3)), arrayContains("b", 7)));
    checkQueryAndPipelineResultsMatch(query2, "doc3");

    Query query3 =
        collection.where(
            or(inArray("a", asList(2, 3)), and(arrayContains("b", 3), equalTo("a", 1))));
    checkQueryAndPipelineResultsMatch(query3, "doc3", "doc4", "doc6");

    Query query4 =
        collection.where(
            and(inArray("a", asList(2, 3)), or(arrayContains("b", 7), equalTo("a", 1))));
    checkQueryAndPipelineResultsMatch(query4, "doc3");
  }

  @Test
  public void testOrderByEquality() {
    Map<String, Map<String, Object>> testDocs =
        map(
            "doc1", map("a", 1, "b", asList(0)),
            "doc2", map("b", asList(1)),
            "doc3", map("a", 3, "b", asList(2, 7), "c", 10),
            "doc4", map("a", 1, "b", asList(3, 7)),
            "doc5", map("a", 1),
            "doc6", map("a", 2, "c", 20));
    CollectionReference collection = testCollectionWithDocs(testDocs);

    Query query1 = collection.where(equalTo("a", 1)).orderBy("a");
    checkQueryAndPipelineResultsMatch(query1, "doc1", "doc4", "doc5");

    Query query2 = collection.where(inArray("a", asList(2, 3))).orderBy("a");
    checkQueryAndPipelineResultsMatch(query2, "doc6", "doc3");
  }
}
