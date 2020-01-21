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

package com.google.firebase.firestore.remote;

import static com.google.firebase.firestore.testutil.TestUtil.activeLimboQueries;
import static com.google.firebase.firestore.testutil.TestUtil.activeQueries;
import static com.google.firebase.firestore.testutil.TestUtil.deletedDoc;
import static com.google.firebase.firestore.testutil.TestUtil.doc;
import static com.google.firebase.firestore.testutil.TestUtil.key;
import static com.google.firebase.firestore.testutil.TestUtil.keySet;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.testutil.TestUtil.targetChange;
import static com.google.firebase.firestore.testutil.TestUtil.version;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.firebase.database.collection.ImmutableSortedSet;
import com.google.firebase.firestore.local.TargetData;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.NoDocument;
import com.google.firebase.firestore.remote.WatchChange.DocumentChange;
import com.google.firebase.firestore.remote.WatchChange.WatchTargetChange;
import com.google.firebase.firestore.remote.WatchChange.WatchTargetChangeType;
import com.google.firebase.firestore.testutil.TestTargetMetadataProvider;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class RemoteEventTest {

  private TestTargetMetadataProvider targetMetadataProvider;
  private Map<Integer, Integer> noOutstandingResponses = Collections.emptyMap();
  private ImmutableSortedSet<DocumentKey> noExistingKeys = DocumentKey.emptyKeySet();
  private ByteString resumeToken = ByteString.copyFromUtf8("resume");

  @Before
  public void before() {
    targetMetadataProvider = new TestTargetMetadataProvider();
  }

  /**
   * Creates an aggregator initialized with the set of provided WatchChanges. Tests can add further
   * changes via `handleDocumentChange`, `handleTargetChange` and `handleExistenceFilterChange`.
   *
   * @param targetMap A map of query data for all active targets. The map must include an entry for
   *     every target referenced by any of the watch changes.
   * @param outstandingResponses The number of outstanding ACKs a target has to receive before it is
   *     considered active, or `noOutstandingResponses` if all targets are already active.
   * @param existingKeys The set of documents that are considered synced with the test targets as
   *     part of a previous listen. To modify this set during test execution, invoke
   *     `targetMetadataProvider.setSyncedKeys()`.
   * @param watchChanges The watch changes to apply before returning the aggregator. Supported
   *     changes are DocumentWatchChange and WatchTargetChange.
   */
  private WatchChangeAggregator createAggregator(
      Map<Integer, TargetData> targetMap,
      Map<Integer, Integer> outstandingResponses,
      ImmutableSortedSet<DocumentKey> existingKeys,
      WatchChange... watchChanges) {
    WatchChangeAggregator aggregator = new WatchChangeAggregator(targetMetadataProvider);

    List<Integer> targetIds = new ArrayList<>();

    for (Map.Entry<Integer, TargetData> entry : targetMap.entrySet()) {
      targetIds.add(entry.getKey());
      targetMetadataProvider.setSyncedKeys(entry.getValue(), existingKeys);
    }

    for (Map.Entry<Integer, Integer> entry : outstandingResponses.entrySet()) {
      for (int i = 0; i < entry.getValue(); ++i) {
        aggregator.recordPendingTargetRequest(entry.getKey());
      }
    }

    for (WatchChange watchChange : watchChanges) {
      if (watchChange instanceof DocumentChange) {
        aggregator.handleDocumentChange((DocumentChange) watchChange);
      } else if (watchChange instanceof WatchTargetChange) {
        aggregator.handleTargetChange((WatchTargetChange) watchChange);
      } else {
        fail("Encountered unexpected type of WatchChange");
      }
    }

    aggregator.handleTargetChange(
        new WatchTargetChange(WatchTargetChangeType.NoChange, targetIds, resumeToken));
    return aggregator;
  }

  /**
   * Creates a single remote event that includes target changes for all provided WatchChanges.
   *
   * @param snapshotVersion The version at which to create the remote event. This corresponds to the
   *     snapshot version provided by the NO_CHANGE event.
   * @param targetMap A map of query data for all active targets. The map must include an entry for
   *     every target referenced by any of the watch changes.
   * @param outstandingResponses The number of outstanding ACKs a target has to receive before it is
   *     considered active, or `noOutstandingResponses` if all targets are already active.
   * @param existingKeys The set of documents that are considered synced with the test targets as
   *     part of a previous listen. To modify this set during test execution, invoke
   *     `targetMetadataProvider.setSyncedKeys()`.
   * @param watchChanges The watch changes to apply before returning the aggregator. Supported
   *     changes are DocumentWatchChange and WatchTargetChange.
   */
  private RemoteEvent createRemoteEvent(
      long snapshotVersion,
      Map<Integer, TargetData> targetMap,
      Map<Integer, Integer> outstandingResponses,
      ImmutableSortedSet<DocumentKey> existingKeys,
      WatchChange... watchChanges) {
    WatchChangeAggregator aggregator =
        createAggregator(targetMap, outstandingResponses, existingKeys, watchChanges);
    return aggregator.createRemoteEvent(version(snapshotVersion));
  }

  @Test
  public void testWillAccumulateDocumentAddedAndRemovedEvents() {
    Map<Integer, TargetData> targetMap = activeQueries(1, 2, 3, 4, 5, 6);

    Document existingDoc = doc("docs/1", 1, map("value", 1));
    Document newDoc = doc("docs/2", 2, map("value", 2));

    WatchChange change1 =
        new DocumentChange(asList(1, 2, 3), asList(4, 5, 6), existingDoc.getKey(), existingDoc);
    WatchChange change2 = new DocumentChange(asList(1, 4), asList(2, 6), newDoc.getKey(), newDoc);

    RemoteEvent event =
        createRemoteEvent(
            3, targetMap, noOutstandingResponses, keySet(existingDoc.getKey()), change1, change2);
    assertEquals(version(3), event.getSnapshotVersion());
    assertEquals(2, event.getDocumentUpdates().size());
    assertEquals(existingDoc, event.getDocumentUpdates().get(existingDoc.getKey()));
    assertEquals(newDoc, event.getDocumentUpdates().get(newDoc.getKey()));

    assertEquals(6, event.getTargetChanges().size());

    TargetChange mapping1 =
        targetChange(resumeToken, false, asList(newDoc), asList(existingDoc), null);
    assertEquals(mapping1, event.getTargetChanges().get(1));

    TargetChange mapping2 = targetChange(resumeToken, false, null, asList(existingDoc), null);
    assertEquals(mapping2, event.getTargetChanges().get(2));

    TargetChange mapping3 = targetChange(resumeToken, false, null, asList(existingDoc), null);
    assertEquals(mapping3, event.getTargetChanges().get(3));

    TargetChange mapping4 =
        targetChange(resumeToken, false, asList(newDoc), null, asList(existingDoc));
    assertEquals(mapping4, event.getTargetChanges().get(4));

    TargetChange mapping5 = targetChange(resumeToken, false, null, null, asList(existingDoc));
    assertEquals(mapping5, event.getTargetChanges().get(5));

    TargetChange mapping6 = targetChange(resumeToken, false, null, null, asList(existingDoc));
    assertEquals(mapping6, event.getTargetChanges().get(6));
  }

  @Test
  public void testWillIgnoreEventsForPendingTargets() {
    Map<Integer, TargetData> targetMap = activeQueries(1);

    Document doc1 = doc("docs/1", 1, map("value", 1));
    Document doc2 = doc("docs/2", 2, map("value", 2));

    // We're waiting for the watch and unwatch ack
    Map<Integer, Integer> outstanding = new HashMap<>();
    outstanding.put(1, 2);

    WatchChange change1 = new DocumentChange(asList(1), emptyList(), doc1.getKey(), doc1);
    WatchChange change2 = new WatchTargetChange(WatchTargetChangeType.Removed, asList(1));
    WatchChange change3 = new WatchTargetChange(WatchTargetChangeType.Added, asList(1));
    WatchChange change4 = new DocumentChange(asList(1), emptyList(), doc2.getKey(), doc2);

    RemoteEvent event =
        createRemoteEvent(
            3, targetMap, outstanding, noExistingKeys, change1, change2, change3, change4);
    assertEquals(version(3), event.getSnapshotVersion());
    // doc1 is ignored because the target was not active at the time, but for
    // doc2 the target is active.
    assertEquals(1, event.getDocumentUpdates().size());
    assertEquals(doc2, event.getDocumentUpdates().get(doc2.getKey()));

    assertEquals(1, event.getTargetChanges().size());
  }

  @Test
  public void testWillIgnoreEventsForRemovedTargets() {
    Map<Integer, TargetData> targetMap = activeQueries();

    Document doc1 = doc("docs/1", 1, map("value", 1));

    // We're waiting for the unwatch ack
    Map<Integer, Integer> outstanding = new HashMap<>();
    outstanding.put(1, 1);

    WatchChange change1 = new DocumentChange(asList(1), emptyList(), doc1.getKey(), doc1);
    WatchChange change2 = new WatchTargetChange(WatchTargetChangeType.Removed, asList(1));

    RemoteEvent event =
        createRemoteEvent(3, targetMap, outstanding, noExistingKeys, change1, change2);
    assertEquals(version(3), event.getSnapshotVersion());
    // doc1 is ignored because it was not apart of an active target.
    assertEquals(0, event.getDocumentUpdates().size());
    // Target 1 is ignored because it was removed
    assertEquals(0, event.getTargetChanges().size());
  }

  @Test
  public void testWillKeepResetMappingEvenWithUpdates() {
    Map<Integer, TargetData> targetMap = activeQueries(1);

    Document doc1 = doc("docs/1", 1, map("value", 1));
    Document doc2 = doc("docs/2", 2, map("value", 2));
    Document doc3 = doc("docs/3", 3, map("value", 3));

    WatchChange change1 = new DocumentChange(asList(1), emptyList(), doc1.getKey(), doc1);
    // Reset stream, ignoring doc1
    WatchChange change2 = new WatchTargetChange(WatchTargetChangeType.Reset, asList(1));

    // Add doc2, doc3
    WatchChange change3 = new DocumentChange(asList(1), emptyList(), doc2.getKey(), doc2);
    WatchChange change4 = new DocumentChange(asList(1), emptyList(), doc3.getKey(), doc3);

    // Remove doc2 again, should not show up in reset mapping.
    WatchChange change5 = new DocumentChange(emptyList(), asList(1), doc2.getKey(), doc2);

    RemoteEvent event =
        createRemoteEvent(
            3,
            targetMap,
            noOutstandingResponses,
            keySet(doc1.getKey()),
            change1,
            change2,
            change3,
            change4,
            change5);
    assertEquals(version(3), event.getSnapshotVersion());
    assertEquals(3, event.getDocumentUpdates().size());
    assertEquals(doc1, event.getDocumentUpdates().get(doc1.getKey()));
    assertEquals(doc2, event.getDocumentUpdates().get(doc2.getKey()));
    assertEquals(doc3, event.getDocumentUpdates().get(doc3.getKey()));

    assertEquals(1, event.getTargetChanges().size());

    // Only doc3 is part of the new mapping.
    TargetChange expected = targetChange(resumeToken, false, asList(doc3), null, asList(doc1));
    assertEquals(expected, event.getTargetChanges().get(1));
  }

  @Test
  public void testWillHandleSingleReset() {
    Map<Integer, TargetData> targetMap = activeQueries(1);

    WatchChangeAggregator aggregator =
        createAggregator(targetMap, noOutstandingResponses, noExistingKeys);

    // Reset target
    WatchTargetChange change = new WatchTargetChange(WatchTargetChangeType.Reset, asList(1));
    aggregator.handleTargetChange(change);

    RemoteEvent event = aggregator.createRemoteEvent(version(3));
    assertEquals(version(3), event.getSnapshotVersion());
    assertEquals(0, event.getDocumentUpdates().size());

    assertEquals(1, event.getTargetChanges().size());

    // Reset mapping is empty.
    TargetChange expected = targetChange(ByteString.EMPTY, false, null, null, null);
    assertEquals(expected, event.getTargetChanges().get(1));
  }

  @Test
  public void testWillHandleTargetAddAndRemovalInSameBatch() {
    Map<Integer, TargetData> targetMap = activeQueries(1, 2);

    Document doc1a = doc("docs/1", 1, map("value", 1));
    Document doc1b = doc("docs/1", 1, map("value", 2));

    WatchChange change1 = new DocumentChange(asList(1), asList(2), doc1a.getKey(), doc1a);
    WatchChange change2 = new DocumentChange(asList(2), asList(1), doc1b.getKey(), doc1b);

    RemoteEvent event =
        createRemoteEvent(
            3, targetMap, noOutstandingResponses, keySet(doc1a.getKey()), change1, change2);
    assertEquals(version(3), event.getSnapshotVersion());
    assertEquals(1, event.getDocumentUpdates().size());
    assertEquals(doc1b, event.getDocumentUpdates().get(doc1b.getKey()));

    assertEquals(2, event.getTargetChanges().size());

    TargetChange mapping1 = targetChange(resumeToken, false, null, null, asList(doc1b));
    assertEquals(mapping1, event.getTargetChanges().get(1));

    TargetChange mapping2 = targetChange(resumeToken, false, null, asList(doc1b), null);
    assertEquals(mapping2, event.getTargetChanges().get(2));
  }

  @Test
  public void testTargetCurrentChangeWillMarkTheTargetCurrent() {
    Map<Integer, TargetData> targetMap = activeQueries(1);

    WatchChange change = new WatchTargetChange(WatchTargetChangeType.Current, asList(1));

    RemoteEvent event =
        createRemoteEvent(3, targetMap, noOutstandingResponses, noExistingKeys, change);
    assertEquals(version(3), event.getSnapshotVersion());
    assertEquals(0, event.getDocumentUpdates().size());
    assertEquals(1, event.getTargetChanges().size());

    TargetChange mapping = targetChange(resumeToken, true, null, null, null);
    assertEquals(mapping, event.getTargetChanges().get(1));
  }

  @Test
  public void testTargetAddedChangeWillResetPreviousState() {
    Map<Integer, TargetData> targetMap = activeQueries(1, 3);

    Document doc1 = doc("docs/1", 1, map("value", 1));
    Document doc2 = doc("docs/2", 2, map("value", 2));

    WatchChange change1 = new DocumentChange(asList(1, 3), asList(2), doc1.getKey(), doc1);
    WatchChange change2 = new WatchTargetChange(WatchTargetChangeType.Current, asList(1, 2, 3));
    WatchChange change3 = new WatchTargetChange(WatchTargetChangeType.Removed, asList(1));
    WatchChange change4 = new WatchTargetChange(WatchTargetChangeType.Removed, asList(2));
    WatchChange change5 = new WatchTargetChange(WatchTargetChangeType.Added, asList(1));
    WatchChange change6 = new DocumentChange(asList(1), asList(3), doc2.getKey(), doc2);

    Map<Integer, Integer> outstanding = new HashMap<>();
    outstanding.put(1, 2);
    outstanding.put(2, 1);

    RemoteEvent event =
        createRemoteEvent(
            3,
            targetMap,
            outstanding,
            keySet(doc2.getKey()),
            change1,
            change2,
            change3,
            change4,
            change5,
            change6);
    assertEquals(version(3), event.getSnapshotVersion());
    assertEquals(2, event.getDocumentUpdates().size());
    assertEquals(doc1, event.getDocumentUpdates().get(doc1.getKey()));
    assertEquals(doc2, event.getDocumentUpdates().get(doc2.getKey()));

    // target 1 and 3 are affected (1 because of re-add), target 2 is not because of remove.
    assertEquals(2, event.getTargetChanges().size());

    // doc1 was before the remove, so it does not show up in the mapping.
    // Current was before the remove.
    TargetChange mapping1 = targetChange(resumeToken, false, null, asList(doc2), null);
    assertEquals(mapping1, event.getTargetChanges().get(1));

    // Doc1 was before the remove.
    // Current was after the remove
    TargetChange mapping3 = targetChange(resumeToken, true, asList(doc1), null, asList(doc2));
    assertEquals(mapping3, event.getTargetChanges().get(3));
  }

  @Test
  public void testNoChangeWillStillMarkTheAffectedTargets() {
    Map<Integer, TargetData> targetMap = activeQueries(1);

    WatchChangeAggregator aggregator =
        createAggregator(targetMap, noOutstandingResponses, noExistingKeys);

    WatchTargetChange change = new WatchTargetChange(WatchTargetChangeType.NoChange, asList(1));
    aggregator.handleTargetChange(change);

    RemoteEvent event = aggregator.createRemoteEvent(version(3));
    assertEquals(version(3), event.getSnapshotVersion());
    assertEquals(0, event.getDocumentUpdates().size());
    assertEquals(1, event.getTargetChanges().size());

    TargetChange expected = targetChange(resumeToken, false, null, null, null);
    assertEquals(expected, event.getTargetChanges().get(1));
  }

  @Test
  public void testExistenceFilterMismatchClearsTarget() {
    Map<Integer, TargetData> targetMap = activeQueries(1, 2);

    Document doc1 = doc("docs/1", 1, map("value", 1));
    Document doc2 = doc("docs/2", 2, map("value", 2));

    WatchChange change1 = new DocumentChange(asList(1), emptyList(), doc1.getKey(), doc1);
    WatchChange change2 = new DocumentChange(asList(1), emptyList(), doc2.getKey(), doc2);
    WatchChange change3 = new WatchTargetChange(WatchTargetChangeType.Current, asList(1));

    WatchChangeAggregator aggregator =
        createAggregator(
            targetMap,
            noOutstandingResponses,
            keySet(doc1.getKey(), doc2.getKey()),
            change1,
            change2,
            change3);

    RemoteEvent event = aggregator.createRemoteEvent(version(3));

    assertEquals(version(3), event.getSnapshotVersion());
    assertEquals(2, event.getDocumentUpdates().size());
    assertEquals(doc1, event.getDocumentUpdates().get(doc1.getKey()));
    assertEquals(doc2, event.getDocumentUpdates().get(doc2.getKey()));

    assertEquals(2, event.getTargetChanges().size());

    TargetChange mapping1 = targetChange(resumeToken, true, null, asList(doc1, doc2), null);
    assertEquals(mapping1, event.getTargetChanges().get(1));

    TargetChange mapping2 = targetChange(resumeToken, false, null, null, null);
    assertEquals(mapping2, event.getTargetChanges().get(2));

    WatchChange.ExistenceFilterWatchChange watchChange =
        new WatchChange.ExistenceFilterWatchChange(1, new ExistenceFilter(1));
    aggregator.handleExistenceFilter(watchChange);

    event = aggregator.createRemoteEvent(version(3));

    TargetChange mapping3 = targetChange(ByteString.EMPTY, false, null, null, asList(doc1, doc2));
    assertEquals(1, event.getTargetChanges().size());
    assertEquals(mapping3, event.getTargetChanges().get(1));
    assertEquals(1, event.getTargetMismatches().size());
    assertEquals(0, event.getDocumentUpdates().size());
  }

  @Test
  public void testExistenceFilterMismatchRemovesCurrentChanges() {
    Map<Integer, TargetData> targetMap = activeQueries(1);

    WatchChangeAggregator aggregator =
        createAggregator(targetMap, noOutstandingResponses, noExistingKeys);
    WatchTargetChange markCurrent = new WatchTargetChange(WatchTargetChangeType.Current, asList(1));
    aggregator.handleTargetChange(markCurrent);

    Document doc1 = doc("docs/1", 1, map("value", 1));
    DocumentChange addDoc = new DocumentChange(asList(1), emptyList(), doc1.getKey(), doc1);
    aggregator.handleDocumentChange(addDoc);

    // The existence filter mismatch will remove the document from target 1, but not synthesize a
    // document delete.
    WatchChange.ExistenceFilterWatchChange existenceFilter =
        new WatchChange.ExistenceFilterWatchChange(1, new ExistenceFilter(0));
    aggregator.handleExistenceFilter(existenceFilter);

    RemoteEvent event = aggregator.createRemoteEvent(version(3));

    assertEquals(version(3), event.getSnapshotVersion());
    assertEquals(1, event.getDocumentUpdates().size());
    assertEquals(1, event.getTargetMismatches().size());
    assertEquals(doc1, event.getDocumentUpdates().get(doc1.getKey()));

    assertEquals(1, event.getTargetChanges().size());

    TargetChange mapping1 = targetChange(ByteString.EMPTY, false, null, null, null);
    assertEquals(mapping1, event.getTargetChanges().get(1));
  }

  @Test
  public void testDocumentUpdate() {
    Map<Integer, TargetData> targetMap = activeQueries(1);

    Document doc1 = doc("docs/1", 1, map("value", 1));
    WatchChange change1 = new DocumentChange(asList(1), emptyList(), doc1.getKey(), doc1);

    Document doc2 = doc("docs/2", 2, map("value", 2));
    WatchChange change2 = new DocumentChange(asList(1), emptyList(), doc2.getKey(), doc2);

    WatchChangeAggregator aggregator =
        createAggregator(targetMap, noOutstandingResponses, noExistingKeys, change1, change2);
    RemoteEvent event = aggregator.createRemoteEvent(version(3));
    assertEquals(version(3), event.getSnapshotVersion());
    assertEquals(2, event.getDocumentUpdates().size());
    assertEquals(doc1, event.getDocumentUpdates().get(doc1.getKey()));
    assertEquals(doc2, event.getDocumentUpdates().get(doc2.getKey()));

    targetMetadataProvider.setSyncedKeys(targetMap.get(1), keySet(doc1.getKey(), doc2.getKey()));

    NoDocument deletedDoc1 = deletedDoc("docs/1", 3);
    DocumentChange change3 =
        new DocumentChange(asList(1), emptyList(), deletedDoc1.getKey(), deletedDoc1);
    aggregator.handleDocumentChange(change3);

    Document updatedDoc2 = doc("docs/2", 3, map("value", 3));
    DocumentChange change4 =
        new DocumentChange(asList(1), emptyList(), updatedDoc2.getKey(), updatedDoc2);
    aggregator.handleDocumentChange(change4);

    Document doc3 = doc("docs/3", 3, map("value", 3));
    DocumentChange change5 = new DocumentChange(asList(1), emptyList(), doc3.getKey(), doc3);
    aggregator.handleDocumentChange(change5);

    event = aggregator.createRemoteEvent(version(3));

    assertEquals(version(3), event.getSnapshotVersion());
    assertEquals(3, event.getDocumentUpdates().size());
    // doc1 is replaced
    assertEquals(deletedDoc1, event.getDocumentUpdates().get(doc1.getKey()));
    // doc2 is updated
    assertEquals(updatedDoc2, event.getDocumentUpdates().get(doc2.getKey()));
    // doc3 is new
    assertEquals(doc3, event.getDocumentUpdates().get(doc3.getKey()));

    // Target is unchanged
    assertEquals(1, event.getTargetChanges().size());

    TargetChange mapping1 =
        targetChange(resumeToken, false, asList(doc3), asList(updatedDoc2), asList(deletedDoc1));
    assertEquals(mapping1, event.getTargetChanges().get(1));
  }

  @Test
  public void testResumeTokenHandledPerTarget() {
    Map<Integer, TargetData> targetMap = activeQueries(1, 2);

    WatchChangeAggregator aggregator =
        createAggregator(targetMap, noOutstandingResponses, noExistingKeys);

    WatchTargetChange change1 = new WatchTargetChange(WatchTargetChangeType.Current, asList(1));
    aggregator.handleTargetChange(change1);

    ByteString resumeToken2 = ByteString.copyFromUtf8("resumeToken2");
    WatchTargetChange change2 =
        new WatchTargetChange(WatchTargetChangeType.Current, asList(2), resumeToken2);
    aggregator.handleTargetChange(change2);

    RemoteEvent event = aggregator.createRemoteEvent(version(3));

    assertEquals(2, event.getTargetChanges().size());

    TargetChange mapping1 = targetChange(resumeToken, true, null, null, null);
    assertEquals(mapping1, event.getTargetChanges().get(1));

    TargetChange mapping2 = targetChange(resumeToken2, true, null, null, null);
    assertEquals(mapping2, event.getTargetChanges().get(2));
  }

  @Test
  public void testLastResumeTokenWins() {
    Map<Integer, TargetData> targetMap = activeQueries(1, 2);

    WatchChangeAggregator aggregator =
        createAggregator(targetMap, noOutstandingResponses, noExistingKeys);

    WatchTargetChange change1 = new WatchTargetChange(WatchTargetChangeType.Current, asList(1));
    aggregator.handleTargetChange(change1);

    ByteString resumeToken2 = ByteString.copyFromUtf8("resumeToken2");
    WatchTargetChange change2 =
        new WatchTargetChange(WatchTargetChangeType.Current, asList(1), resumeToken2);
    aggregator.handleTargetChange(change2);

    ByteString resumeToken3 = ByteString.copyFromUtf8("resumeToken3");
    WatchTargetChange change3 =
        new WatchTargetChange(WatchTargetChangeType.Current, asList(2), resumeToken3);
    aggregator.handleTargetChange(change3);

    RemoteEvent event = aggregator.createRemoteEvent(version(3));

    assertEquals(2, event.getTargetChanges().size());

    TargetChange mapping1 = targetChange(resumeToken2, true, null, null, null);
    assertEquals(mapping1, event.getTargetChanges().get(1));

    TargetChange mapping2 = targetChange(resumeToken3, true, null, null, null);
    assertEquals(mapping2, event.getTargetChanges().get(2));
  }

  @Test
  public void testSynthesizeDeletes() {
    Map<Integer, TargetData> targetMap = activeLimboQueries("foo/doc", 1);

    WatchTargetChange shouldSynthesize =
        new WatchTargetChange(WatchTargetChangeType.Current, asList(1));
    RemoteEvent event =
        createRemoteEvent(3, targetMap, noOutstandingResponses, noExistingKeys, shouldSynthesize);

    DocumentKey synthesized = key("docs/2");
    assertNull(event.getDocumentUpdates().get(synthesized));

    NoDocument expected = deletedDoc("foo/doc", 3);
    assertEquals(expected, event.getDocumentUpdates().get(expected.getKey()));
    assertTrue(event.getResolvedLimboDocuments().contains(expected.getKey()));
  }

  @Test
  public void testDoesNotSynthesizeDeleteInWrongState() {
    Map<Integer, TargetData> targetMap = activeLimboQueries("foo/doc", 1);

    WatchTargetChange wrongState = new WatchTargetChange(WatchTargetChangeType.NoChange, asList(1));

    RemoteEvent event =
        createRemoteEvent(3, targetMap, noOutstandingResponses, noExistingKeys, wrongState);
    assertEquals(0, event.getDocumentUpdates().size());
    assertEquals(0, event.getResolvedLimboDocuments().size());
  }

  @Test
  public void testDoesNotSynthesizeDeleteWithExistingDocument() {
    Map<Integer, TargetData> targetMap = activeLimboQueries("foo/doc", 1);

    WatchTargetChange hasDocument = new WatchTargetChange(WatchTargetChangeType.Current, asList(1));

    RemoteEvent event =
        createRemoteEvent(
            3, targetMap, noOutstandingResponses, keySet(key("foo/doc")), hasDocument);
    assertEquals(0, event.getDocumentUpdates().size());
    assertEquals(0, event.getResolvedLimboDocuments().size());
  }

  @Test
  public void testSeparatesUpdates() {
    Map<Integer, TargetData> targetMap = activeQueries(1);

    Document newDoc = doc("docs/new", 1, map("key", "value"));
    DocumentChange newDocChange =
        new DocumentChange(asList(1), emptyList(), newDoc.getKey(), newDoc);

    Document existingDoc = doc("docs/existing", 1, map("some", "data"));
    DocumentChange existingDocChange =
        new DocumentChange(asList(1), emptyList(), existingDoc.getKey(), existingDoc);

    NoDocument deletedDoc = deletedDoc("docs/deleted", 1);
    DocumentChange deletedDocChange =
        new DocumentChange(asList(1), emptyList(), deletedDoc.getKey(), deletedDoc);

    NoDocument missingDoc = deletedDoc("docs/missing  ", 1);
    DocumentChange missingDocChange =
        new DocumentChange(asList(1), emptyList(), missingDoc.getKey(), missingDoc);

    RemoteEvent event =
        createRemoteEvent(
            3,
            targetMap,
            noOutstandingResponses,
            keySet(existingDoc.getKey(), deletedDoc.getKey()),
            newDocChange,
            existingDocChange,
            deletedDocChange,
            missingDocChange);

    TargetChange mapping =
        targetChange(resumeToken, false, asList(newDoc), asList(existingDoc), asList(deletedDoc));
    assertEquals(mapping, event.getTargetChanges().get(1));
  }

  @Test
  public void testTracksLimboDocuments() {
    Map<Integer, TargetData> listens = activeQueries(1);
    listens.putAll(activeLimboQueries("doc/2", 2));

    // Add 3 docs: 1 is limbo and non-limbo, 2 is limbo-only, 3 is non-limbo
    Document doc1 = doc("docs/1", 1, map("key", "value"));
    Document doc2 = doc("docs/2", 1, map("key", "value"));
    Document doc3 = doc("docs/3", 1, map("key", "value"));

    // Target 2 is a limbo target
    DocumentChange docChange1 = new DocumentChange(asList(1, 2), emptyList(), doc1.getKey(), doc1);
    DocumentChange docChange2 = new DocumentChange(asList(2), emptyList(), doc2.getKey(), doc2);
    DocumentChange docChange3 = new DocumentChange(asList(1), emptyList(), doc3.getKey(), doc3);

    WatchTargetChange targetsChange =
        new WatchTargetChange(WatchTargetChangeType.Current, asList(1, 2));

    RemoteEvent event =
        createRemoteEvent(
            3,
            listens,
            noOutstandingResponses,
            noExistingKeys,
            docChange1,
            docChange2,
            docChange3,
            targetsChange);
    Set<DocumentKey> limboDocuments = event.getResolvedLimboDocuments();
    // Doc1 is in both limbo and non-limbo targets, therefore not tracked as limbo
    assertFalse(limboDocuments.contains(doc1.getKey()));
    // Doc2 is only in the limbo target, so is tracked as a limbo document
    assertTrue(limboDocuments.contains(doc2.getKey()));
    // Doc3 is only in the non-limbo target, therefore not tracked as limbo
    assertFalse(limboDocuments.contains(doc3.getKey()));
  }
}
