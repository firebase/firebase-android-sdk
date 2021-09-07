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

package com.google.firebase.firestore.local;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.firestore.testutil.TestUtil.bound;
import static com.google.firebase.firestore.testutil.TestUtil.doc;
import static com.google.firebase.firestore.testutil.TestUtil.field;
import static com.google.firebase.firestore.testutil.TestUtil.filter;
import static com.google.firebase.firestore.testutil.TestUtil.key;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.testutil.TestUtil.orderBy;
import static com.google.firebase.firestore.testutil.TestUtil.query;

import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldIndex;
import com.google.firebase.firestore.model.MutableDocument;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class SQLiteIndexManagerTest extends IndexManagerTestCase {
  /** Current state of indexing support. Used for restoring after test run. */
  private static final boolean supportsIndexing = Persistence.INDEXING_SUPPORT_ENABLED;

  @BeforeClass
  public static void beforeClass() {
    Persistence.INDEXING_SUPPORT_ENABLED = true;
  }

  @BeforeClass
  public static void afterClass() {
    Persistence.INDEXING_SUPPORT_ENABLED = supportsIndexing;
  }

  private void setUpSingleValueFilter() {
    indexManager.addFieldIndex(
        new FieldIndex("coll").withAddedField(field("count"), FieldIndex.Segment.Kind.ORDERED));
    addDoc("coll/doc1", map("count", 1));
    addDoc("coll/doc2", map("count", 2));
    addDoc("coll/doc3", map("count", 3));
  }

  private void setUpArrayValueFilter() {
    indexManager.addFieldIndex(
        new FieldIndex("coll").withAddedField(field("values"), FieldIndex.Segment.Kind.CONTAINS));
    addDoc("coll/doc1", map("values", Arrays.asList(1, 2, 3)));
    addDoc("coll/doc2", map("values", Arrays.asList(4, 5, 6)));
    addDoc("coll/doc3", map("values", Arrays.asList(7, 8, 9)));
  }

  @Override
  Persistence getPersistence() {
    return PersistenceTestHelpers.createSQLitePersistence();
  }

  @Test
  public void testEqualityFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").filter(filter("count", "==", 2));
    verifyResults(query, "coll/doc2");
  }

  @Test
  public void testNotEqualityFilter() {
    // TODO(indexing): Optimize != filters. We currently return all documents and do not exclude
    // the documents with the provided value.
    setUpSingleValueFilter();
    Query query = query("coll").filter(filter("count", "!=", 2));
    verifyResults(query, "coll/doc1", "coll/doc2", "coll/doc3");
  }

  @Test
  public void testLessThanFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").filter(filter("count", "<", 2));
    verifyResults(query, "coll/doc1");
  }

  @Test
  public void testLessThanOrEqualsFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").filter(filter("count", "<=", 2));
    verifyResults(query, "coll/doc1", "coll/doc2");
  }

  @Test
  public void testGreaterThanOrEqualsFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").filter(filter("count", ">=", 2));
    verifyResults(query, "coll/doc2", "coll/doc3");
  }

  @Test
  public void testGreaterThanFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").filter(filter("count", ">", 2));
    verifyResults(query, "coll/doc3");
  }

  @Test
  public void testRangeFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").filter(filter("count", ">", 1)).filter(filter("count", "<", 3));
    verifyResults(query, "coll/doc2");
  }

  @Test
  public void testStartAtFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").orderBy(orderBy("count")).startAt(bound(true, 2));
    verifyResults(query, "coll/doc2", "coll/doc3");
  }

  @Test
  public void testStartAfterFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").orderBy(orderBy("count")).startAt(bound(false, 2));
    verifyResults(query, "coll/doc3");
  }

  @Test
  public void testEndAtFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").orderBy(orderBy("count")).endAt(bound(true, 2));
    verifyResults(query, "coll/doc1", "coll/doc2");
  }

  @Test
  public void testEndBeforeFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").orderBy(orderBy("count")).endAt(bound(false, 2));
    verifyResults(query, "coll/doc1");
  }

  @Test
  public void testRangeWithBoundFilter() {
    setUpSingleValueFilter();
    Query startAt =
        query("coll")
            .filter(filter("count", ">=", 1))
            .filter(filter("count", "<=", 3))
            .orderBy(orderBy("count"))
            .startAt(bound(false, 1))
            .endAt(bound(true, 2));
    verifyResults(startAt, "coll/doc2");
  }

  @Test
  public void testInFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").filter(filter("count", "in", Arrays.asList(1, 2)));
    verifyResults(query, "coll/doc1", "coll/doc2");
  }

  @Test
  public void testNotInFilter() {
    // TODO(indexing): Optimize not-in filters. We currently return all documents and do not exclude
    // the documents with the provided values.
    setUpSingleValueFilter();
    Query query = query("coll").filter(filter("count", "not-in", Arrays.asList(1, 2)));
    verifyResults(query, "coll/doc1", "coll/doc2", "coll/doc3");
  }

  @Test
  public void testArrayContainsFilter() {
    setUpArrayValueFilter();
    Query query = query("coll").filter(filter("values", "array-contains", 1));
    verifyResults(query, "coll/doc1");
  }

  @Test
  public void testArrayContainsAnyFilter() {
    setUpArrayValueFilter();
    Query query =
        query("coll").filter(filter("values", "array-contains-any", Arrays.asList(1, 2, 4)));
    verifyResults(query, "coll/doc1", "coll/doc2");
  }

  private void addDoc(String key, Map<String, Object> data) {
    MutableDocument count1Doc = doc(key, 1, data);
    indexManager.addIndexEntries(count1Doc);
  }

  private void verifyResults(Query query, String... documents) {
    Iterable<DocumentKey> results = indexManager.getDocumentsMatchingTarget(query.toTarget());
    List<DocumentKey> keys = Arrays.stream(documents).map(s -> key(s)).collect(Collectors.toList());
    assertThat(results).containsExactlyElementsIn(keys);
  }
}
