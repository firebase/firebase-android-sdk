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

import static com.google.firebase.firestore.testutil.TestUtil.ackTarget;
import static com.google.firebase.firestore.testutil.TestUtil.deletedDoc;
import static com.google.firebase.firestore.testutil.TestUtil.doc;
import static com.google.firebase.firestore.testutil.TestUtil.docUpdates;
import static com.google.firebase.firestore.testutil.TestUtil.filter;
import static com.google.firebase.firestore.testutil.TestUtil.keySet;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.testutil.TestUtil.orderBy;
import static com.google.firebase.firestore.testutil.TestUtil.targetChange;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.firestore.core.DocumentViewChange.Type;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.MaybeDocument;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.remote.TargetChange;
import com.google.protobuf.ByteString;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Tests View */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class ViewTest {

  private Query messageQuery() {
    return Query.atPath(ResourcePath.fromString("rooms/eros/messages"));
  }

  private static ViewChange applyChanges(View view, MaybeDocument... docs) {
    return view.applyChanges(view.computeDocChanges(docUpdates(docs)));
  }

  @Test
  public void testAddsDocumentsBasedOnQuery() {
    Query query = messageQuery();
    View view = new View(query, DocumentKey.emptyKeySet());

    Document doc1 = doc("rooms/eros/messages/1", 0, map("text", "msg1"));
    Document doc2 = doc("rooms/eros/messages/2", 0, map("text", "msg2"));
    Document doc3 = doc("rooms/other/messages/1", 0, map("text", "msg3"));

    ImmutableSortedMap<DocumentKey, Document> updates = docUpdates(doc1, doc2, doc3);
    View.DocumentChanges docViewChanges = view.computeDocChanges(updates);
    TargetChange targetChange = ackTarget(doc1, doc2, doc3);
    ViewSnapshot snapshot = view.applyChanges(docViewChanges, targetChange).getSnapshot();
    assertEquals(query, snapshot.getQuery());
    assertEquals(asList(doc1, doc2), snapshot.getDocuments().toList());
    assertEquals(
        asList(
            DocumentViewChange.create(Type.ADDED, doc1),
            DocumentViewChange.create(Type.ADDED, doc2)),
        snapshot.getChanges());
    assertFalse(snapshot.isFromCache());
    assertTrue(snapshot.didSyncStateChange());
    assertFalse(snapshot.hasPendingWrites());
  }

  @Test
  public void testRemovesDocument() {
    Query query = messageQuery();
    View view = new View(query, DocumentKey.emptyKeySet());

    Document doc1 = doc("rooms/eros/messages/1", 0, map("text", "msg1"));
    Document doc2 = doc("rooms/eros/messages/2", 0, map("text", "msg2"));
    Document doc3 = doc("rooms/eros/messages/3", 0, map("text", "msg3"));

    // initial state
    applyChanges(view, doc1, doc2);

    // delete doc2, add doc3
    ViewSnapshot snapshot =
        view.applyChanges(
                view.computeDocChanges(docUpdates(deletedDoc("rooms/eros/messages/2", 0), doc3)),
                ackTarget(doc1, doc3))
            .getSnapshot();

    assertEquals(query, snapshot.getQuery());
    assertEquals(asList(doc1, doc3), snapshot.getDocuments().toList());
    assertEquals(
        asList(
            DocumentViewChange.create(Type.REMOVED, doc2),
            DocumentViewChange.create(Type.ADDED, doc3)),
        snapshot.getChanges());
    assertFalse(snapshot.isFromCache());
    assertTrue(snapshot.didSyncStateChange());
  }

  @Test
  public void testReturnsNilIfNoChange() {
    Query query = messageQuery();
    View view = new View(query, DocumentKey.emptyKeySet());

    Document doc1 = doc("rooms/eros/messages/1", 0, map("text", "msg1"));
    Document doc2 = doc("rooms/eros/messages/2", 0, map("text", "msg2"));

    // initial state
    applyChanges(view, doc1, doc2);

    ViewSnapshot snapshot = applyChanges(view, doc1, doc2).getSnapshot();
    assertNull(snapshot);
  }

  @Test
  public void testReturnsNotNilForFirstChanges() {
    Query query = messageQuery();
    View view = new View(query, DocumentKey.emptyKeySet());

    // initial state
    assertNotNull(applyChanges(view).getSnapshot());
  }

  @Test
  public void testFiltersDocumentsBasedOnQueryWithFilters() {
    Query query = messageQuery().filter(filter("sort", "<=", 2));
    View view = new View(query, DocumentKey.emptyKeySet());

    Document doc1 = doc("rooms/eros/messages/1", 0, map("sort", 1));
    Document doc2 = doc("rooms/eros/messages/2", 0, map("sort", 2));
    Document doc3 = doc("rooms/eros/messages/3", 0, map("sort", 3));
    Document doc4 = doc("rooms/eros/messages/4", 0, map()); // no sort, no match
    Document doc5 = doc("rooms/eros/messages/5", 0, map("sort", 1));

    ViewSnapshot snapshot = applyChanges(view, doc1, doc2, doc3, doc4, doc5).getSnapshot();

    assertEquals(query, snapshot.getQuery());
    assertEquals(asList(doc1, doc5, doc2), snapshot.getDocuments().toList());
    assertEquals(
        asList(
            DocumentViewChange.create(Type.ADDED, doc1),
            DocumentViewChange.create(Type.ADDED, doc5),
            DocumentViewChange.create(Type.ADDED, doc2)),
        snapshot.getChanges());
    assertTrue(snapshot.isFromCache());
    assertTrue(snapshot.didSyncStateChange());
  }

  @Test
  public void testUpdatesDocumentsBasedOnQueryWithFilters() {
    Query query = messageQuery().filter(filter("sort", "<=", 2));
    View view = new View(query, DocumentKey.emptyKeySet());

    Document doc1 = doc("rooms/eros/messages/1", 0, map("sort", 1));
    Document doc2 = doc("rooms/eros/messages/2", 0, map("sort", 3));
    Document doc3 = doc("rooms/eros/messages/3", 0, map("sort", 2));
    Document doc4 = doc("rooms/eros/messages/4", 0, map());

    ViewSnapshot snapshot = applyChanges(view, doc1, doc2, doc3, doc4).getSnapshot();

    assertEquals(query, snapshot.getQuery());
    assertEquals(asList(doc1, doc3), snapshot.getDocuments().toList());

    Document newDoc2 = doc("rooms/eros/messages/2", 1, map("sort", 2));
    Document newDoc3 = doc("rooms/eros/messages/3", 1, map("sort", 3));
    Document newDoc4 = doc("rooms/eros/messages/4", 1, map("sort", 0));

    snapshot = applyChanges(view, newDoc2, newDoc3, newDoc4).getSnapshot();

    assertEquals(query, snapshot.getQuery());
    assertEquals(asList(newDoc4, doc1, newDoc2), snapshot.getDocuments().toList());

    assertEquals(
        asList(
            DocumentViewChange.create(Type.REMOVED, doc3),
            DocumentViewChange.create(Type.ADDED, newDoc4),
            DocumentViewChange.create(Type.ADDED, newDoc2)),
        snapshot.getChanges());
    assertTrue(snapshot.isFromCache());
    assertFalse(snapshot.didSyncStateChange());
  }

  @Test
  public void testRemovesDocumentsForQueryWithLimit() {
    Query query = messageQuery().limitToFirst(2);
    View view = new View(query, DocumentKey.emptyKeySet());

    Document doc1 = doc("rooms/eros/messages/1", 0, map("text", "msg1"));
    Document doc2 = doc("rooms/eros/messages/2", 0, map("text", "msg2"));
    Document doc3 = doc("rooms/eros/messages/3", 0, map("text", "msg3"));

    // initial state
    applyChanges(view, doc1, doc3);

    ViewSnapshot snapshot =
        view.applyChanges(view.computeDocChanges(docUpdates(doc2)), ackTarget(doc1, doc2, doc3))
            .getSnapshot();
    assertEquals(query, snapshot.getQuery());
    assertEquals(asList(doc1, doc2), snapshot.getDocuments().toList());

    assertEquals(
        asList(
            DocumentViewChange.create(Type.REMOVED, doc3),
            DocumentViewChange.create(Type.ADDED, doc2)),
        snapshot.getChanges());
    assertFalse(snapshot.isFromCache());
    assertTrue(snapshot.didSyncStateChange());
  }

  @Test
  public void testDoesNotReportChangesForDocumentBeyondLimit() {
    Query query = messageQuery().orderBy(orderBy("num")).limitToFirst(2);
    View view = new View(query, DocumentKey.emptyKeySet());

    Document doc1 = doc("rooms/eros/messages/1", 0, map("num", 1));
    Document doc2 = doc("rooms/eros/messages/2", 0, map("num", 2));
    Document doc3 = doc("rooms/eros/messages/3", 0, map("num", 3));
    Document doc4 = doc("rooms/eros/messages/4", 0, map("num", 4));

    applyChanges(view, doc1, doc2);

    // change doc2 to 5, and add doc3 and doc4.
    // doc2 will be modified + removed = removed
    // doc3 will be added
    // doc4 will be added + removed = nothing
    doc2 = doc("rooms/eros/messages/2", 1, map("num", 5));
    View.DocumentChanges viewDocChanges = view.computeDocChanges(docUpdates(doc2, doc3, doc4));
    assertTrue(viewDocChanges.needsRefill());
    // Verify that all the docs still match.
    viewDocChanges = view.computeDocChanges(docUpdates(doc1, doc2, doc3, doc4), viewDocChanges);
    ViewSnapshot snapshot =
        view.applyChanges(viewDocChanges, ackTarget(doc1, doc2, doc3, doc4)).getSnapshot();

    assertEquals(query, snapshot.getQuery());
    assertEquals(asList(doc1, doc3), snapshot.getDocuments().toList());

    assertEquals(
        asList(
            DocumentViewChange.create(Type.REMOVED, doc2),
            DocumentViewChange.create(Type.ADDED, doc3)),
        snapshot.getChanges());
    assertFalse(snapshot.isFromCache());
    assertTrue(snapshot.didSyncStateChange());
  }

  @Test
  public void testKeepsTrackOfLimboDocuments() {
    Query query = messageQuery();
    View view = new View(query, DocumentKey.emptyKeySet());
    Document doc1 = doc("rooms/eros/messages/0", 0, map());
    Document doc2 = doc("rooms/eros/messages/1", 0, map());
    Document doc3 = doc("rooms/eros/messages/2", 0, map());

    ViewChange change = applyChanges(view, doc1);
    assertTrue(change.getLimboChanges().isEmpty());

    View.DocumentChanges viewDocChanges = view.computeDocChanges(docUpdates());
    change = view.applyChanges(viewDocChanges, ackTarget());
    assertEquals(
        asList(new LimboDocumentChange(LimboDocumentChange.Type.ADDED, doc1.getKey())),
        change.getLimboChanges());

    viewDocChanges = view.computeDocChanges(docUpdates());
    change =
        view.applyChanges(
            viewDocChanges, targetChange(ByteString.EMPTY, true, asList(doc1), null, null));
    assertEquals(
        asList(new LimboDocumentChange(LimboDocumentChange.Type.REMOVED, doc1.getKey())),
        change.getLimboChanges());

    viewDocChanges = view.computeDocChanges(docUpdates(doc2));
    change =
        view.applyChanges(
            viewDocChanges, targetChange(ByteString.EMPTY, true, asList(doc2), null, null));
    assertTrue(change.getLimboChanges().isEmpty());

    change = applyChanges(view, doc3);
    assertEquals(
        asList(new LimboDocumentChange(LimboDocumentChange.Type.ADDED, doc3.getKey())),
        change.getLimboChanges());

    change = applyChanges(view, deletedDoc("rooms/eros/messages/2", 1));
    assertEquals(
        asList(new LimboDocumentChange(LimboDocumentChange.Type.REMOVED, doc3.getKey())),
        change.getLimboChanges());
  }

  @Test
  public void testViewsWithLimboDocumentsAreMarkedFromCache() {
    Query query = messageQuery();
    View view = new View(query, DocumentKey.emptyKeySet());
    Document doc1 = doc("rooms/eros/messages/0", 0, map());
    Document doc2 = doc("rooms/eros/messages/1", 0, map());

    // Doc1 is contained in the local view, but we are not yet CURRENT so it is expected that the
    // backend hasn't told us about all documents yet.
    ViewChange change = applyChanges(view, doc1);
    assertTrue(change.getSnapshot().isFromCache());

    // Add doc2 to generate a snapshot. Doc1 is still missing.
    View.DocumentChanges viewDocChanges = view.computeDocChanges(docUpdates(doc2));
    change =
        view.applyChanges(
            viewDocChanges, targetChange(ByteString.EMPTY, true, asList(doc2), null, null));
    assertTrue(change.getSnapshot().isFromCache()); // We are CURRENT but doc1 is in limbo.

    // Add doc1 to the backend's result set.
    change =
        view.applyChanges(
            viewDocChanges, targetChange(ByteString.EMPTY, true, asList(doc1), null, null));
    assertFalse(change.getSnapshot().isFromCache());
  }

  @Test
  public void testResumingQueryCreatesNoLimbos() {
    Query query = messageQuery();
    Document doc1 = doc("rooms/eros/messages/0", 0, map());
    Document doc2 = doc("rooms/eros/messages/1", 0, map());

    // Unlike other cases, here the view is initialized with a set of previously synced documents
    // which happens when listening to a previously listened-to query.
    View view = new View(query, keySet(doc1.getKey(), doc2.getKey()));

    TargetChange markCurrent = ackTarget();
    View.DocumentChanges changes = view.computeDocChanges(docUpdates());
    ViewChange change = view.applyChanges(changes, markCurrent);
    assertEquals(emptyList(), change.getLimboChanges());
  }

  @Test
  public void testReturnsNeedsRefillOnDeleteInLimitQuery() {
    Query query = messageQuery().limitToFirst(2);
    Document doc1 = doc("rooms/eros/messages/0", 0, map());
    Document doc2 = doc("rooms/eros/messages/1", 0, map());
    View view = new View(query, DocumentKey.emptyKeySet());

    // Start with a full view.
    View.DocumentChanges changes = view.computeDocChanges(docUpdates(doc1, doc2));
    assertEquals(2, changes.documentSet.size());
    assertFalse(changes.needsRefill());
    assertEquals(2, changes.changeSet.getChanges().size());
    view.applyChanges(changes);

    // Remove one of the docs.
    changes = view.computeDocChanges(docUpdates(deletedDoc("rooms/eros/messages/0", 0)));
    assertEquals(1, changes.documentSet.size());
    assertTrue(changes.needsRefill());
    assertEquals(1, changes.changeSet.getChanges().size());
    // Refill it with just the one doc remaining.
    changes = view.computeDocChanges(docUpdates(doc2), changes);
    assertEquals(1, changes.documentSet.size());
    assertFalse(changes.needsRefill());
    assertEquals(1, changes.changeSet.getChanges().size());
    view.applyChanges(changes);
  }

  @Test
  public void testReturnsNeedsRefillOnReorderInLimitQuery() {
    Query query = messageQuery().orderBy(orderBy("order")).limitToFirst(2);
    Document doc1 = doc("rooms/eros/messages/0", 0, map("order", 1));
    Document doc2 = doc("rooms/eros/messages/1", 0, map("order", 2));
    Document doc3 = doc("rooms/eros/messages/2", 0, map("order", 3));
    View view = new View(query, DocumentKey.emptyKeySet());

    // Start with a full view.
    View.DocumentChanges changes = view.computeDocChanges(docUpdates(doc1, doc2, doc3));
    assertEquals(2, changes.documentSet.size());
    assertFalse(changes.needsRefill());
    assertEquals(2, changes.changeSet.getChanges().size());
    view.applyChanges(changes);

    // Move one of the docs.
    doc2 = doc("rooms/eros/messages/1", 1, map("order", 2000));
    changes = view.computeDocChanges(docUpdates(doc2));
    assertEquals(2, changes.documentSet.size());
    assertTrue(changes.needsRefill());
    assertEquals(1, changes.changeSet.getChanges().size());
    // Refill it with all three current docs.
    changes = view.computeDocChanges(docUpdates(doc1, doc2, doc3), changes);
    assertEquals(2, changes.documentSet.size());
    assertFalse(changes.needsRefill());
    assertEquals(2, changes.changeSet.getChanges().size());
    view.applyChanges(changes);
  }

  @Test
  public void testDoesNotNeedRefillOnReorderWithinLimit() {
    Query query = messageQuery().orderBy(orderBy("order")).limitToFirst(3);
    Document doc1 = doc("rooms/eros/messages/0", 0, map("order", 1));
    Document doc2 = doc("rooms/eros/messages/1", 0, map("order", 2));
    Document doc3 = doc("rooms/eros/messages/2", 0, map("order", 3));
    Document doc4 = doc("rooms/eros/messages/3", 0, map("order", 4));
    Document doc5 = doc("rooms/eros/messages/4", 0, map("order", 5));
    View view = new View(query, DocumentKey.emptyKeySet());

    // Start with a full view.
    View.DocumentChanges changes = view.computeDocChanges(docUpdates(doc1, doc2, doc3, doc4, doc5));
    assertEquals(3, changes.documentSet.size());
    assertFalse(changes.needsRefill());
    assertEquals(3, changes.changeSet.getChanges().size());
    view.applyChanges(changes);

    // Move one of the docs.
    doc1 = doc("rooms/eros/messages/0", 1, map("order", 3));
    changes = view.computeDocChanges(docUpdates(doc1));
    assertEquals(3, changes.documentSet.size());
    assertFalse(changes.needsRefill());
    assertEquals(1, changes.changeSet.getChanges().size());
    view.applyChanges(changes);
  }

  @Test
  public void testDoesNotNeedRefillOnReorderAfterLimitQuery() {
    Query query = messageQuery().orderBy(orderBy("order")).limitToFirst(3);
    Document doc1 = doc("rooms/eros/messages/0", 0, map("order", 1));
    Document doc2 = doc("rooms/eros/messages/1", 0, map("order", 2));
    Document doc3 = doc("rooms/eros/messages/2", 0, map("order", 3));
    Document doc4 = doc("rooms/eros/messages/3", 0, map("order", 4));
    Document doc5 = doc("rooms/eros/messages/4", 0, map("order", 5));
    View view = new View(query, DocumentKey.emptyKeySet());

    // Start with a full view.
    View.DocumentChanges changes = view.computeDocChanges(docUpdates(doc1, doc2, doc3, doc4, doc5));
    assertEquals(3, changes.documentSet.size());
    assertFalse(changes.needsRefill());
    assertEquals(3, changes.changeSet.getChanges().size());
    view.applyChanges(changes);

    // Move one of the docs.
    doc4 = doc("rooms/eros/messages/3", 1, map("order", 6));
    changes = view.computeDocChanges(docUpdates(doc4));
    assertEquals(3, changes.documentSet.size());
    assertFalse(changes.needsRefill());
    assertEquals(0, changes.changeSet.getChanges().size());
    view.applyChanges(changes);
  }

  @Test
  public void testDoesNotNeedRefillForAdditionAfterTheLimit() {
    Query query = messageQuery().limitToFirst(2);
    Document doc1 = doc("rooms/eros/messages/0", 0, map());
    Document doc2 = doc("rooms/eros/messages/1", 0, map());
    View view = new View(query, DocumentKey.emptyKeySet());

    // Start with a full view.
    View.DocumentChanges changes = view.computeDocChanges(docUpdates(doc1, doc2));
    assertEquals(2, changes.documentSet.size());
    assertFalse(changes.needsRefill());
    assertEquals(2, changes.changeSet.getChanges().size());
    view.applyChanges(changes);

    // Add a doc that is past the limit.
    changes = view.computeDocChanges(docUpdates(deletedDoc("rooms/eros/messages/2", 0)));
    assertEquals(2, changes.documentSet.size());
    assertFalse(changes.needsRefill());
    assertEquals(0, changes.changeSet.getChanges().size());
    view.applyChanges(changes);
  }

  @Test
  public void testDoesNotNeedRefillForDeletionsWhenNotNearTheLimit() {
    Query query = messageQuery().limitToFirst(20);
    Document doc1 = doc("rooms/eros/messages/0", 0, map());
    Document doc2 = doc("rooms/eros/messages/1", 0, map());
    View view = new View(query, DocumentKey.emptyKeySet());

    View.DocumentChanges changes = view.computeDocChanges(docUpdates(doc1, doc2));
    assertEquals(2, changes.documentSet.size());
    assertFalse(changes.needsRefill());
    assertEquals(2, changes.changeSet.getChanges().size());
    view.applyChanges(changes);

    // Remove one of the docs.
    changes = view.computeDocChanges(docUpdates(deletedDoc("rooms/eros/messages/1", 0)));
    assertEquals(1, changes.documentSet.size());
    assertFalse(changes.needsRefill());
    assertEquals(1, changes.changeSet.getChanges().size());
    view.applyChanges(changes);
  }

  @Test
  public void testHandlesApplyingIrrelevantDocs() {
    Query query = messageQuery().limitToFirst(2);
    Document doc1 = doc("rooms/eros/messages/0", 0, map());
    Document doc2 = doc("rooms/eros/messages/1", 0, map());
    View view = new View(query, DocumentKey.emptyKeySet());

    // Start with a full view.
    View.DocumentChanges changes = view.computeDocChanges(docUpdates(doc1, doc2));
    assertEquals(2, changes.documentSet.size());
    assertFalse(changes.needsRefill());
    assertEquals(2, changes.changeSet.getChanges().size());
    view.applyChanges(changes);

    // Remove a doc that isn't even in the results.
    changes = view.computeDocChanges(docUpdates(deletedDoc("rooms/eros/messages/2", 0)));
    assertEquals(2, changes.documentSet.size());
    assertFalse(changes.needsRefill());
    assertEquals(0, changes.changeSet.getChanges().size());
    view.applyChanges(changes);
  }

  @Test
  public void testComputesMutatedDocumentKeys() {
    Query query = messageQuery();
    Document doc1 = doc("rooms/eros/messages/0", 0, map());
    Document doc2 = doc("rooms/eros/messages/1", 0, map());
    View view = new View(query, DocumentKey.emptyKeySet());

    // Start with a full view.
    View.DocumentChanges changes = view.computeDocChanges(docUpdates(doc1, doc2));
    view.applyChanges(changes);
    assertEquals(keySet(), changes.mutatedKeys);

    Document doc3 = doc("rooms/eros/messages/2", 0, map(), Document.DocumentState.LOCAL_MUTATIONS);
    changes = view.computeDocChanges(docUpdates(doc3));
    view.applyChanges(changes);
    assertEquals(keySet(doc3.getKey()), changes.mutatedKeys);
  }

  @Test
  public void testRemovesKeysFromMutatedDocumentKeysWhenNewDocDoesNotHaveChanges() {
    Query query = messageQuery().limitToFirst(2);
    Document doc1 = doc("rooms/eros/messages/0", 0, map());
    Document doc2 = doc("rooms/eros/messages/1", 0, map(), Document.DocumentState.LOCAL_MUTATIONS);
    View view = new View(query, DocumentKey.emptyKeySet());

    // Start with a full view.
    View.DocumentChanges changes = view.computeDocChanges(docUpdates(doc1, doc2));
    view.applyChanges(changes);
    assertEquals(keySet(doc2.getKey()), changes.mutatedKeys);

    Document doc2Prime = doc("rooms/eros/messages/1", 0, map());

    changes = view.computeDocChanges(docUpdates(doc2Prime));
    view.applyChanges(changes);
    assertEquals(keySet(), changes.mutatedKeys);
  }

  @Test
  public void testRemembersLocalMutationsFromPreviousSnapshot() {
    Query query = messageQuery().limitToFirst(2);
    Document doc1 = doc("rooms/eros/messages/0", 0, map());
    Document doc2 = doc("rooms/eros/messages/1", 0, map(), Document.DocumentState.LOCAL_MUTATIONS);
    View view = new View(query, DocumentKey.emptyKeySet());

    // Start with a full view.
    View.DocumentChanges changes = view.computeDocChanges(docUpdates(doc1, doc2));
    view.applyChanges(changes);
    assertEquals(keySet(doc2.getKey()), changes.mutatedKeys);

    Document doc3 = doc("rooms/eros/messages/2", 0, map());
    changes = view.computeDocChanges(docUpdates(doc3));
    assertEquals(keySet(doc2.getKey()), changes.mutatedKeys);
  }

  @Test
  public void testRemembersLocalMutationsFromPreviousCallToComputeChanges() {
    Query query = messageQuery().limitToFirst(2);
    Document doc1 = doc("rooms/eros/messages/0", 0, map());
    Document doc2 = doc("rooms/eros/messages/1", 0, map(), Document.DocumentState.LOCAL_MUTATIONS);
    View view = new View(query, DocumentKey.emptyKeySet());

    // Start with a full view.
    View.DocumentChanges changes = view.computeDocChanges(docUpdates(doc1, doc2));
    assertEquals(keySet(doc2.getKey()), changes.mutatedKeys);

    Document doc3 = doc("rooms/eros/messages/2", 0, map());
    changes = view.computeDocChanges(docUpdates(doc3), changes);
    assertEquals(keySet(doc2.getKey()), changes.mutatedKeys);
  }

  @Test
  public void testRaisesHasPendingWritesForPendingMutationsInInitialSnapshot() {
    Query query = messageQuery();
    Document doc1 = doc("rooms/eros/messages/1", 0, map(), Document.DocumentState.LOCAL_MUTATIONS);
    View view = new View(query, DocumentKey.emptyKeySet());

    View.DocumentChanges changes = view.computeDocChanges(docUpdates(doc1));
    ViewChange viewChange = view.applyChanges(changes);

    assertTrue(viewChange.getSnapshot().hasPendingWrites());
  }

  @Test
  public void testDoesntRaiseHasPendingWritesForCommittedMutationsInInitialSnapshot() {
    Query query = messageQuery();
    Document doc1 =
        doc("rooms/eros/messages/1", 0, map(), Document.DocumentState.COMMITTED_MUTATIONS);
    View view = new View(query, DocumentKey.emptyKeySet());

    View.DocumentChanges changes = view.computeDocChanges(docUpdates(doc1));
    ViewChange viewChange = view.applyChanges(changes);

    assertFalse(viewChange.getSnapshot().hasPendingWrites());
  }

  @Test
  public void testSuppressesWriteAcknowledgementIfWatchHasNotCaughtUp() {
    // This test verifies that we don't get three events for a ServerTimestamp mutation. We suppress
    // the event generated by the write acknowledgement and instead wait for Watch to catch up.
    Query query = messageQuery();

    Document doc1 =
        doc("rooms/eros/messages/1", 1, map("time", 1), Document.DocumentState.LOCAL_MUTATIONS);
    Document doc1Committed =
        doc("rooms/eros/messages/1", 2, map("time", 2), Document.DocumentState.COMMITTED_MUTATIONS);
    Document doc1Acknowledged =
        doc("rooms/eros/messages/1", 2, map("time", 2), Document.DocumentState.SYNCED);

    Document doc2 =
        doc("rooms/eros/messages/2", 1, map("time", 1), Document.DocumentState.LOCAL_MUTATIONS);
    Document doc2Modified =
        doc("rooms/eros/messages/2", 2, map("time", 3), Document.DocumentState.LOCAL_MUTATIONS);
    Document doc2Acknowledged =
        doc("rooms/eros/messages/2", 2, map("time", 3), Document.DocumentState.SYNCED);

    View view = new View(query, DocumentKey.emptyKeySet());

    View.DocumentChanges changes = view.computeDocChanges(docUpdates(doc1, doc2));
    ViewChange snap = view.applyChanges(changes);

    assertEquals(
        asList(
            DocumentViewChange.create(Type.ADDED, doc1),
            DocumentViewChange.create(Type.ADDED, doc2)),
        snap.getSnapshot().getChanges());

    changes = view.computeDocChanges(docUpdates(doc1Committed, doc2Modified));
    snap = view.applyChanges(changes);

    // The 'doc1Committed' update is suppressed
    assertEquals(
        Collections.singletonList(DocumentViewChange.create(Type.MODIFIED, doc2Modified)),
        snap.getSnapshot().getChanges());

    changes = view.computeDocChanges(docUpdates(doc1Acknowledged, doc2Acknowledged));
    snap = view.applyChanges(changes);

    assertEquals(
        asList(
            DocumentViewChange.create(Type.MODIFIED, doc1Acknowledged),
            DocumentViewChange.create(Type.METADATA, doc2Acknowledged)),
        snap.getSnapshot().getChanges());
  }
}
