package com.google.firebase.firestore.local;

import static com.google.firebase.firestore.testutil.TestUtil.doc;
import static com.google.firebase.firestore.testutil.TestUtil.docMap;
import static com.google.firebase.firestore.testutil.TestUtil.filter;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.testutil.TestUtil.patchMutation;
import static com.google.firebase.firestore.testutil.TestUtil.query;
import static org.junit.Assert.assertEquals;

import com.google.android.gms.common.internal.Preconditions;
import com.google.firebase.Timestamp;
import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.database.collection.ImmutableSortedSet;
import com.google.firebase.firestore.auth.User;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.core.View;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.DocumentSet;
import com.google.firebase.firestore.model.FieldIndex;
import com.google.firebase.firestore.model.MutableDocument;
import com.google.firebase.firestore.model.mutation.Mutation;
import com.google.firebase.firestore.model.mutation.MutationBatch;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class AutoIndexingExperiment {
  static List<Object> values =
      Arrays.asList(
          "Hello world",
          46239847,
          -1984092375,
          Arrays.asList(1, "foo", 3, 5, 8, 10, 11),
          Arrays.asList(1, "foo", 9, 5, 8),
          Double.NaN,
          map("nested", "random"));

  private Persistence persistence;
  private RemoteDocumentCache remoteDocumentCache;
  private MutationQueue mutationQueue;
  private DocumentOverlayCache documentOverlayCache;

  protected IndexManager indexManager;
  protected QueryEngine queryEngine;

  private @Nullable Boolean expectFullCollectionScan;

  @Before
  public void setUp() {
    expectFullCollectionScan = null;

    persistence = PersistenceTestHelpers.createSQLitePersistence();

    indexManager = persistence.getIndexManager(User.UNAUTHENTICATED);
    mutationQueue = persistence.getMutationQueue(User.UNAUTHENTICATED, indexManager);
    documentOverlayCache = persistence.getDocumentOverlayCache(User.UNAUTHENTICATED);
    remoteDocumentCache = persistence.getRemoteDocumentCache();
    queryEngine = new QueryEngine();

    indexManager.start();
    mutationQueue.start();

    remoteDocumentCache.setIndexManager(indexManager);

    LocalDocumentsView localDocuments =
        new LocalDocumentsView(
            remoteDocumentCache, mutationQueue, documentOverlayCache, indexManager) {
          @Override
          public ImmutableSortedMap<DocumentKey, Document> getDocumentsMatchingQuery(
              Query query, FieldIndex.IndexOffset offset) {
            assertEquals(
                "Observed query execution mode did not match expectation",
                expectFullCollectionScan,
                FieldIndex.IndexOffset.NONE.equals(offset));
            return super.getDocumentsMatchingQuery(query, offset);
          }
        };
    queryEngine.initialize(localDocuments, indexManager, false);
  }

  /** Adds the provided documents to the remote document cache. */
  protected void addDocument(MutableDocument... docs) {
    persistence.runTransaction(
        "addDocument",
        () -> {
          for (MutableDocument doc : docs) {
            remoteDocumentCache.add(doc, doc.getVersion());
          }
        });
  }

  protected void addMutation(Mutation mutation) {
    persistence.runTransaction(
        "addMutation",
        () -> {
          MutationBatch batch =
              mutationQueue.addMutationBatch(
                  Timestamp.now(), Collections.emptyList(), Collections.singletonList(mutation));
          Map<DocumentKey, Mutation> overlayMap =
              Collections.singletonMap(mutation.getKey(), mutation);
          documentOverlayCache.saveOverlays(batch.getBatchId(), overlayMap);
        });
  }

  protected <T> T expectOptimizedCollectionScan(Callable<T> c) throws Exception {
    try {
      expectFullCollectionScan = false;
      return c.call();
    } finally {
      expectFullCollectionScan = null;
    }
  }

  private <T> T expectFullCollectionScan(Callable<T> c) throws Exception {
    try {
      expectFullCollectionScan = true;
      return c.call();
    } finally {
      expectFullCollectionScan = null;
    }
  }

  protected DocumentSet runQuery(Query query, boolean autoIndexing, QueryContext counter) {
    Preconditions.checkNotNull(
        expectFullCollectionScan,
        "Encountered runQuery() call not wrapped in expectOptimizedCollectionQuery()/expectFullCollectionQuery()");
    ImmutableSortedMap<DocumentKey, Document> docs =
        queryEngine.getDocumentsMatchingQueryTest(query, autoIndexing, counter);
    View view =
        new View(query, new ImmutableSortedSet<>(Collections.emptyList(), DocumentKey::compareTo));
    View.DocumentChanges viewDocChanges = view.computeDocChanges(docs);
    return view.applyChanges(viewDocChanges).getSnapshot().getDocuments();
  }

  private void createTestingDocument(
      String basePath, int documentID, boolean isMatched, int numOfFields) {
    Map<String, Object> fields = map("match", isMatched);

    // Randomly generate the rest of fields
    for (int i = 2; i <= numOfFields; i++) {
      int valueIndex = (int) (Math.random() * values.size()) % values.size();
      fields.put("field" + i, values.get(valueIndex));
    }

    MutableDocument doc = doc(basePath + "/" + documentID, 1, fields);
    addDocument(doc);

    indexManager.updateIndexEntries(docMap(doc));
    indexManager.updateCollectionGroup(basePath, FieldIndex.IndexOffset.fromDocument(doc));
  }

  private void createTestingCollection(
      String basePath, int totalSetCount, int portion /*0 - 10*/, int numOfFields /* 1 - 30*/) {
    int documentCounter = 0;
    for (int i = 1; i <= totalSetCount; i++) {
      // Generate a random order list of 0 ... 9
      ArrayList<Integer> indexes = new ArrayList<>();
      for (int index = 0; index < 10; index++) {
        indexes.add(index);
      }
      Collections.shuffle(indexes);

      for (int match = 0; match < portion; match++) {
        int currentID = documentCounter + indexes.get(match);
        createTestingDocument(basePath, currentID, true, numOfFields);
      }
      for (int unmatch = portion; unmatch < 10; unmatch++) {
        int currentID = documentCounter + indexes.get(unmatch);
        createTestingDocument(basePath, currentID, false, numOfFields);
      }
      documentCounter += 10;
    }
  }

  private void createMutationForCollection(String basePath, int totalSetCount) {
    ArrayList<Integer> indexes = new ArrayList<>();
    for (int index = 0; index < totalSetCount * 10; index++) {
      indexes.add(index);
    }
    Collections.shuffle(indexes);

    for (int i = 0; i < totalSetCount; i++) {
      addMutation(patchMutation(basePath + "/" + indexes.get(i), map("a", 5)));
    }
  }

  @Test
  public void testCombinesIndexedWithNonIndexedResults() throws Exception {
    // Every set contains 10 documents
    final int numOfSet = 100;
    // could overflow. Currently it is safe when numOfSet set to 1000 and running on macbook M1
    long totalBeforeIndex = 0;
    long totalAfterIndex = 0;
    long totalDocumentCount = 0;
    long totalResultCount = 0;

    // Temperate heuristic
    double without = 3.7;
    double with = 4.9;

    for (int totalSetCount = 1; totalSetCount <= numOfSet; totalSetCount *= 10) {
      // portion stands for the percentage of documents matching query
      for (int portion = 0; portion <= 10; portion++) {
        for (int numOfFields = 1; numOfFields <= 31; numOfFields += 10) {
          String basePath = "documentCount" + totalSetCount;
          // Auto indexing
          Query query = query(basePath).filter(filter("match", "==", true));
          indexManager.createTargetIndices(query.toTarget());
          createTestingCollection(basePath, totalSetCount, portion, numOfFields);
          createMutationForCollection(basePath, totalSetCount);

          QueryContext counterWithoutIndex = new QueryContext();
          long beforeAutoStart = System.nanoTime();
          DocumentSet results =
              expectFullCollectionScan(() -> runQuery(query, false, counterWithoutIndex));
          long beforeAutoEnd = System.nanoTime();
          long millisecondsBeforeAuto =
              TimeUnit.MILLISECONDS.convert(
                  (beforeAutoEnd - beforeAutoStart), TimeUnit.NANOSECONDS);
          totalBeforeIndex += (beforeAutoEnd - beforeAutoStart);
          totalDocumentCount += counterWithoutIndex.fullScanCount;
          assertEquals(portion * totalSetCount, results.size());

          QueryContext counterWithIndex = new QueryContext();
          long autoStart = System.nanoTime();
          results = expectOptimizedCollectionScan(() -> runQuery(query, true, counterWithIndex));
          long autoEnd = System.nanoTime();
          long millisecondsAfterAuto =
              TimeUnit.MILLISECONDS.convert((autoEnd - autoStart), TimeUnit.NANOSECONDS);
          totalAfterIndex += (autoEnd - autoStart);
          assertEquals(portion * totalSetCount, results.size());
          totalResultCount += results.size();
          if (millisecondsBeforeAuto > millisecondsAfterAuto) {
            System.out.println(
                "Auto Indexing saves time when total of documents inside collection is "
                    + totalSetCount * 10
                    + ". The matching percentage is "
                    + portion
                    + "0%. And each document contains "
                    + numOfFields
                    + " fields.\n"
                    + "Weight result for without auto indexing is "
                    + without * counterWithoutIndex.fullScanCount
                    + ". And weight result for auto indexing is "
                    + with * results.size());
          }
        }
      }
    }

    System.out.println(
        "The time heuristic is "
            + (totalBeforeIndex / totalDocumentCount)
            + " before auto indexing");
    System.out.println(
        "The time heuristic is " + (totalAfterIndex / totalResultCount) + " after auto indexing");
  }
}
