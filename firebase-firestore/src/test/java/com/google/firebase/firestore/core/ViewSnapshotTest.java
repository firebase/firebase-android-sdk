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

import static com.google.firebase.firestore.testutil.TestUtil.doc;
import static com.google.firebase.firestore.testutil.TestUtil.key;
import static com.google.firebase.firestore.testutil.TestUtil.keySet;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static org.junit.Assert.assertEquals;

import com.google.firebase.database.collection.ImmutableSortedSet;
import com.google.firebase.firestore.core.DocumentViewChange.Type;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.DocumentSet;
import com.google.firebase.firestore.model.ResourcePath;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Tests ViewSnapshot */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class ViewSnapshotTest {
  @Test
  public void testConstructor() {
    Query query = Query.atPath(ResourcePath.fromString("a"));
    DocumentSet docs = DocumentSet.emptySet(Document.keyComparator()).add(doc("c/foo", 1, map()));
    DocumentSet oldDocs = DocumentSet.emptySet(Document.keyComparator());
    List<DocumentViewChange> changes =
        Arrays.asList(DocumentViewChange.create(Type.ADDED, doc("c/foo", 1, map())));
    ImmutableSortedSet<DocumentKey> mutatedKeys = keySet(key("c/foo"));
    boolean fromCache = true;
    boolean hasPendingWrites = true;
    boolean syncStateChanges = true;
    boolean excludesMetadataChanges = true;

    ViewSnapshot snapshot =
        new ViewSnapshot(
            query,
            docs,
            oldDocs,
            changes,
            fromCache,
            mutatedKeys,
            syncStateChanges,
            excludesMetadataChanges);

    assertEquals(query, snapshot.getQuery());
    assertEquals(docs, snapshot.getDocuments());
    assertEquals(oldDocs, snapshot.getOldDocuments());
    assertEquals(changes, snapshot.getChanges());
    assertEquals(fromCache, snapshot.isFromCache());
    assertEquals(mutatedKeys, snapshot.getMutatedKeys());
    assertEquals(hasPendingWrites, snapshot.hasPendingWrites());
    assertEquals(syncStateChanges, snapshot.didSyncStateChange());
    assertEquals(excludesMetadataChanges, snapshot.excludesMetadataChanges());
  }
}
