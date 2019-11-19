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

import static com.google.firebase.firestore.testutil.TestUtil.ackTarget;
import static com.google.firebase.firestore.testutil.TestUtil.deletedDoc;
import static com.google.firebase.firestore.testutil.TestUtil.doc;
import static com.google.firebase.firestore.testutil.TestUtil.docUpdates;
import static com.google.firebase.firestore.testutil.TestUtil.key;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.testutil.TestUtil.orderBy;
import static com.google.firebase.firestore.testutil.TestUtil.path;
import static com.google.firebase.firestore.testutil.TestUtil.targetChange;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.firestore.DocumentChange.Type;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.core.View;
import com.google.firebase.firestore.core.ViewSnapshot;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.MaybeDocument;
import com.google.firebase.firestore.model.NoDocument;
import com.google.firebase.firestore.remote.TargetChange;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class DocumentChangeTest {

  private static void validatePositions(
      com.google.firebase.firestore.core.Query query,
      Collection<Document> initialDocsList,
      Collection<Document> addedList,
      Collection<Document> modifiedList,
      Collection<NoDocument> removedList) {
    ImmutableSortedMap<DocumentKey, MaybeDocument> initialDocs =
        docUpdates(initialDocsList.toArray(new MaybeDocument[] {}));

    ImmutableSortedMap<DocumentKey, MaybeDocument> updates =
        ImmutableSortedMap.Builder.emptyMap(DocumentKey.comparator());
    for (Document doc : addedList) {
      updates = updates.insert(doc.getKey(), doc);
    }
    for (Document doc : modifiedList) {
      updates = updates.insert(doc.getKey(), doc);
    }
    for (NoDocument doc : removedList) {
      updates = updates.insert(doc.getKey(), doc);
    }

    View view = new View(query, DocumentKey.emptyKeySet());
    View.DocumentChanges initialChanges = view.computeDocChanges(initialDocs);
    TargetChange initialTargetChange = ackTarget(initialDocsList.toArray(new Document[] {}));
    ViewSnapshot initialSnapshot =
        view.applyChanges(initialChanges, initialTargetChange).getSnapshot();

    View.DocumentChanges updateChanges = view.computeDocChanges(updates);
    TargetChange updateTargetChange =
        targetChange(ByteString.EMPTY, true, addedList, modifiedList, removedList);
    ViewSnapshot updatedSnapshot =
        view.applyChanges(updateChanges, updateTargetChange).getSnapshot();

    if (updatedSnapshot == null) {
      // Nothing changed, no positions to verify
      return;
    }

    List<Document> expected = new ArrayList<>(updatedSnapshot.getDocuments().toList());
    List<Document> actual = new ArrayList<>(initialSnapshot.getDocuments().toList());

    FirebaseFirestore firestore = mock(FirebaseFirestore.class);
    List<DocumentChange> changes =
        DocumentChange.changesFromSnapshot(firestore, MetadataChanges.EXCLUDE, updatedSnapshot);

    for (DocumentChange change : changes) {
      if (change.getType() != Type.ADDED) {
        actual.remove(change.getOldIndex());
      }

      if (change.getType() != Type.REMOVED) {
        actual.add(change.getNewIndex(), change.getDocument().getDocument());
      }
    }

    assertEquals(expected, actual);
  }

  @Test
  public void testAdditions() {
    Query query = Query.atPath(path("c"));
    List<Document> initialDocs =
        asList(doc("c/a", 1, map()), doc("c/c", 1, map()), doc("c/e", 1, map()));
    List<Document> adds = asList(doc("c/d", 2, map()), doc("c/b", 2, map()));
    validatePositions(query, initialDocs, adds, asList(), Collections.emptyList());
  }

  @Test
  public void testDeletions() {
    Query query = Query.atPath(path("c"));
    List<Document> initialDocs =
        asList(doc("c/a", 1, map()), doc("c/b", 1, map()), doc("c/c", 1, map()));
    List<NoDocument> deletes = asList(deletedDoc("c/a", 2), deletedDoc("c/c", 2));
    validatePositions(
        query, initialDocs, Collections.emptyList(), Collections.emptyList(), deletes);
  }

  @Test
  public void testModifications() {
    Query query = Query.atPath(path("c"));
    List<Document> initialDocs =
        asList(
            doc("c/a", 1, map("value", "a-1")),
            doc("c/b", 1, map("value", "b-1")),
            doc("c/c", 1, map("value", "c-1")));
    List<Document> updates =
        asList(doc("c/a", 2, map("value", "a-2")), doc("c/c", 2, map("value", "c-2")));
    validatePositions(
        query, initialDocs, Collections.emptyList(), updates, Collections.emptyList());
  }

  @Test
  public void testChangesWithSortOrderChange() {
    Query query = Query.atPath(path("c")).orderBy(orderBy("sort"));
    List<Document> initialDocs =
        asList(
            doc("c/a", 1, map("sort", 10)),
            doc("c/b", 1, map("sort", 20)),
            doc("c/c", 1, map("sort", 30)));
    List<Document> adds = asList(doc("c/new-a", 2, map("sort", 0)), doc("c/e", 2, map("sort", 25)));
    List<Document> updates =
        asList(
            doc("c/new-a", 2, map("sort", 0)),
            doc("c/b", 2, map("sort", 5)),
            doc("c/e", 2, map("sort", 25)),
            doc("c/a", 2, map("sort", 35)));
    List<NoDocument> deletes = asList(deletedDoc("c/c", 2));
    validatePositions(query, initialDocs, adds, updates, deletes);
  }

  @Test
  public void randomTests() {
    for (int run = 0; run < 100; run++) {
      Query query = Query.atPath(path("c")).orderBy(orderBy("sort"));
      Map<DocumentKey, Document> initialDocs = new HashMap<>();
      List<Document> adds = new ArrayList<>();
      List<Document> updates = new ArrayList<>();
      List<NoDocument> deletes = new ArrayList<>();
      int numDocs = 100;
      for (int i = 0; i < numDocs; i++) {
        String docKey = "c/test-doc-" + i;
        // Skip 20% of the docs
        if (Math.random() > 0.8) {
          initialDocs.put(key(docKey), doc(docKey, 1, map("sort", Math.random())));
        }
      }
      for (int i = 0; i < numDocs; i++) {
        String docKey = "c/test-doc-" + i;
        // Only update 20% of the docs
        if (Math.random() < 0.2) {
          // 30% deletes, rest updates and/or additions
          if (Math.random() < 0.3) {
            deletes.add(deletedDoc(docKey, 2));
          } else {
            if (initialDocs.containsKey(key(docKey))) {
              updates.add(doc(docKey, 2, map("sort", Math.random())));
            } else {
              adds.add(doc(docKey, 2, map("sort", Math.random())));
            }
          }
        }
      }

      validatePositions(query, initialDocs.values(), adds, updates, deletes);
    }
  }
}
