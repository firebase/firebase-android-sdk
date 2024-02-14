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

package com.google.firebase.firestore;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.querySnapshotToValues;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testFirestore;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.RuntimeExecutionException;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.testutil.EventAccumulator;
import com.google.firebase.firestore.testutil.IntegrationTestUtil;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class BundleTest {
  private static Charset UTF8_CHARSET = Charset.forName("UTF-8");
  private static String[] BUNDLE_TEMPLATES =
      new String[] {
        "{\"metadata\":{\"id\":\"test-bundle\",\"createTime\":{\"seconds\":1001,\"nanos\":9999},"
            + "\"version\":1,\"totalDocuments\":2,\"totalBytes\":{totalBytes}}}",
        "{\"namedQuery\":{\"name\":\"limit\",\"readTime\":{\"seconds\":1000,\"nanos\":9999},"
            + "\"bundledQuery\":{\"parent\":\"projects/{projectId}/databases/(default)/documents\","
            + "\"structuredQuery\":{\"from\":[{\"collectionId\":\"coll-1\"}],\"orderBy\":"
            + "[{\"field\":{\"fieldPath\":\"bar\"},\"direction\":\"DESCENDING\"},{\"field\":"
            + "{\"fieldPath\":\"__name__\"},\"direction\":\"DESCENDING\"}],\"limit\":"
            + "{\"value\":1}},\"limitType\":\"FIRST\"}}}",
        "{\"namedQuery\":{\"name\":\"limit-to-last\",\"readTime\":{\"seconds\":1000,\"nanos\":9999},"
            + "\"bundledQuery\":{\"parent\":\"projects/{projectId}/databases/(default)/documents\","
            + "\"structuredQuery\":{\"from\":[{\"collectionId\":\"coll-1\"}],\"orderBy\":"
            + "[{\"field\":{\"fieldPath\":\"bar\"},\"direction\":\"DESCENDING\"},{\"field\":"
            + "{\"fieldPath\":\"__name__\"},\"direction\":\"DESCENDING\"}],\"limit\":"
            + "{\"value\":1}},\"limitType\":\"LAST\"}}}",
        "{\"documentMetadata\":{\"name\":"
            + "\"projects/{projectId}/databases/(default)/documents/coll-1/a\",\"readTime\":"
            + "{\"seconds\":1000,\"nanos\":9999},\"exists\":true}}",
        "{\"document\":{\"name\":\"projects/{projectId}/databases/(default)/documents/coll-1/a\","
            + "\"createTime\":{\"seconds\":1,\"nanos\":9},\"updateTime\":{\"seconds\":1,"
            + "\"nanos\":9},\"fields\":{\"k\":{\"stringValue\":\"a\"},\"bar\":"
            + "{\"integerValue\":1}}}}",
        "{\"documentMetadata\":{\"name\":"
            + "\"projects/{projectId}/databases/(default)/documents/coll-1/b\",\"readTime\":"
            + "{\"seconds\":1000,\"nanos\":9999},\"exists\":true}}",
        "{\"document\":{\"name\":\"projects/{projectId}/databases/(default)/documents/coll-1/b\","
            + "\"createTime\":{\"seconds\":1,\"nanos\":9},\"updateTime\":{\"seconds\":1,"
            + "\"nanos\":9},\"fields\":{\"k\":{\"stringValue\":\"\uD83D\uDE0A\"},\"bar\":"
            + "{\"integerValue\":2}}}}"
      };

  private FirebaseFirestore db;

  @Before
  public void setUp() {
    db = testFirestore();
  }

  @After
  public void tearDown() {
    IntegrationTestUtil.tearDown();
  }

  @Test
  public void testLoadDocumentsWithProgressUpdates() throws Exception {
    List<LoadBundleTaskProgress> progressEvents = new ArrayList<>();

    byte[] bundle = createBundle();
    LoadBundleTask bundleTask = db.loadBundle(bundle);
    bundleTask.addOnProgressListener(progressEvents::add);

    LoadBundleTaskProgress result = awaitCompletion(bundleTask);

    assertEquals(4, progressEvents.size());
    verifyProgress(progressEvents.get(0), 0);
    verifyProgress(progressEvents.get(1), 1);
    verifyProgress(progressEvents.get(2), 2);
    verifySuccessProgress(progressEvents.get(3));
    assertEquals(progressEvents.get(3), result);

    verifyQueryResults();
  }

  @Test
  public void testLoadForASecondTimeSkips() throws Exception {
    byte[] bundle = createBundle();
    LoadBundleTask initialLoad = db.loadBundle(bundle);
    Tasks.await(initialLoad);

    List<LoadBundleTaskProgress> progressEvents = new ArrayList<>();
    LoadBundleTask secondLoad = db.loadBundle(bundle);
    secondLoad.addOnProgressListener(progressEvents::add);
    awaitCompletion(secondLoad);

    // No loading actually happened in the second `loadBundle` call only the success progress is
    // recorded.
    assertEquals(1, progressEvents.size());
    verifySuccessProgress(progressEvents.get(0));

    verifyQueryResults();
  }

  @Test
  public void testLoadWithDocumentsThatAreAlreadyPulledFromBackend() throws Exception {
    Task<Void> docA = db.document("coll-1/a").set(map("bar", "newValueA"));
    Tasks.await(docA);
    Task<Void> docB = db.document("coll-1/b").set(map("bar", "newValueB"));
    Tasks.await(docB);

    EventAccumulator<QuerySnapshot> accumulator = new EventAccumulator<>();

    ListenerRegistration listenerRegistration = null;
    try {
      listenerRegistration = db.collection("coll-1").addSnapshotListener(accumulator.listener());
      accumulator.awaitRemoteEvent();

      ByteBuffer bundle = ByteBuffer.wrap(createBundle());
      LoadBundleTask bundleTask = db.loadBundle(bundle); // Test the ByteBuffer overload
      LoadBundleTaskProgress result = Tasks.await(bundleTask);
      verifySuccessProgress(result);

      // The test bundle is holding ancient documents, so no events are generated as a result.
      // The case where a bundle has newer doc than cache can only be tested in spec tests.
      accumulator.assertNoAdditionalEvents();

      CollectionReference collectionQuery = db.collection("coll-1");
      QuerySnapshot collectionSnapshot = Tasks.await(collectionQuery.get(Source.CACHE));
      assertEquals(
          asList(map("bar", "newValueA"), map("bar", "newValueB")),
          querySnapshotToValues(collectionSnapshot));

      Query limitQuery = Tasks.await(db.getNamedQuery("limit"));
      QuerySnapshot limitSnapshot = Tasks.await(limitQuery.get(Source.CACHE));
      assertEquals(1, limitSnapshot.size());

      Query limitToLastQuery = Tasks.await(db.getNamedQuery("limit-to-last"));
      QuerySnapshot limitToLastSnapshot = Tasks.await(limitToLastQuery.get(Source.CACHE));
      assertEquals(1, limitToLastSnapshot.size());
    } finally {
      if (listenerRegistration != null) {
        listenerRegistration.remove();
      }
    }
  }

  @Test
  public void testLoadedDocumentsShouldNotBeGarbageCollectedRightAway() throws Exception {
    // This test really only makes sense with memory persistence, as SQLite persistence only ever
    // lazily deletes data
    db.setFirestoreSettings(
        new FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
            .build());

    InputStream bundle = new ByteArrayInputStream(createBundle());
    LoadBundleTask bundleTask = db.loadBundle(bundle); // Test the InputStream overload
    LoadBundleTaskProgress result = Tasks.await(bundleTask);
    verifySuccessProgress(result);

    // Read a different collection. This will trigger GC.
    Tasks.await(db.collection("coll-other").get());

    // Read the loaded documents, expecting them to exist in cache. With memory GC, the documents
    // would get GC-ed if we did not hold the document keys in an "umbrella" target. See
    // LocalStore for details.
    verifyQueryResults();
  }

  @Test
  public void testLoadWithDocumentsFromOtherProjectFails() throws Exception {
    List<LoadBundleTaskProgress> progressEvents = new ArrayList<>();

    byte[] bundle = createBundle("other-project", db.getDatabaseId().getDatabaseId());
    LoadBundleTask bundleTask = db.loadBundle(bundle);

    bundleTask.addOnProgressListener(progressEvents::add);

    try {
      awaitCompletion(bundleTask);
      fail();
    } catch (RuntimeExecutionException e) {
      assertThat(e.getCause().getCause())
          .hasMessageThat()
          .contains("Resource name is not valid for current instance");
    }

    assertEquals(2, progressEvents.size());
    verifyProgress(progressEvents.get(0), 0);
    verifyErrorProgress(progressEvents.get(1));

    // Verify Firestore still functions, despite loaded a problematic bundle.
    Tasks.await(db.collection("coll-1").get());
  }

  /**
   * Waits for the completion handler to be invoked.
   *
   * <p>Tasks.await() in combination with progress updates can be racy, as the Task can resolve just
   * before the final progress even is raised. We use listeners here as their order is
   * deterministic.
   */
  private LoadBundleTaskProgress awaitCompletion(LoadBundleTask bundleTask)
      throws InterruptedException {
    Semaphore success = new Semaphore(0);
    bundleTask.addOnCompleteListener(v -> success.release());
    success.acquire();
    return bundleTask.getResult();
  }

  private void verifyQueryResults() throws ExecutionException, InterruptedException {
    // Read from cache. These documents do not exist in backend, so they can only be read from
    // cache.
    CollectionReference collectionQuery = db.collection("coll-1");
    QuerySnapshot collectionSnapshot = Tasks.await(collectionQuery.get(Source.CACHE));
    assertEquals(
        asList(map("bar", 1L, "k", "a"), map("bar", 2L, "k", "\uD83D\uDE0A")),
        querySnapshotToValues(collectionSnapshot));

    Query limitQuery = Tasks.await(db.getNamedQuery("limit"));
    QuerySnapshot limitSnapshot = Tasks.await(limitQuery.get(Source.CACHE));
    assertEquals(asList(map("bar", 2L, "k", "\uD83D\uDE0A")), querySnapshotToValues(limitSnapshot));

    Query limitToLastQuery = Tasks.await(db.getNamedQuery("limit-to-last"));
    QuerySnapshot limitToLastSnapshot = Tasks.await(limitToLastQuery.get(Source.CACHE));
    assertEquals(asList(map("bar", 1L, "k", "a")), querySnapshotToValues(limitToLastSnapshot));
  }

  private int getUTF8ByteCount(String s) {
    return s.getBytes(UTF8_CHARSET).length;
  }

  /**
   * Returns a valid bundle by replacing project id in `BUNDLE_TEMPLATES` with the given db project
   * id (also recalculates length prefixes).
   */
  private byte[] createBundle(String projectId, String databaseId)
      throws UnsupportedEncodingException {
    StringBuilder bundle = new StringBuilder();

    // Prepare non-metadata elements since we need the total length of these elements before
    // generating the metadata.
    for (int i = 1; i < BUNDLE_TEMPLATES.length; ++i) {
      // Extract elements from BUNDLE_TEMPLATE and replace the project ID.
      String element = BUNDLE_TEMPLATES[i].replaceAll("\\{projectId\\}", projectId);
      element = element.replaceAll("\\(default\\)", databaseId);
      bundle.append(getUTF8ByteCount(element));
      bundle.append(element);
    }

    String bundleString = bundle.toString();

    // Update BundleMetadata with new totalBytes.
    String metadata =
        BUNDLE_TEMPLATES[0].replace(
            "{totalBytes}", Integer.toString(bundleString.getBytes("UTF-8").length));

    String fullBundle = getUTF8ByteCount(metadata) + metadata + bundleString;
    return fullBundle.getBytes("UTF-8");
  }

  /**
   * Returns a valid bundle by replacing project id in `BUNDLE_TEMPLATES` with the project ID of the
   * current test database.
   */
  private byte[] createBundle() throws UnsupportedEncodingException {
    return createBundle(db.getDatabaseId().getProjectId(), db.getDatabaseId().getDatabaseId());
  }

  private void verifySuccessProgress(LoadBundleTaskProgress progress) {
    assertEquals(LoadBundleTaskProgress.TaskState.SUCCESS, progress.getTaskState());
    assertEquals(progress.getTotalBytes(), progress.getBytesLoaded());
    assertEquals(progress.getTotalDocuments(), progress.getDocumentsLoaded());
  }

  private void verifyErrorProgress(LoadBundleTaskProgress progress) {
    assertEquals(LoadBundleTaskProgress.TaskState.ERROR, progress.getTaskState());
    assertEquals(0, progress.getBytesLoaded());
    assertEquals(0, progress.getDocumentsLoaded());
  }

  private void verifyProgress(LoadBundleTaskProgress progress, int expectedDocuments) {
    assertEquals(LoadBundleTaskProgress.TaskState.RUNNING, progress.getTaskState());
    assertTrue(progress.getBytesLoaded() <= progress.getTotalBytes());
    assertTrue(progress.getDocumentsLoaded() <= progress.getTotalDocuments());
    assertEquals(expectedDocuments, progress.getDocumentsLoaded());
  }
}
