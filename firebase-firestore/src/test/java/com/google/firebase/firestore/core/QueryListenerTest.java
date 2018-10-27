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
import static com.google.firebase.firestore.testutil.TestUtil.doc;
import static com.google.firebase.firestore.testutil.TestUtil.docUpdates;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.testutil.TestUtil.path;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.core.DocumentViewChange.Type;
import com.google.firebase.firestore.core.EventManager.ListenOptions;
import com.google.firebase.firestore.core.View.DocumentChanges;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.DocumentSet;
import com.google.firebase.firestore.model.MaybeDocument;
import com.google.firebase.firestore.remote.TargetChange;
import com.google.firebase.firestore.util.Util;
import io.grpc.Status;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class QueryListenerTest {
  private static ViewSnapshot applyChanges(View view, MaybeDocument... docs) {
    return view.applyChanges(view.computeDocChanges(docUpdates(docs))).getSnapshot();
  }

  private static QueryListener queryListener(
      Query query, ListenOptions options, List<ViewSnapshot> accumulator) {
    return new QueryListener(
        query,
        options,
        (value, error) -> {
          assertNull(error);
          accumulator.add(value);
        });
  }

  private static QueryListener queryListener(Query query, List<ViewSnapshot> accumulator) {
    ListenOptions options = new ListenOptions();
    options.includeDocumentMetadataChanges = true;
    options.includeQueryMetadataChanges = true;
    return queryListener(query, options, accumulator);
  }

  @Test
  public void testRaisesCollectionEvents() {
    final List<ViewSnapshot> accum = new ArrayList<>();
    final List<ViewSnapshot> otherAccum = new ArrayList<>();

    Query query = Query.atPath(path("rooms"));
    Document doc1 = doc("rooms/eros", 1, map("name", "eros"));
    Document doc2 = doc("rooms/hades", 2, map("name", "hades"));
    Document doc2prime = doc("rooms/hades", 3, map("name", "hades", "owner", "Jonny"));

    QueryListener listener = queryListener(query, accum);
    QueryListener otherListener = queryListener(query, otherAccum);

    View view = new View(query, DocumentKey.emptyKeySet());
    ViewSnapshot snap1 = applyChanges(view, doc1, doc2);
    ViewSnapshot snap2 = applyChanges(view, doc2prime);

    DocumentViewChange change1 = DocumentViewChange.create(Type.ADDED, doc1);
    DocumentViewChange change2 = DocumentViewChange.create(Type.ADDED, doc2);
    DocumentViewChange change3 = DocumentViewChange.create(Type.MODIFIED, doc2prime);
    // Second listener should receive doc2prime as added document not modified.
    DocumentViewChange change4 = DocumentViewChange.create(Type.ADDED, doc2prime);

    listener.onViewSnapshot(snap1);
    listener.onViewSnapshot(snap2);
    otherListener.onViewSnapshot(snap2);
    assertEquals(asList(snap1, snap2), accum);
    assertEquals(asList(change1, change2), accum.get(0).getChanges());
    assertEquals(asList(change3), accum.get(1).getChanges());

    ViewSnapshot snap2Prime =
        new ViewSnapshot(
            snap2.getQuery(),
            snap2.getDocuments(),
            DocumentSet.emptySet(snap2.getQuery().comparator()),
            asList(change1, change4),
            snap2.isFromCache(),
            snap2.getMutatedKeys(),
            /* didSyncStateChange= */ true,
            /* excludesMetadataChanges= */ false);
    assertEquals(asList(snap2Prime), otherAccum);
  }

  @Test
  public void testRaisesErrorEvent() {
    Query query = Query.atPath(path("rooms/eros"));

    AtomicBoolean hadEvent = new AtomicBoolean(false);
    QueryListener listener =
        new QueryListener(
            query,
            new ListenOptions(),
            (value, error) -> {
              assertNull(value);
              assertNotNull(error);
              hadEvent.set(true);
            });
    Status status = Status.ALREADY_EXISTS.withDescription("test error");
    FirebaseFirestoreException error = Util.exceptionFromStatus(status);
    listener.onError(error);
    assertTrue(hadEvent.get());
  }

  @Test
  public void testRaisesEventForEmptyCollectionsAfterSync() {
    final List<ViewSnapshot> accum = new ArrayList<>();
    Query query = Query.atPath(path("rooms"));

    QueryListener listener = queryListener(query, accum);

    View view = new View(query, DocumentKey.emptyKeySet());
    ViewSnapshot snap1 = applyChanges(view);
    TargetChange ackTarget = ackTarget();
    ViewSnapshot snap2 =
        view.applyChanges(view.computeDocChanges(docUpdates()), ackTarget).getSnapshot();

    listener.onViewSnapshot(snap1);
    assertEquals(asList(), accum);

    listener.onViewSnapshot(snap2);
    assertEquals(asList(snap2), accum);
  }

  @Test
  public void testDoesNotRaiseEventsForMetadataChangesUnlessSpecified() {
    List<ViewSnapshot> filteredAccum = new ArrayList<>();
    List<ViewSnapshot> fullAccum = new ArrayList<>();
    Query query = Query.atPath(path("rooms"));
    Document doc1 = doc("rooms/eros", 1, map("name", "eros"));
    Document doc2 = doc("rooms/hades", 2, map("name", "hades"));
    ListenOptions options1 = new ListenOptions();
    ListenOptions options2 = new ListenOptions();
    options2.includeQueryMetadataChanges = true;
    options2.includeDocumentMetadataChanges = true;
    QueryListener filteredListener = queryListener(query, options1, filteredAccum);
    QueryListener fullListener = queryListener(query, options2, fullAccum);

    View view = new View(query, DocumentKey.emptyKeySet());
    ViewSnapshot snap1 = applyChanges(view, doc1);

    TargetChange ackTarget = ackTarget(doc1);
    ViewSnapshot snap2 =
        view.applyChanges(view.computeDocChanges(docUpdates()), ackTarget).getSnapshot();
    ViewSnapshot snap3 = applyChanges(view, doc2);

    filteredListener.onViewSnapshot(snap1); // local event
    filteredListener.onViewSnapshot(snap2); // no event
    filteredListener.onViewSnapshot(snap3); // doc2 update

    fullListener.onViewSnapshot(snap1); // local event
    fullListener.onViewSnapshot(snap2); // no event
    fullListener.onViewSnapshot(snap3); // doc2 update

    assertEquals(
        asList(
            applyExpectedMetadata(snap1, MetadataChanges.EXCLUDE),
            applyExpectedMetadata(snap3, MetadataChanges.EXCLUDE)),
        filteredAccum);
    assertEquals(asList(snap1, snap2, snap3), fullAccum);
  }

  @Test
  public void testRaisesDocumentMetadataEventsOnlyWhenSpecified() {
    List<ViewSnapshot> filteredAccum = new ArrayList<>();
    List<ViewSnapshot> fullAccum = new ArrayList<>();
    Query query = Query.atPath(path("rooms"));
    Document doc1 = doc("rooms/eros", 1, map("name", "eros"));
    Document doc1Prime =
        doc("rooms/eros", 1, map("name", "eros"), Document.DocumentState.LOCAL_MUTATIONS);
    Document doc2 = doc("rooms/hades", 2, map("name", "hades"));
    Document doc3 = doc("rooms/other", 3, map("name", "other"));

    ListenOptions options1 = new ListenOptions();
    ListenOptions options2 = new ListenOptions();
    options2.includeDocumentMetadataChanges = true;
    QueryListener filteredListener = queryListener(query, options1, filteredAccum);
    QueryListener fullListener = queryListener(query, options2, fullAccum);

    View view = new View(query, DocumentKey.emptyKeySet());
    ViewSnapshot snap1 = applyChanges(view, doc1, doc2);
    ViewSnapshot snap2 = applyChanges(view, doc1Prime);
    ViewSnapshot snap3 = applyChanges(view, doc3);

    filteredListener.onViewSnapshot(snap1);
    filteredListener.onViewSnapshot(snap2);
    filteredListener.onViewSnapshot(snap3);

    fullListener.onViewSnapshot(snap1);
    fullListener.onViewSnapshot(snap2);
    fullListener.onViewSnapshot(snap3);

    assertEquals(
        asList(
            applyExpectedMetadata(snap1, MetadataChanges.EXCLUDE),
            applyExpectedMetadata(snap3, MetadataChanges.EXCLUDE)),
        filteredAccum);
    // Second listener should receive doc1prime as added document not modified
    assertEquals(asList(snap1, snap2, snap3), fullAccum);
  }

  @Test
  public void testRaisesQueryMetadataEventsOnlyWhenHasPendingWritesOnTheQueryChanges() {
    List<ViewSnapshot> fullAccum = new ArrayList<>();
    Query query = Query.atPath(path("rooms"));
    Document doc1 =
        doc("rooms/eros", 1, map("name", "eros"), Document.DocumentState.LOCAL_MUTATIONS);
    Document doc2 =
        doc("rooms/hades", 2, map("name", "hades"), Document.DocumentState.LOCAL_MUTATIONS);
    Document doc1Prime = doc("rooms/eros", 1, map("name", "eros"));
    Document doc2Prime = doc("rooms/hades", 2, map("name", "hades"));
    Document doc3 = doc("rooms/other", 3, map("name", "other"));

    ListenOptions options = new ListenOptions();
    options.includeQueryMetadataChanges = true;
    QueryListener fullListener = queryListener(query, options, fullAccum);

    View view = new View(query, DocumentKey.emptyKeySet());
    ViewSnapshot snap1 = applyChanges(view, doc1, doc2);
    ViewSnapshot snap2 = applyChanges(view, doc1Prime);
    ViewSnapshot snap3 = applyChanges(view, doc3);
    ViewSnapshot snap4 = applyChanges(view, doc2Prime);

    fullListener.onViewSnapshot(snap1);
    fullListener.onViewSnapshot(snap2); // Emits no events
    fullListener.onViewSnapshot(snap3);
    fullListener.onViewSnapshot(snap4); // Metadata change event

    ViewSnapshot expectedSnapshot4 =
        new ViewSnapshot(
            snap4.getQuery(),
            snap4.getDocuments(),
            snap3.getDocuments(),
            asList(),
            snap4.isFromCache(),
            snap4.getMutatedKeys(),
            snap4.didSyncStateChange(),
            /* excludeMetadataChanges= */ true); // This test excludes document metadata changes

    assertEquals(
        asList(
            applyExpectedMetadata(snap1, MetadataChanges.EXCLUDE),
            applyExpectedMetadata(snap3, MetadataChanges.EXCLUDE),
            expectedSnapshot4),
        fullAccum);
  }

  @Test
  public void testMetadataOnlyDocumentChangesAreFilteredOut() {
    List<ViewSnapshot> filteredAccum = new ArrayList<>();
    Query query = Query.atPath(path("rooms"));
    Document doc1 = doc("rooms/eros", 1, map("name", "eros"));
    Document doc1Prime =
        doc("rooms/eros", 1, map("name", "eros"), Document.DocumentState.LOCAL_MUTATIONS);
    Document doc2 = doc("rooms/hades", 2, map("name", "hades"));
    Document doc3 = doc("rooms/other", 3, map("name", "other"));

    ListenOptions options = new ListenOptions();
    options.includeDocumentMetadataChanges = false;
    QueryListener filteredListener = queryListener(query, options, filteredAccum);

    View view = new View(query, DocumentKey.emptyKeySet());
    ViewSnapshot snap1 = applyChanges(view, doc1, doc2);
    ViewSnapshot snap2 = applyChanges(view, doc1Prime, doc3);

    filteredListener.onViewSnapshot(snap1);
    filteredListener.onViewSnapshot(snap2);

    DocumentViewChange change3 = DocumentViewChange.create(Type.ADDED, doc3);
    ViewSnapshot expectedSnapshot2 =
        new ViewSnapshot(
            snap2.getQuery(),
            snap2.getDocuments(),
            snap1.getDocuments(),
            asList(change3),
            snap2.isFromCache(),
            snap2.getMutatedKeys(),
            snap2.didSyncStateChange(),
            /* excludesMetadataChanges= */ true);
    assertEquals(
        asList(applyExpectedMetadata(snap1, MetadataChanges.EXCLUDE), expectedSnapshot2),
        filteredAccum);
  }

  @Test
  public void testWillWaitForSyncIfOnline() {
    List<ViewSnapshot> events = new ArrayList<>();
    Query query = Query.atPath(path("rooms"));

    Document doc1 = doc("rooms/eros", 1, map("name", "eros"));
    Document doc2 = doc("rooms/hades", 2, map("name", "hades"));

    ListenOptions options = new ListenOptions();
    options.waitForSyncWhenOnline = true;
    QueryListener listener = queryListener(query, options, events);

    View view = new View(query, DocumentKey.emptyKeySet());
    ViewSnapshot snap1 = applyChanges(view, doc1);
    ViewSnapshot snap2 = applyChanges(view, doc2);
    DocumentChanges changes = view.computeDocChanges(docUpdates());
    ViewSnapshot snap3 = view.applyChanges(changes, ackTarget(doc1, doc2)).getSnapshot();

    listener.onOnlineStateChanged(OnlineState.ONLINE); // no event
    listener.onViewSnapshot(snap1); // no event
    listener.onOnlineStateChanged(OnlineState.UNKNOWN); // no event
    listener.onOnlineStateChanged(OnlineState.ONLINE); // no event
    listener.onViewSnapshot(snap2); // no event
    listener.onViewSnapshot(snap3); // event because synced

    DocumentViewChange change1 = DocumentViewChange.create(Type.ADDED, doc1);
    DocumentViewChange change2 = DocumentViewChange.create(Type.ADDED, doc2);
    ViewSnapshot expectedSnapshot =
        new ViewSnapshot(
            snap3.getQuery(),
            snap3.getDocuments(),
            DocumentSet.emptySet(snap3.getQuery().comparator()),
            asList(change1, change2),
            /* isFromCache= */ false,
            snap3.getMutatedKeys(),
            /* didSyncStateChange= */ true,
            /* excludesMetadataChanges= */ true);
    assertEquals(asList(expectedSnapshot), events);
  }

  @Test
  public void testWillRaiseInitialEventWhenGoingOffline() {
    List<ViewSnapshot> events = new ArrayList<>();
    Query query = Query.atPath(path("rooms"));

    Document doc1 = doc("rooms/eros", 1, map("name", "eros"));
    Document doc2 = doc("rooms/hades", 2, map("name", "hades"));

    ListenOptions options = new ListenOptions();
    options.waitForSyncWhenOnline = true;
    QueryListener listener = queryListener(query, options, events);

    View view = new View(query, DocumentKey.emptyKeySet());
    ViewSnapshot snap1 = applyChanges(view, doc1);
    ViewSnapshot snap2 = applyChanges(view, doc2);

    listener.onOnlineStateChanged(OnlineState.ONLINE); // no event
    listener.onViewSnapshot(snap1); // no event
    listener.onOnlineStateChanged(OnlineState.OFFLINE); // event
    listener.onOnlineStateChanged(OnlineState.ONLINE); // event
    listener.onOnlineStateChanged(OnlineState.OFFLINE); // no event
    listener.onViewSnapshot(snap2); // event

    DocumentViewChange change1 = DocumentViewChange.create(Type.ADDED, doc1);
    DocumentViewChange change2 = DocumentViewChange.create(Type.ADDED, doc2);
    ViewSnapshot expectedSnapshot1 =
        new ViewSnapshot(
            snap1.getQuery(),
            snap1.getDocuments(),
            DocumentSet.emptySet(snap1.getQuery().comparator()),
            asList(change1),
            /* isFromCache= */ true,
            snap1.getMutatedKeys(),
            /* didSyncStateChange= */ true,
            /* excludesMetadataChanges= */ true);
    ViewSnapshot expectedSnapshot2 =
        new ViewSnapshot(
            snap2.getQuery(),
            snap2.getDocuments(),
            snap1.getDocuments(),
            asList(change2),
            /* isFromCache= */ true,
            snap2.getMutatedKeys(),
            /* didSyncStateChange= */ false,
            /* excludesMetadataChanges= */ true);
    assertEquals(asList(expectedSnapshot1, expectedSnapshot2), events);
  }

  @Test
  public void testWillRaiseInitialEventWhenGoingOfflineAndThereAreNoDocs() {
    List<ViewSnapshot> events = new ArrayList<>();
    Query query = Query.atPath(path("rooms"));

    QueryListener listener = queryListener(query, new ListenOptions(), events);

    View view = new View(query, DocumentKey.emptyKeySet());
    ViewSnapshot snap1 = applyChanges(view);

    listener.onOnlineStateChanged(OnlineState.ONLINE); // no event
    listener.onViewSnapshot(snap1); // no event
    listener.onOnlineStateChanged(OnlineState.OFFLINE); // event

    ViewSnapshot expectedSnapshot =
        new ViewSnapshot(
            snap1.getQuery(),
            snap1.getDocuments(),
            DocumentSet.emptySet(snap1.getQuery().comparator()),
            asList(),
            /* isFromCache= */ true,
            snap1.getMutatedKeys(),
            /* didSyncStateChange= */ true,
            /* excludesMetadataChanges= */ true);
    assertEquals(asList(expectedSnapshot), events);
  }

  @Test
  public void testWillRaiseInitialEventWhenStartingOfflineAndThereAreNoDocs() {
    List<ViewSnapshot> events = new ArrayList<>();
    Query query = Query.atPath(path("rooms"));

    QueryListener listener = queryListener(query, new ListenOptions(), events);

    View view = new View(query, DocumentKey.emptyKeySet());
    ViewSnapshot snap1 = applyChanges(view);

    listener.onOnlineStateChanged(OnlineState.OFFLINE);
    listener.onViewSnapshot(snap1);

    ViewSnapshot expectedSnapshot =
        new ViewSnapshot(
            snap1.getQuery(),
            snap1.getDocuments(),
            DocumentSet.emptySet(snap1.getQuery().comparator()),
            asList(),
            /* isFromCache= */ true,
            snap1.getMutatedKeys(),
            /* didSyncStateChange= */ true,
            /* excludesMetadataChanges= */ true);
    assertEquals(asList(expectedSnapshot), events);
  }

  private ViewSnapshot applyExpectedMetadata(ViewSnapshot snap, MetadataChanges metadata) {
    return new ViewSnapshot(
        snap.getQuery(),
        snap.getDocuments(),
        snap.getOldDocuments(),
        snap.getChanges(),
        snap.isFromCache(),
        snap.getMutatedKeys(),
        snap.didSyncStateChange(),
        MetadataChanges.EXCLUDE.equals(metadata));
  }
}
