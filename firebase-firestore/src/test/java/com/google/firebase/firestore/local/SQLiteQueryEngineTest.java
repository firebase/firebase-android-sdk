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

import static com.google.firebase.firestore.model.FieldIndex.*;
import static com.google.firebase.firestore.model.FieldIndex.Segment.*;
import static com.google.firebase.firestore.testutil.TestUtil.doc;
import static com.google.firebase.firestore.testutil.TestUtil.docMap;
import static com.google.firebase.firestore.testutil.TestUtil.docSet;
import static com.google.firebase.firestore.testutil.TestUtil.fieldIndex;
import static com.google.firebase.firestore.testutil.TestUtil.filter;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.testutil.TestUtil.query;
import static com.google.firebase.firestore.testutil.TestUtil.setMutation;
import static org.junit.Assert.assertEquals;

import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.DocumentSet;
import com.google.firebase.firestore.model.MutableDocument;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firestore.admin.v1.Index;
import org.junit.Ignore;
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
  @Ignore("b/226360573")
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
    assertEquals(docSet(query.comparator(), doc1), result);
  }
}
