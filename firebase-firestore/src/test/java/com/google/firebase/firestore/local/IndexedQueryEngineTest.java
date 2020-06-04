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
import static com.google.firebase.firestore.local.IndexedQueryEngine.extractBestIndexRange;
import static com.google.firebase.firestore.testutil.TestUtil.deletedDoc;
import static com.google.firebase.firestore.testutil.TestUtil.doc;
import static com.google.firebase.firestore.testutil.TestUtil.field;
import static com.google.firebase.firestore.testutil.TestUtil.filter;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.testutil.TestUtil.query;

import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.firestore.Blob;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.auth.User;
import com.google.firebase.firestore.core.Filter;
import com.google.firebase.firestore.core.IndexRange;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.testutil.TestUtil;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class IndexedQueryEngineTest {
  private IndexedQueryEngine queryEngine;
  private RemoteDocumentCache remoteDocuments;

  // Version numbers used for document updates.
  private static final int ORIGINAL_VERSION = 0;
  private static final int UPDATED_VERSION = 1;

  // Documents used in the verify the index lookups.
  private static final Document NON_MATCHING_DOC = doc("coll/a", ORIGINAL_VERSION, map("a", "b"));
  private static final Document MATCHING_DOC = doc("coll/a", UPDATED_VERSION, map("a", "a"));
  private static final Document IGNORED_DOC = doc("coll/b", ORIGINAL_VERSION, map("b", "b"));

  @Before
  public void setUp() {
    Persistence.INDEXING_SUPPORT_ENABLED = true;

    SQLitePersistence persistence = PersistenceTestHelpers.createSQLitePersistence();
    SQLiteCollectionIndex index = new SQLiteCollectionIndex(persistence, User.UNAUTHENTICATED);
    remoteDocuments = persistence.getRemoteDocumentCache();
    queryEngine = new IndexedQueryEngine(index);
  }

  private void addDocument(Document newDoc) {
    // Use document version as read time as the IndexedQueryEngine does not rely on read time.
    remoteDocuments.add(newDoc, newDoc.getVersion());
    queryEngine.handleDocumentChange(
        deletedDoc(newDoc.getKey().toString(), ORIGINAL_VERSION), newDoc);
  }

  private void removeDocument(Document oldDoc) {
    remoteDocuments.remove(oldDoc.getKey());
    queryEngine.handleDocumentChange(
        oldDoc, deletedDoc(oldDoc.getKey().toString(), UPDATED_VERSION));
  }

  private void updateDocument(Document oldDoc, Document newDoc) {
    remoteDocuments.add(newDoc, newDoc.getVersion());
    queryEngine.handleDocumentChange(oldDoc, newDoc);
  }

  @Test
  public void valueSelectivity() {
    List<Filter> highSelectivity =
        Arrays.asList(
            filter("high", "==", 0.1),
            filter("high", "==", 1),
            filter("high", "==", new Date()),
            filter("high", "==", "foo"),
            filter("high", "==", null),
            filter("high", "==", Double.NaN),
            filter("high", "==", TestUtil.ref("a/b")),
            filter("high", "==", new GeoPoint(0.0, 0.0)),
            filter("high", "==", Blob.fromBytes(new byte[] {})));

    List<Filter> lowSelectivity =
        Arrays.asList(
            filter("low", "==", true),
            filter("low", "==", false),
            filter("low", "==", Arrays.asList("foo", "bar")),
            filter("low", "==", map("a", "b")));

    for (Filter lowSelectivityFilter : lowSelectivity) {
      Query query = query("a").filter(lowSelectivityFilter);
      IndexRange indexRange = extractBestIndexRange(query);
      assertThat(field("low")).isEqualTo(indexRange.getFieldPath());

      for (Filter highSelectivityFilter : highSelectivity) {
        Query modifiedQuery = query.filter(highSelectivityFilter);
        IndexRange modifiedRange = extractBestIndexRange(modifiedQuery);
        assertThat(field("high")).isEqualTo(modifiedRange.getFieldPath());
      }
    }
  }

  @Test
  public void filterSelectivity() {
    List<Filter> highSelectivity = Collections.singletonList(filter("high", "==", 1));

    List<Filter> lowSelectivity =
        Arrays.asList(
            filter("low", ">", 1),
            filter("low", ">=", 1),
            filter("low", "<", 1),
            filter("low", "<=", 1));

    for (Filter lowSelectivityFilter : lowSelectivity) {
      Query query = query("a").filter(lowSelectivityFilter);
      IndexRange indexRange = extractBestIndexRange(query);
      assertThat(field("low")).isEqualTo(indexRange.getFieldPath());

      for (Filter highSelectivityFilter : highSelectivity) {
        Query modifiedQuery = query.filter(highSelectivityFilter);
        IndexRange modifiedRange = extractBestIndexRange(modifiedQuery);
        assertThat(field("high")).isEqualTo(modifiedRange.getFieldPath());
      }
    }
  }

  @Test
  public void noIndexRangeForEmptyQuery() {
    Query query = query("a");
    IndexRange indexRange = extractBestIndexRange(query);
    assertThat(indexRange).isNull();
  }

  @Test
  public void usesFirstOrderBy() {
    Query query = query("a").orderBy(TestUtil.orderBy("a")).orderBy(TestUtil.orderBy("b"));
    IndexRange indexRange = extractBestIndexRange(query);
    assertThat(field("a")).isEqualTo(indexRange.getFieldPath());
  }

  @Test
  public void preferFilterOverOrderBy() {
    Query queryWithoutFilter = query("a").orderBy(TestUtil.orderBy("a"));
    Query queryWithFilter =
        query("a")
            .orderBy(TestUtil.orderBy("a"))
            .orderBy(TestUtil.orderBy("b"))
            // We need to specify this filter since the first filter needs to match the orderBy.
            .filter(filter("a", ">=", true))
            .filter(filter("c", "==", true));

    IndexRange indexRangeWithoutFilter = extractBestIndexRange(queryWithoutFilter);
    IndexRange indexRangeWithFilter = extractBestIndexRange(queryWithFilter);

    assertThat(field("a")).isEqualTo(indexRangeWithoutFilter.getFieldPath());
    assertThat(field("c")).isEqualTo(indexRangeWithFilter.getFieldPath());
  }

  @Test
  public void preferEqualsOverInequality() {
    Query queryWithInequality = query("a").filter(filter("a", ">=", true));
    Query queryWithEquality =
        query("a").filter(filter("a", ">=", true)).filter(filter("b", "==", true));

    IndexRange indexRangeWithInequality = extractBestIndexRange(queryWithInequality);
    IndexRange indexRangeWithEquality = extractBestIndexRange(queryWithEquality);

    assertThat(field("a")).isEqualTo(indexRangeWithInequality.getFieldPath());
    assertThat(field("b")).isEqualTo(indexRangeWithEquality.getFieldPath());
  }

  @Test
  public void preferHighSelectivityTypes() {
    Query queryWithLowSelectivity = query("a").filter(filter("a", "==", true));
    Query queryWithHighSelectivity =
        query("a").filter(filter("a", "==", true)).filter(filter("b", "==", 1.0));

    IndexRange indexRangeWithLowSelectivity = extractBestIndexRange(queryWithLowSelectivity);
    IndexRange indexRangeWithHighSelectivity = extractBestIndexRange(queryWithHighSelectivity);

    assertThat(field("a")).isEqualTo(indexRangeWithLowSelectivity.getFieldPath());
    assertThat(field("b")).isEqualTo(indexRangeWithHighSelectivity.getFieldPath());
  }

  @Test
  @Ignore("Pending cr/164068667")
  public void addDocumentQuery() {
    addDocument(IGNORED_DOC);
    addDocument(MATCHING_DOC);
    Query query = query("coll").filter(filter("a", "==", "a"));

    ImmutableSortedMap<DocumentKey, Document> results =
        queryEngine.getDocumentsMatchingQuery(
            query, /* lastLimboFreeSnapshotVersion= */ null, DocumentKey.emptyKeySet());

    assertThat(results).doesNotContain(IGNORED_DOC.getKey());
    assertThat(results).contains(MATCHING_DOC.getKey());
  }

  @Test
  @Ignore("Pending cr/164068667")
  public void updateDocumentQuery() {
    addDocument(IGNORED_DOC);
    addDocument(NON_MATCHING_DOC);
    updateDocument(NON_MATCHING_DOC, MATCHING_DOC);
    Query query = query("coll").filter(filter("a", "==", "a"));

    ImmutableSortedMap<DocumentKey, Document> results =
        queryEngine.getDocumentsMatchingQuery(
            query, /* lastLimboFreeSnapshotVersion= */ null, DocumentKey.emptyKeySet());

    assertThat(results).doesNotContain(IGNORED_DOC.getKey());
    assertThat(results).contains(MATCHING_DOC.getKey());
  }

  @Test
  @Ignore("Pending cr/164068667")
  public void removeDocumentQuery() {
    addDocument(IGNORED_DOC);
    addDocument(MATCHING_DOC);
    removeDocument(MATCHING_DOC);
    Query query = query("coll").filter(filter("a", "==", "a"));

    ImmutableSortedMap<DocumentKey, Document> results =
        queryEngine.getDocumentsMatchingQuery(
            query, /* lastLimboFreeSnapshotVersion= */ null, DocumentKey.emptyKeySet());

    assertThat(results).doesNotContain(IGNORED_DOC.getKey());
    assertThat(results).doesNotContain(MATCHING_DOC.getKey());
  }

  @Test
  @Ignore("Pending cr/164068667")
  public void nestedQuery() {
    Document nonMatchingDoc = doc("coll/a", ORIGINAL_VERSION, map("a", map("a", "b")));
    Document matchingDoc = doc("coll/a", UPDATED_VERSION, map("a", map("a", "a")));
    Document ignoredDoc = doc("coll/b", ORIGINAL_VERSION, map("a", map("a", "b")));
    addDocument(nonMatchingDoc);
    addDocument(ignoredDoc);
    updateDocument(nonMatchingDoc, matchingDoc);
    Query query = query("coll").filter(filter("a.a", "==", "a"));

    ImmutableSortedMap<DocumentKey, Document> results =
        queryEngine.getDocumentsMatchingQuery(
            query, /* lastLimboFreeSnapshotVersion= */ null, DocumentKey.emptyKeySet());

    assertThat(results).doesNotContain(ignoredDoc.getKey());
    assertThat(results).contains(matchingDoc.getKey());
  }

  @Test
  @Ignore("Pending cr/164068667")
  public void orderByQuery() {
    addDocument(IGNORED_DOC);
    addDocument(MATCHING_DOC);
    Query query = query("coll").orderBy(TestUtil.orderBy("a"));

    ImmutableSortedMap<DocumentKey, Document> results =
        queryEngine.getDocumentsMatchingQuery(
            query, /* lastLimboFreeSnapshotVersion= */ null, DocumentKey.emptyKeySet());

    assertThat(results).doesNotContain(IGNORED_DOC.getKey());
    assertThat(results).contains(MATCHING_DOC.getKey());
  }
}
