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

import static com.google.firebase.firestore.local.IndexManager.*;
import static com.google.firebase.firestore.model.FieldIndex.*;
import static com.google.firebase.firestore.model.FieldIndex.Segment.*;
import static com.google.firebase.firestore.testutil.TestUtil.doc;
import static com.google.firebase.firestore.testutil.TestUtil.docMap;
import static com.google.firebase.firestore.testutil.TestUtil.docSet;
import static com.google.firebase.firestore.testutil.TestUtil.fieldIndex;
import static com.google.firebase.firestore.testutil.TestUtil.filter;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.testutil.TestUtil.orderBy;
import static com.google.firebase.firestore.testutil.TestUtil.query;
import static com.google.firebase.firestore.testutil.TestUtil.setMutation;
import static org.junit.Assert.assertEquals;

import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.DocumentSet;
import com.google.firebase.firestore.model.MutableDocument;
import com.google.firebase.firestore.model.SnapshotVersion;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class SQLiteQueryEngineTest extends QueryEngineTestCase {

  @Override
  Persistence getPersistence() {
    return PersistenceTestHelpers.createSQLitePersistence();
  }

  @Test
  public void combinesIndexedWithNonIndexedResults() throws Exception {
    MutableDocument doc1 = doc("coll/a", 1, map("foo", true));
    MutableDocument doc2 = doc("coll/b", 2, map("foo", true));
    MutableDocument doc3 = doc("coll/c", 3, map("foo", true));
    MutableDocument doc4 = doc("coll/d", 3, map("foo", true)).setHasLocalMutations();

    indexManager.addFieldIndex(fieldIndex("coll", "foo", Kind.ASCENDING));

    addDocument(doc1);
    addDocument(doc2);
    indexManager.updateIndexEntries(docMap(doc1, doc2));
    indexManager.updateCollectionGroup("coll", IndexOffset.fromDocument(doc2));

    addDocument(doc3);
    addMutation(setMutation("coll/d", map("foo", true)));

    Query queryWithFilter = query("coll").filter(filter("foo", "==", true));
    DocumentSet results =
        expectOptimizedCollectionScan(() -> runQuery(queryWithFilter, SnapshotVersion.NONE));

    assertEquals(docSet(queryWithFilter.comparator(), doc1, doc2, doc3, doc4), results);
  }

  @Test
  public void usesPartialIndexForLimitQueries() throws Exception {
    MutableDocument doc1 = doc("coll/1", 1, map("a", 1, "b", 0));
    MutableDocument doc2 = doc("coll/2", 1, map("a", 1, "b", 1));
    MutableDocument doc3 = doc("coll/3", 1, map("a", 1, "b", 2));
    MutableDocument doc4 = doc("coll/4", 1, map("a", 1, "b", 3));
    MutableDocument doc5 = doc("coll/5", 1, map("a", 2, "b", 3));
    addDocument(doc1, doc2, doc3, doc4, doc5);

    indexManager.addFieldIndex(fieldIndex("coll", "a", Kind.ASCENDING));
    indexManager.updateIndexEntries(docMap(doc1, doc2, doc3, doc4, doc5));
    indexManager.updateCollectionGroup("coll", IndexOffset.fromDocument(doc5));

    Query query =
        query("coll").filter(filter("a", "==", 1)).filter(filter("b", "==", 1)).limitToFirst(3);
    DocumentSet result = expectOptimizedCollectionScan(() -> runQuery(query, SnapshotVersion.NONE));
    assertEquals(docSet(query.comparator(), doc2), result);
  }

  @Test
  public void testPartialIndexAndFullIndex() throws Exception {
    indexManager.addFieldIndex(fieldIndex("coll", "a", Kind.ASCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "b", Kind.ASCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "c", Kind.ASCENDING, "d", Kind.ASCENDING));

    Query query1 = query("coll").filter(filter("a", "==", 1));
    validateIsFullIndex(query1);

    Query query2 = query("coll").filter(filter("b", "==", 1));
    validateIsFullIndex(query2);

    Query query3 = query("coll").filter(filter("a", "==", 1)).orderBy(orderBy("a"));
    validateIsFullIndex(query3);

    Query query4 = query("coll").filter(filter("b", "==", 1)).orderBy(orderBy("b"));
    validateIsFullIndex(query4);

    Query query5 = query("coll").filter(filter("a", "==", 1)).filter(filter("b", "==", 1));
    validateIsPartialIndex(query5);

    Query query6 = query("coll").filter(filter("a", "==", 1)).orderBy(orderBy("b"));
    validateIsPartialIndex(query6);

    Query query7 = query("coll").filter(filter("b", "==", 1)).orderBy(orderBy("a"));
    validateIsPartialIndex(query7);

    Query query8 = query("coll").filter(filter("c", "==", 1)).filter(filter("d", "==", 1));
    validateIsFullIndex(query8);

    Query query9 =
        query("coll")
            .filter(filter("c", "==", 1))
            .filter(filter("d", "==", 1))
            .orderBy(orderBy("c"));
    validateIsFullIndex(query9);

    Query query10 =
        query("coll")
            .filter(filter("c", "==", 1))
            .filter(filter("d", "==", 1))
            .orderBy(orderBy("d"));
    validateIsFullIndex(query10);

    Query query11 =
        query("coll")
            .filter(filter("c", "==", 1))
            .filter(filter("d", "==", 1))
            .orderBy(orderBy("c"))
            .orderBy(orderBy("d"));
    validateIsFullIndex(query11);

    Query query12 =
        query("coll")
            .filter(filter("c", "==", 1))
            .filter(filter("d", "==", 1))
            .orderBy(orderBy("d"))
            .orderBy(orderBy("c"));
    validateIsFullIndex(query12);

    Query query13 =
        query("coll")
            .filter(filter("c", "==", 1))
            .filter(filter("d", "==", 1))
            .orderBy(orderBy("e"));
    validateIsPartialIndex(query13);

    Query query14 = query("coll").filter(filter("c", "==", 1)).filter(filter("d", "<=", 1));
    validateIsFullIndex(query14);

    Query query15 =
        query("coll")
            .filter(filter("c", "==", 1))
            .filter(filter("d", ">", 1))
            .orderBy(orderBy("d"));
    validateIsFullIndex(query15);
  }

  private void validateIsPartialIndex(Query query) {
    validateIndex(query, false);
  }

  private void validateIsFullIndex(Query query) {
    validateIndex(query, true);
  }

  private void validateIndex(Query query, boolean validateFullIndex) {
    IndexStatus indexStatus = indexManager.canServeUsingIndex(query.toTarget());
    assertEquals(indexStatus, validateFullIndex ? IndexStatus.FULL : IndexStatus.PARTIAL);
  }
}
