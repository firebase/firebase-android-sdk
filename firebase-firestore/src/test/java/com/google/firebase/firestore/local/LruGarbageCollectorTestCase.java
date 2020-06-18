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

import static com.google.firebase.firestore.testutil.TestUtil.doc;
import static com.google.firebase.firestore.testutil.TestUtil.keySet;
import static com.google.firebase.firestore.testutil.TestUtil.query;
import static com.google.firebase.firestore.testutil.TestUtil.resumeToken;
import static com.google.firebase.firestore.testutil.TestUtil.version;
import static com.google.firebase.firestore.testutil.TestUtil.wrapObject;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.util.SparseArray;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.auth.User;
import com.google.firebase.firestore.core.ListenSequence;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.ObjectValue;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.model.mutation.Mutation;
import com.google.firebase.firestore.model.mutation.Precondition;
import com.google.firebase.firestore.model.mutation.SetMutation;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public abstract class LruGarbageCollectorTestCase {
  private Persistence persistence;
  private TargetCache targetCache;
  private MutationQueue mutationQueue;
  private RemoteDocumentCache documentCache;
  private LruGarbageCollector garbageCollector;
  private LruGarbageCollector.Params lruParams;
  private int previousTargetId;
  private int previousDocNum;
  private long initialSequenceNumber;
  private ObjectValue testValue;

  abstract Persistence createPersistence(LruGarbageCollector.Params params);

  @Before
  public void setUp() {
    previousTargetId = 500;
    previousDocNum = 10;
    Map<String, Object> dataMap = new HashMap<>();
    dataMap.put("test", "data");
    dataMap.put("foo", true);
    dataMap.put("bar", 3);
    testValue = wrapObject(dataMap);

    newTestResources();
  }

  @After
  public void tearDown() {
    persistence.shutdown();
  }

  private void newTestResources() {
    newTestResources(LruGarbageCollector.Params.Default());
  }

  private void newTestResources(LruGarbageCollector.Params params) {
    persistence = createPersistence(params);
    persistence.getReferenceDelegate().setInMemoryPins(new ReferenceSet());
    targetCache = persistence.getTargetCache();
    documentCache = persistence.getRemoteDocumentCache();
    User user = new User("user");
    mutationQueue = persistence.getMutationQueue(user);
    initialSequenceNumber = targetCache.getHighestListenSequenceNumber();
    garbageCollector = ((LruDelegate) persistence.getReferenceDelegate()).getGarbageCollector();
    lruParams = params;
  }

  private TargetData nextTargetData() {
    int targetId = ++previousTargetId;
    long sequenceNumber = persistence.getReferenceDelegate().getCurrentSequenceNumber();
    Query query = query("path" + targetId);
    return new TargetData(query.toTarget(), targetId, sequenceNumber, QueryPurpose.LISTEN);
  }

  private void updateTargetInTransaction(TargetData targetData) {
    SnapshotVersion version = version(2);
    ByteString resumeToken = resumeToken(2);
    TargetData updated =
        targetData
            .withResumeToken(resumeToken, version)
            .withSequenceNumber(persistence.getReferenceDelegate().getCurrentSequenceNumber());
    targetCache.updateTargetData(updated);
  }

  private TargetData addNextQueryInTransaction() {
    TargetData targetData = nextTargetData();
    targetCache.addTargetData(targetData);
    return targetData;
  }

  private TargetData addNextQuery() {
    return persistence.runTransaction("Add query", this::addNextQueryInTransaction);
  }

  private DocumentKey nextTestDocumentKey() {
    return DocumentKey.fromPathString("docs/doc_" + ++previousDocNum);
  }

  private Document nextTestDocument() {
    DocumentKey key = nextTestDocumentKey();
    long version = 1;
    Map<String, Object> data = new HashMap<>();
    data.put("baz", true);
    data.put("ok", "fine");
    return doc(key, version, data);
  }

  private Document cacheADocumentInTransaction() {
    Document doc = nextTestDocument();
    documentCache.add(doc, doc.getVersion());
    return doc;
  }

  private void markDocumentEligibleForGcInTransaction(DocumentKey key) {
    persistence.getReferenceDelegate().removeMutationReference(key);
  }

  private void markDocumentEligibleForGc(DocumentKey key) {
    persistence.runTransaction(
        "Removing mutation reference", () -> markDocumentEligibleForGcInTransaction(key));
  }

  private void markADocumentEligibleForGc() {
    DocumentKey key = nextTestDocumentKey();
    markDocumentEligibleForGc(key);
  }

  private void markADocumentEligibleForGcInTransaction() {
    DocumentKey key = nextTestDocumentKey();
    markDocumentEligibleForGcInTransaction(key);
  }

  private void addDocumentToTarget(DocumentKey key, int targetId) {
    targetCache.addMatchingKeys(keySet(key), targetId);
  }

  private void removeDocumentFromTarget(DocumentKey key, int targetId) {
    targetCache.removeMatchingKeys(keySet(key), targetId);
  }

  private int removeTargets(long upperBound, SparseArray<?> activeTargetIds) {
    return persistence.runTransaction(
        "Remove queries", () -> garbageCollector.removeTargets(upperBound, activeTargetIds));
  }

  private SetMutation mutation(DocumentKey key) {
    return new SetMutation(key, testValue, Precondition.NONE);
  }

  @Test
  public void testPickSequenceNumberPercentile() {
    int[] queryCounts = new int[] {0, 10, 9, 50, 49};
    int[] expectedCounts = new int[] {0, 1, 0, 5, 4};

    for (int i = 0; i < queryCounts.length; i++) {
      int numQueries = queryCounts[i];
      int expectedTenthPercentile = expectedCounts[i];
      newTestResources();
      for (int j = 0; j < numQueries; j++) {
        addNextQuery();
      }
      int tenth = garbageCollector.calculateQueryCount(10);
      assertEquals(expectedTenthPercentile, tenth);
      persistence.shutdown();
      newTestResources();
    }
  }

  @Test
  public void testSequenceNumberNoQueries() {
    assertEquals(ListenSequence.INVALID, garbageCollector.getNthSequenceNumber(0));
  }

  @Test
  public void testSequenceNumberForFiftyQueries() {
    // Add 50 queries sequentially, aim to collect 10 of them.
    // The sequence number to collect should be 10 past the initial sequence number.
    for (int i = 0; i < 50; i++) {
      addNextQuery();
    }
    assertEquals(initialSequenceNumber + 10, garbageCollector.getNthSequenceNumber(10));
  }

  @Test
  public void testSequenceNumberForMultipleQueriesInATransaction() {
    // 50 queries, 9 with one transaction, incrementing from there. Should get second sequence
    // number.
    persistence.runTransaction(
        "9 queries in a batch",
        () -> {
          for (int i = 0; i < 9; i++) {
            addNextQueryInTransaction();
          }
        });
    for (int i = 9; i < 50; i++) {
      addNextQuery();
    }
    assertEquals(2 + initialSequenceNumber, garbageCollector.getNthSequenceNumber(10));
  }

  @Test
  public void testAllCollectedQueriesInSingleTransaction() {
    // Ensure that even if all of the queries are added in a single transaction, we still
    // pick a sequence number and GC. In this case, the initial transaction contains all of the
    // targets that will get GC'd, since they account for more than the first 10 targets.
    // 50 queries, 11 with one transaction, incrementing from there. Should get first sequence
    // number.
    persistence.runTransaction(
        "9 queries in a batch",
        () -> {
          for (int i = 0; i < 11; i++) {
            addNextQueryInTransaction();
          }
        });
    for (int i = 11; i < 50; i++) {
      addNextQuery();
    }
    assertEquals(1 + initialSequenceNumber, garbageCollector.getNthSequenceNumber(10));
  }

  @Test
  public void testSequenceNumbersWithMutationAndSequentialQueries() {
    // Remove a mutated doc reference, marking it as eligible for GC.
    // Then add 50 queries. Should get 10 past initial (9 queries).
    markADocumentEligibleForGc();
    for (int i = 0; i < 50; i++) {
      addNextQuery();
    }
    assertEquals(10 + initialSequenceNumber, garbageCollector.getNthSequenceNumber(10));
  }

  @Test
  public void testSequenceNumbersWithMutationsInQueries() {
    // Add mutated docs, then add one of them to a query target so it doesn't get GC'd.
    // Expect 3 past the initial value: the mutations not part of a query, and two queries
    DocumentKey docInQuery = nextTestDocumentKey();
    persistence.runTransaction(
        "mark mutations",
        () -> {
          // Adding 9 doc keys in a transaction. If we remove one of them, we'll have room for two
          // actual queries.
          markDocumentEligibleForGcInTransaction(docInQuery);
          for (int i = 0; i < 8; i++) {
            markADocumentEligibleForGcInTransaction();
          }
        });
    for (int i = 0; i < 49; i++) {
      addNextQuery();
    }
    persistence.runTransaction(
        "query with a mutation",
        () -> {
          TargetData targetData = addNextQueryInTransaction();
          addDocumentToTarget(docInQuery, targetData.getTargetId());
        });
    // This should catch the remaining 8 documents, plus the first two queries we added.
    assertEquals(3 + initialSequenceNumber, garbageCollector.getNthSequenceNumber(10));
  }

  @Test
  public void testRemoveQueriesUpThroughSequenceNumber() {
    SparseArray<TargetData> activeTargetIds = new SparseArray<>();
    for (int i = 0; i < 100; i++) {
      TargetData targetData = addNextQuery();
      // Mark odd queries as live so we can test filtering out live queries.
      int targetId = targetData.getTargetId();
      if (targetId % 2 == 1) {
        activeTargetIds.put(targetId, targetData);
      }
    }
    // GC up through 20th query, which is 20%.
    // Expect to have GC'd 10 targets, since every other target is live
    long upperBound = 20 + initialSequenceNumber;
    int removed = removeTargets(upperBound, activeTargetIds);
    assertEquals(10, removed);
    // Make sure we removed the even targets with targetID <= 20.
    persistence.runTransaction(
        "verify remaining targets are > 20 or odd",
        () ->
            targetCache.forEachTarget(
                (TargetData targetData) -> {
                  boolean isOdd = targetData.getTargetId() % 2 == 1;
                  boolean isOver20 = targetData.getTargetId() > 20;
                  assertTrue(isOdd || isOver20);
                }));
  }

  @Test
  public void testRemoveOrphanedDocuments() {
    // Track documents we expect to be retained so we can verify post-GC.
    // This will contain documents associated with targets that survive GC, as well
    // as any documents with pending mutations.
    Set<DocumentKey> expectedRetained = new HashSet<>();
    // we add two mutations later, for now track them in an array.
    List<Mutation> mutations = new ArrayList<>();

    persistence.runTransaction(
        "add a target and add two documents to it",
        () -> {
          // Add two documents to first target, queue a mutation on the second document
          TargetData targetData = addNextQueryInTransaction();
          Document doc1 = cacheADocumentInTransaction();
          addDocumentToTarget(doc1.getKey(), targetData.getTargetId());
          expectedRetained.add(doc1.getKey());

          Document doc2 = cacheADocumentInTransaction();
          addDocumentToTarget(doc2.getKey(), targetData.getTargetId());
          expectedRetained.add(doc2.getKey());
          mutations.add(mutation(doc2.getKey()));
        });

    // Add a second query and register a third document on it
    persistence.runTransaction(
        "second query",
        () -> {
          TargetData targetData = addNextQueryInTransaction();
          Document doc3 = cacheADocumentInTransaction();
          addDocumentToTarget(doc3.getKey(), targetData.getTargetId());
          expectedRetained.add(doc3.getKey());
        });

    // cache another document and prepare a mutation on it.
    persistence.runTransaction(
        "queue a mutation",
        () -> {
          Document doc4 = cacheADocumentInTransaction();
          mutations.add(mutation(doc4.getKey()));
          expectedRetained.add(doc4.getKey());
        });

    // Insert the mutations. These operations don't have a sequence number, they just
    // serve to keep the mutated documents from being GC'd while the mutations are outstanding.
    persistence.runTransaction(
        "actually register the mutations",
        () -> {
          Timestamp writeTime = Timestamp.now();
          mutationQueue.addMutationBatch(writeTime, Collections.emptyList(), mutations);
        });

    // Mark 5 documents eligible for GC. This simulates documents that were mutated then ack'd.
    // Since they were ack'd, they are no longer in a mutation queue, and there is nothing keeping
    // them alive.
    Set<DocumentKey> toBeRemoved = new HashSet<>();
    persistence.runTransaction(
        "add orphaned docs (previously mutated, then ack'd)",
        () -> {
          for (int i = 0; i < 5; i++) {
            Document doc = cacheADocumentInTransaction();
            toBeRemoved.add(doc.getKey());
            markDocumentEligibleForGcInTransaction(doc.getKey());
          }
        });

    // We expect only the orphaned documents, those not in a mutation or a target, to be removed.
    // use a large sequence number to remove as much as possible
    int removed = garbageCollector.removeOrphanedDocuments(1000);
    assertEquals(toBeRemoved.size(), removed);
    persistence.runTransaction(
        "verify",
        () -> {
          for (DocumentKey key : toBeRemoved) {
            assertNull(documentCache.get(key));
            assertFalse(targetCache.containsKey(key));
          }
          for (DocumentKey key : expectedRetained) {
            assertNotNull(documentCache.get(key));
          }
        });
  }

  @Test
  public void testRemoveOrphanedDocumentsWithNoDocuments() {
    int removed = garbageCollector.removeOrphanedDocuments(1000);
    assertEquals(0, removed);
  }

  @Test
  public void testRemoveOrphanedDocumentsWithLargeNumberOfDocuments() {
    int orphanedDocumentCount =
        SQLiteLruReferenceDelegate.REMOVE_ORPHANED_DOCUMENTS_BATCH_SIZE * 2 + 1;

    persistence.runTransaction(
        "add orphaned docs",
        () -> {
          for (int i = 0; i < orphanedDocumentCount; i++) {
            Document doc = cacheADocumentInTransaction();
            markDocumentEligibleForGcInTransaction(doc.getKey());
          }
        });

    int removed = garbageCollector.removeOrphanedDocuments(1000);
    assertEquals(orphanedDocumentCount, removed);
  }

  @Test
  public void testRemoveTargetsThenGC() {
    // Create 3 targets, add docs to all of them
    // Leave oldest target alone, it is still live
    // Remove newest target
    // Blind write 2 documents
    // Add one of the blind write docs to oldest target (preserves it)
    // Remove some documents from middle target (bumps sequence number)
    // Add some documents from newest target to oldest target (preserves them)
    // Update a doc from middle target
    // Remove middle target
    // Do a blind write
    // GC up to but not including the removal of the middle target
    //
    // Expect:
    // All docs in oldest target are still around
    // One blind write is gone, the first one not added to oldest target
    // Documents removed from middle target are gone, except ones added to oldest target
    // Documents from newest target are gone, except those added to the old target as well

    // Through the various steps, track which documents we expect to be removed vs
    // documents we expect to be retained.
    Set<DocumentKey> expectedRetained = new HashSet<>();
    Set<DocumentKey> expectedRemoved = new HashSet<>();

    // Add oldest target, 5 documents, and add those documents to the target.
    // This target will not be removed, so all documents that are part of it will
    // be retained.
    TargetData oldestTarget =
        persistence.runTransaction(
            "Add oldest target and docs",
            () -> {
              TargetData targetData = addNextQueryInTransaction();
              for (int i = 0; i < 5; i++) {
                Document doc = cacheADocumentInTransaction();
                expectedRetained.add(doc.getKey());
                addDocumentToTarget(doc.getKey(), targetData.getTargetId());
              }
              return targetData;
            });

    // Add middle target and docs. Some docs will be removed from this target later,
    // which we track here.
    Set<DocumentKey> middleDocsToRemove = new HashSet<>();
    // This will be the document in this target that gets an update later.
    DocumentKey[] middleDocToUpdateHolder = new DocumentKey[1];
    TargetData middleTarget =
        persistence.runTransaction(
            "Add middle target and docs",
            () -> {
              TargetData targetData = addNextQueryInTransaction();
              // these docs will be removed from this target later, triggering a bump
              // to their sequence numbers. Since they will not be a part of the target, we
              // expect them to be removed.
              for (int i = 0; i < 2; i++) {
                Document doc = cacheADocumentInTransaction();
                addDocumentToTarget(doc.getKey(), targetData.getTargetId());
                expectedRemoved.add(doc.getKey());
                middleDocsToRemove.add(doc.getKey());
              }
              // these docs stay in this target and only this target. There presence in this
              // target prevents them from being GC'd, so they are also expected to be retained.
              for (int i = 2; i < 4; i++) {
                Document doc = cacheADocumentInTransaction();
                expectedRetained.add(doc.getKey());
                addDocumentToTarget(doc.getKey(), targetData.getTargetId());
              }
              // This doc stays in this target, but gets updated.
              {
                Document doc = cacheADocumentInTransaction();
                expectedRetained.add(doc.getKey());
                addDocumentToTarget(doc.getKey(), targetData.getTargetId());
                middleDocToUpdateHolder[0] = doc.getKey();
              }
              return targetData;
            });
    DocumentKey middleDocToUpdate = middleDocToUpdateHolder[0];

    // Add the newest target and add 5 documents to it. Some of those documents will
    // additionally be added to the oldest target, which will cause those documents to
    // be retained. The remaining documents are expected to be removed, since this target
    // will be removed.
    Set<DocumentKey> newestDocsToAddToOldest = new HashSet<>();
    persistence.runTransaction(
        "Add newest target and docs",
        () -> {
          TargetData targetData = addNextQueryInTransaction();
          // These documents are only in this target. They are expected to be removed
          // because this target will also be removed.
          for (int i = 0; i < 3; i++) {
            Document doc = cacheADocumentInTransaction();
            expectedRemoved.add(doc.getKey());
            addDocumentToTarget(doc.getKey(), targetData.getTargetId());
          }
          // docs to add to the oldest target in addition to this target. They will be retained
          for (int i = 3; i < 5; i++) {
            Document doc = cacheADocumentInTransaction();
            expectedRetained.add(doc.getKey());
            addDocumentToTarget(doc.getKey(), targetData.getTargetId());
            newestDocsToAddToOldest.add(doc.getKey());
          }
        });

    // 2 doc writes, add one of them to the oldest target.
    persistence.runTransaction(
        "2 doc writes, add one of them to the oldest target",
        () -> {
          // write two docs and have them ack'd by the server. can skip mutation queue
          // and set them in document cache. Add potentially orphaned first, also add one
          // doc to a target.
          Document doc1 = cacheADocumentInTransaction();
          markDocumentEligibleForGcInTransaction(doc1.getKey());
          updateTargetInTransaction(oldestTarget);
          addDocumentToTarget(doc1.getKey(), oldestTarget.getTargetId());
          // doc1 should be retained by being added to oldestTarget
          expectedRetained.add(doc1.getKey());

          Document doc2 = cacheADocumentInTransaction();
          markDocumentEligibleForGcInTransaction(doc2.getKey());
          // nothing is keeping doc2 around, it should be removed
          expectedRemoved.add(doc2.getKey());
        });

    // Remove some documents from the middle target.
    persistence.runTransaction(
        "Remove some documents from the middle target",
        () -> {
          updateTargetInTransaction(middleTarget);
          for (DocumentKey key : middleDocsToRemove) {
            removeDocumentFromTarget(key, middleTarget.getTargetId());
          }
        });

    // Add a couple docs from the newest target to the oldest (preserves them past the point where
    // newest was removed)
    // upperBound is the sequence number right before middleTarget is updated, then removed.
    long upperBound =
        persistence.runTransaction(
            "Add a couple docs from the newest target to the oldest",
            () -> {
              updateTargetInTransaction(oldestTarget);
              for (DocumentKey key : newestDocsToAddToOldest) {
                addDocumentToTarget(key, oldestTarget.getTargetId());
              }
              return persistence.getReferenceDelegate().getCurrentSequenceNumber();
            });

    // Update a doc in the middle target
    persistence.runTransaction(
        "Update a doc in the middle target",
        () -> {
          SnapshotVersion newVersion = version(3);
          Document doc =
              new Document(middleDocToUpdate, newVersion, testValue, Document.DocumentState.SYNCED);
          documentCache.add(doc, newVersion);
          updateTargetInTransaction(middleTarget);
        });

    // Remove the middle target
    persistence.runTransaction(
        "remove middle target",
        () -> persistence.getReferenceDelegate().removeTarget(middleTarget));

    // Write a doc and get an ack, not part of a target
    persistence.runTransaction(
        "Write a doc and get an ack, not part of a target",
        () -> {
          Document doc = cacheADocumentInTransaction();
          // Mark it as eligible for GC, but this is after our upper bound for what we will collect.
          markDocumentEligibleForGcInTransaction(doc.getKey());
          // This should be retained, it's too new to get removed.
          expectedRetained.add(doc.getKey());
        });

    // Finally, do the garbage collection, up to but not including the removal of middleTarget
    SparseArray<TargetData> activeTargetIds = new SparseArray<>();
    activeTargetIds.put(oldestTarget.getTargetId(), oldestTarget);
    int targetsRemoved = garbageCollector.removeTargets(upperBound, activeTargetIds);
    // Expect to remove newest target
    assertEquals(1, targetsRemoved);
    int docsRemoved = garbageCollector.removeOrphanedDocuments(upperBound);
    assertEquals(expectedRemoved.size(), docsRemoved);
    persistence.runTransaction(
        "verify results",
        () -> {
          for (DocumentKey key : expectedRemoved) {
            assertNull(documentCache.get(key));
            assertFalse(targetCache.containsKey(key));
          }
          for (DocumentKey key : expectedRetained) {
            assertNotNull(documentCache.get(key));
          }
        });
  }

  @Test
  public void testGetsSize() {
    long initialSize = garbageCollector.getByteSize();

    persistence.runTransaction(
        "fill cache",
        () -> {
          // Simulate a bunch of ack'd mutations
          for (int i = 0; i < 50; i++) {
            Document doc = cacheADocumentInTransaction();
            markDocumentEligibleForGcInTransaction(doc.getKey());
          }
        });

    long finalSize = garbageCollector.getByteSize();
    assertTrue(finalSize > initialSize);
  }

  @Test
  public void testDisabled() {
    LruGarbageCollector.Params params = LruGarbageCollector.Params.Disabled();

    // Switch out the test resources for ones with a disabled GC.
    persistence.shutdown();
    newTestResources(params);

    persistence.runTransaction(
        "Fill cache",
        () -> {
          // Simulate a bunch of ack'd mutations
          for (int i = 0; i < 500; i++) {
            Document doc = cacheADocumentInTransaction();
            markDocumentEligibleForGcInTransaction(doc.getKey());
          }
        });

    LruGarbageCollector.Results results =
        persistence.runTransaction("GC", () -> garbageCollector.collect(new SparseArray<>()));

    assertFalse(results.hasRun());
  }

  @Test
  public void testCacheTooSmall() {
    // Default LRU Params are ok for this test.

    persistence.runTransaction(
        "Fill cache",
        () -> {
          // Simulate a bunch of ack'd mutations
          for (int i = 0; i < 50; i++) {
            Document doc = cacheADocumentInTransaction();
            markDocumentEligibleForGcInTransaction(doc.getKey());
          }
        });

    // Make sure we're under the target size
    long cacheSize = garbageCollector.getByteSize();
    assertTrue(cacheSize < lruParams.minBytesThreshold);

    LruGarbageCollector.Results results =
        persistence.runTransaction("GC", () -> garbageCollector.collect(new SparseArray<>()));

    assertFalse(results.hasRun());
  }

  @Test
  public void testGCRan() {
    // Set a low byte threshold so we can guarantee that GC will run.
    LruGarbageCollector.Params params = LruGarbageCollector.Params.WithCacheSizeBytes(100);

    // Switch to persistence using our new params.
    persistence.shutdown();
    newTestResources(params);

    // Add 100 targets and 10 documents to each
    for (int i = 0; i < 100; i++) {
      // Use separate transactions so that each target and associated documents get their own
      // sequence number.
      persistence.runTransaction(
          "Add a target and some documents",
          () -> {
            TargetData targetData = addNextQueryInTransaction();
            for (int j = 0; j < 10; j++) {
              Document doc = cacheADocumentInTransaction();
              addDocumentToTarget(doc.getKey(), targetData.getTargetId());
            }
          });
    }

    // Mark nothing as live, so everything is eligible.
    LruGarbageCollector.Results results =
        persistence.runTransaction("GC", () -> garbageCollector.collect(new SparseArray<>()));

    // By default, we collect 10% of the sequence numbers. Since we added 100 targets,
    // that should be 10 targets with 10 documents each, for a total of 100 documents.
    assertTrue(results.hasRun());
    assertEquals(10, results.getTargetsRemoved());
    assertEquals(100, results.getDocumentsRemoved());
  }
}
