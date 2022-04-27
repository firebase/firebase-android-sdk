// Copyright 2021 Google LLC
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

package com.google.firebase.firestore.conformance;

import static com.google.common.truth.Truth.assertThat;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.common.truth.Correspondence;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.WriteBatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A runtime for performing Firestore conformance tests on Android.
 *
 * <p>This class contains all logic for a test run. Test cases only need to supply data (and this
 * should be done by the test code generator).
 */
public final class ConformanceRuntime {
  // Note: This code is copied from Google3.

  private static final Logger logger = Logger.getLogger("ConformanceRuntime");

  // Compares DocumentSnapshots to TestDocuments.
  private static final Correspondence<DocumentSnapshot, TestDocument> CHECKER =
      Correspondence.from(ConformanceRuntime::compareTest, "is equal to");

  // Timeouts.
  private static final long QUERY_TIMEOUT_MS = 1000;
  private static final long BATCH_TIMEOUT_MS = 1000;

  private final FirebaseFirestore firestore;
  private final Source source;

  // Random document path to isolate test runs.
  private final String testDocument = newRandomDocumentPath();

  // Initial and expected data for running the test.
  private final ArrayList<TestCollection> initialData = new ArrayList<>();
  private final ArrayList<TestDocument> expectedData = new ArrayList<>();

  // Set by expectException() if the query is invalid and an exception should be thrown.
  private boolean expectException = false;

  public ConformanceRuntime(FirebaseFirestore firestore, Source source) {
    this.firestore = firestore;
    this.source = source;
  }

  public TestDocument addExpectedDocumentWithId(String id) {
    TestDocument document = new TestDocument(id);
    expectedData.add(document);
    return document;
  }

  public TestCollection addInitialCollectionWithPath(String path) {
    TestCollection collection = new TestCollection(path);
    initialData.add(collection);
    return collection;
  }

  /**
   * Rethrows the exception unless an exception is expected.
   *
   * @param t a caught RuntimeException or AssertionError. Other Throwables are not supported.
   */
  public void checkQueryError(Throwable t) {
    if (expectException) {
      logger.log(Level.INFO, "Caught exception as expected", t);
      return;
    }

    // Rethrow as unchecked instead of checked Throwable.
    if (t instanceof AssertionError) {
      throw (AssertionError) t;
    } else {
      throw (RuntimeException) t;
    }
  }

  public Query createQueryAtPath(String path) {
    return firestore.document(testDocument).collection(path);
  }

  public void expectException() {
    expectException = true;
  }

  /** Initializes the database, runs the query, removes the documents, and verifies the results. */
  public void runQuery(Query query) throws InterruptedException, TimeoutException {
    logger.info("Performing query");
    Task<QuerySnapshot> snapshotTask = query.get(source);

    List<DocumentSnapshot> results = waitForTask(snapshotTask, QUERY_TIMEOUT_MS).getDocuments();
    assertThat(results).comparingElementsUsing(CHECKER).containsExactlyElementsIn(expectedData);
  }

  /** Initializes the database with the initial data. */
  public void setup() throws InterruptedException, TimeoutException {
    WriteBatch batch = firestore.batch();

    for (TestCollection collection : initialData) {
      CollectionReference ref = firestore.document(testDocument).collection(collection.path());
      for (TestDocument document : collection.documents()) {
        DocumentReference docRef = ref.document(document.id());
        batch.set(docRef, document.fields());

        logger.fine("Initializing document: " + docRef.getPath());
      }
    }

    waitForBatch(batch);
  }

  /** Cleans up the database by removing the initialized data. */
  public void teardown() throws InterruptedException, TimeoutException {
    WriteBatch batch = firestore.batch();

    for (TestCollection collection : initialData) {
      CollectionReference ref = firestore.document(testDocument).collection(collection.path());
      for (TestDocument document : collection.documents()) {
        DocumentReference docRef = ref.document(document.id());
        batch.delete(docRef);

        logger.info("Removing document: " + docRef.getPath());
      }
    }

    waitForBatch(batch);
  }

  /** Returns a new random document path for isolating test runs. */
  private static String newRandomDocumentPath() {
    StringBuilder builder = new StringBuilder(28).append("qct_run/");
    Random random = new Random();

    for (int i = 0; i < 20; ++i) {
      int next = random.nextInt(62);

      if (next < 10) {
        next += '0'; // 0-9 => 0-9
      } else if (next < 36) {
        next += 'a' - 10; // 10-35 => a-z
      } else {
        next += 'A' - 36; // 36-61 => A-Z
      }

      builder.append((char) next);
    }

    return builder.toString();
  }

  /**
   * Commits the batch and waits for it to complete normally.
   *
   * <p>If the source if {@link Source#CACHE} this method returns immediately. Otherwise, this
   * method delegates to {@link #waitForTask}.
   */
  private void waitForBatch(WriteBatch batch) throws InterruptedException, TimeoutException {
    // Batches don't complete until after confirmation is received from the server. The cache is
    // updated and queried in order, so there's no need to wait for cache-only tests.
    if (source.equals(Source.CACHE)) {
      batch.commit();
      return;
    }

    waitForTask(batch.commit(), BATCH_TIMEOUT_MS);
  }

  /**
   * Waits for the task to complete normally and returns the result.
   *
   * <p>This method throws an {@link AssertionError} if the task fails or doesn't complete within
   * the time limit.
   */
  private <T> T waitForTask(Task<T> task, long timeout)
      throws InterruptedException, TimeoutException {
    try {
      return Tasks.await(task, timeout, TimeUnit.MILLISECONDS);
    } catch (ExecutionException x) {
      throw new AssertionError(x.getCause());
    }
  }

  private static boolean compareTest(DocumentSnapshot snapshot, TestDocument document) {
    boolean nameOk = document.id().equals(snapshot.getId());
    return nameOk && document.fields().equals(snapshot.getData());
  }
}
