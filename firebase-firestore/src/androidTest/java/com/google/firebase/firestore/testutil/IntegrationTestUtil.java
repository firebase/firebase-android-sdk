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

package com.google.firebase.firestore.testutil;

import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.util.Util.autoId;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.Context;
import android.os.StrictMode;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.firestore.AccessHelper;
import com.google.firebase.firestore.BuildConfig;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.firestore.auth.User;
import com.google.firebase.firestore.core.DatabaseInfo;
import com.google.firebase.firestore.model.DatabaseId;
import com.google.firebase.firestore.testutil.provider.FirestoreProvider;
import com.google.firebase.firestore.util.AsyncQueue;
import com.google.firebase.firestore.util.Listener;
import com.google.firebase.firestore.util.Logger;
import com.google.firebase.firestore.util.Logger.Level;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

class MockCredentialsProvider extends EmptyCredentialsProvider {

  private static MockCredentialsProvider instance;
  private Listener<User> listener;

  public static MockCredentialsProvider instance() {
    if (MockCredentialsProvider.instance == null) {
      MockCredentialsProvider.instance = new MockCredentialsProvider();
    }
    return MockCredentialsProvider.instance;
  }

  private MockCredentialsProvider() {}

  @Override
  public void setChangeListener(Listener<User> changeListener) {
    super.setChangeListener(changeListener);
    this.listener = changeListener;
  }

  public void changeUserTo(User user) {
    listener.onValue(user);
  }
}

/** A set of helper methods for tests */
public class IntegrationTestUtil {

  public enum TargetBackend {
    EMULATOR,
    QA,
    NIGHTLY,
    PROD
  }

  // Set this to the desired enum value to change the target backend when running tests locally.
  // Note: DO NOT change this variable except for local testing.
  private static final TargetBackend backendForLocalTesting = null;

  private static final TargetBackend backend = getTargetBackend();
  private static final String EMULATOR_HOST = "10.0.2.2";
  private static final int EMULATOR_PORT = 8080;

  // Alternate project ID for creating "bad" references. Doesn't actually need to work.
  public static final String BAD_PROJECT_ID = "test-project-2";

  /** Online status of all active Firestore clients. */
  private static final Map<FirebaseFirestore, Boolean> firestoreStatus = new HashMap<>();

  /** Default amount of time to wait for a given operation to complete, used by waitFor() helper. */
  private static final long OPERATION_WAIT_TIMEOUT_MS = 30000;

  /**
   * Firestore databases can be subject to a ~30s "cold start" delay if they have not been used
   * recently, so before any tests run we "prime" the backend.
   */
  private static final long PRIMING_TIMEOUT_MS = 45000;

  private static final FirestoreProvider provider = new FirestoreProvider();

  private static boolean strictModeEnabled = false;
  private static boolean backendPrimed = false;

  // FirebaseOptions needed to create a test FirebaseApp.
  private static final FirebaseOptions OPTIONS =
      new FirebaseOptions.Builder()
          .setApplicationId(":123:android:123ab")
          .setProjectId(provider.projectId())
          .build();

  public static FirestoreProvider provider() {
    return provider;
  }

  private static String getFirestoreHost() {
    switch (backend) {
      case EMULATOR:
        return String.format("%s:%d", EMULATOR_HOST, EMULATOR_PORT);
      case QA:
        return "staging-firestore.sandbox.googleapis.com";
      case NIGHTLY:
        return "test-firestore.sandbox.googleapis.com";
      case PROD:
      default:
        return "firestore.googleapis.com";
    }
  }

  private static boolean getSslEnabled() {
    // ssl is enabled in all environments except for the emulator.
    return !isRunningAgainstEmulator();
  }

  public static TargetBackend getTargetBackend() {
    if (backendForLocalTesting != null) {
      return backendForLocalTesting;
    }
    switch (BuildConfig.TARGET_BACKEND) {
      case "emulator":
        return TargetBackend.EMULATOR;
      case "qa":
        return TargetBackend.QA;
      case "nightly":
        return TargetBackend.NIGHTLY;
      case "prod":
        return TargetBackend.PROD;
      default:
        throw new RuntimeException("Unknown backend configuration used for integration tests.");
    }
  }

  public static DatabaseInfo testEnvDatabaseInfo() {
    return new DatabaseInfo(
        DatabaseId.forProject(provider.projectId()),
        "test-persistenceKey",
        getFirestoreHost(),
        getSslEnabled());
  }

  public static FirebaseFirestoreSettings newTestSettings() {
    Logger.debug("IntegrationTestUtil", "target backend is: %s", backend.name());
    FirebaseFirestoreSettings.Builder settings = new FirebaseFirestoreSettings.Builder();
    settings.setHost(getFirestoreHost());
    settings.setSslEnabled(getSslEnabled());
    return settings.build();
  }

  public static FirebaseApp testFirebaseApp() {
    try {
      return FirebaseApp.getInstance(FirebaseApp.DEFAULT_APP_NAME);
    } catch (IllegalStateException e) {
      return FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext(), OPTIONS);
    }
  }

  /** Initializes a new Firestore instance that uses the default project. */
  public static FirebaseFirestore testFirestore() {
    return testFirestore(newTestSettings());
  }

  /**
   * Initializes a new Firestore instance that uses the default project, customized with the
   * provided settings.
   */
  public static FirebaseFirestore testFirestore(FirebaseFirestoreSettings settings) {
    FirebaseFirestore firestore = testFirestore(provider.projectId(), Level.DEBUG, settings);
    primeBackend();
    return firestore;
  }

  private static void primeBackend() {
    if (!backendPrimed) {
      backendPrimed = true;
      TaskCompletionSource<Void> watchInitialized = new TaskCompletionSource<>();
      TaskCompletionSource<Void> watchUpdateReceived = new TaskCompletionSource<>();
      DocumentReference docRef = testDocument();
      ListenerRegistration listenerRegistration =
          docRef.addSnapshotListener(
              (snapshot, error) -> {
                assertNull(error);
                if ("done".equals(snapshot.get("value"))) {
                  watchUpdateReceived.setResult(null);
                } else {
                  watchInitialized.setResult(null);
                }
              });

      // Wait for watch to initialize and deliver first event.
      waitFor(watchInitialized.getTask());

      // Use a transaction to perform a write without triggering any local events.
      docRef
          .getFirestore()
          .runTransaction(
              transaction -> {
                transaction.set(docRef, map("value", "done"));
                return null;
              });

      // Wait to see the write on the watch stream.
      waitFor(watchUpdateReceived.getTask(), PRIMING_TIMEOUT_MS);

      listenerRegistration.remove();
    }
  }

  /** Initializes a new Firestore instance that uses a non-existing default project. */
  public static FirebaseFirestore testAlternateFirestore() {
    return testFirestore(BAD_PROJECT_ID, Level.DEBUG, newTestSettings());
  }

  /**
   * Enable strict mode for integration tests. Currently checks for leaked SQLite or other Closeable
   * objects.
   *
   * <p>If a leak is found, Android will log the leak and kill the test.
   */
  private static void ensureStrictMode() {
    if (strictModeEnabled) {
      return;
    }

    strictModeEnabled = true;
    StrictMode.setVmPolicy(
        new StrictMode.VmPolicy.Builder()
            .detectLeakedSqlLiteObjects()
            .detectLeakedClosableObjects()
            .penaltyLog()
            .penaltyDeath()
            .build());
  }

  /**
   * Initializes a new Firestore instance that can be used in testing. It is guaranteed to not share
   * state with other instances returned from this call.
   */
  public static FirebaseFirestore testFirestore(
      String projectId, Logger.Level logLevel, FirebaseFirestoreSettings settings) {
    String persistenceKey = "db" + firestoreStatus.size();
    return testFirestore(projectId, logLevel, settings, persistenceKey);
  }

  public static FirebaseFirestore testFirestore(
      String projectId,
      Logger.Level logLevel,
      FirebaseFirestoreSettings settings,
      String persistenceKey) {
    return testFirestore(
        DatabaseId.forDatabase(projectId, BuildConfig.TARGET_DATABASE_ID),
        logLevel,
        settings,
        persistenceKey);
  }

  public static FirebaseFirestore testFirestore(
      DatabaseId databaseId,
      Logger.Level logLevel,
      FirebaseFirestoreSettings settings,
      String persistenceKey) {
    // This unfortunately is a global setting that affects existing Firestore clients.
    Logger.setLogLevel(logLevel);

    Context context = ApplicationProvider.getApplicationContext();

    ensureStrictMode();

    AsyncQueue asyncQueue = new AsyncQueue();

    FirebaseFirestore firestore =
        AccessHelper.newFirebaseFirestore(
            context,
            databaseId,
            persistenceKey,
            MockCredentialsProvider.instance(),
            new EmptyAppCheckTokenProvider(),
            asyncQueue,
            /*firebaseApp=*/ null,
            /*instanceRegistry=*/ (dbId) -> {});
    waitFor(firestore.clearPersistence());
    firestore.setFirestoreSettings(settings);
    firestoreStatus.put(firestore, true);

    return firestore;
  }

  public static void tearDown() {
    try {
      for (FirebaseFirestore firestore : firestoreStatus.keySet()) {
        Task<Void> result = firestore.terminate();
        waitFor(result);
      }
    } finally {
      firestoreStatus.clear();
    }
  }

  public static DocumentReference testDocument() {
    return testCollection("test-collection").document();
  }

  public static DocumentReference testDocumentWithData(Map<String, Object> data) {
    DocumentReference docRef = testDocument();
    waitFor(docRef.set(data));
    return docRef;
  }

  public static CollectionReference testCollection() {
    return testFirestore().collection(autoId());
  }

  public static CollectionReference testCollection(String name) {
    return testFirestore().collection(name + "_" + autoId());
  }

  public static CollectionReference testCollectionWithDocs(Map<String, Map<String, Object>> docs) {
    CollectionReference collection = testCollection();
    CollectionReference writer = testFirestore().collection(collection.getId());
    writeAllDocs(writer, docs);
    return collection;
  }

  public static void writeAllDocs(
      CollectionReference collection, Map<String, Map<String, Object>> docs) {
    WriteBatch writeBatch = null;
    int writeBatchSize = 0;

    for (Map.Entry<String, Map<String, Object>> doc : docs.entrySet()) {
      if (writeBatch == null) {
        writeBatch = collection.getFirestore().batch();
      }

      writeBatch.set(collection.document(doc.getKey()), doc.getValue());
      writeBatchSize++;

      // Write batches are capped at 500 writes. Use 400 just to be safe.
      if (writeBatchSize == 400) {
        waitFor(writeBatch.commit());
        writeBatch = null;
        writeBatchSize = 0;
      }
    }

    if (writeBatch != null) {
      waitFor(writeBatch.commit());
    }
  }

  public static void waitForOnlineSnapshot(DocumentReference doc) {
    TaskCompletionSource<Void> done = new TaskCompletionSource<>();
    ListenerRegistration registration =
        doc.addSnapshotListener(
            MetadataChanges.INCLUDE,
            (snapshot, error) -> {
              assertNull(error);
              if (!snapshot.getMetadata().isFromCache()) {
                done.setResult(null);
              }
            });
    waitFor(done.getTask());
    registration.remove();
  }

  public static void waitFor(Semaphore semaphore) {
    waitFor(semaphore, 1);
  }

  public static void waitFor(Semaphore semaphore, int count) {
    try {
      boolean acquired =
          semaphore.tryAcquire(count, OPERATION_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
      if (!acquired) {
        throw new TimeoutException("Failed to acquire semaphore within test timeout");
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static void waitFor(CountDownLatch countDownLatch) {
    try {
      boolean acquired = countDownLatch.await(OPERATION_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
      if (!acquired) {
        throw new TimeoutException("Failed to acquire countdown latch within test timeout");
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static <T> T waitFor(Task<T> task) {
    return waitFor(task, OPERATION_WAIT_TIMEOUT_MS);
  }

  public static <T> T waitFor(Task<T> task, long timeoutMS) {
    try {
      return Tasks.await(task, timeoutMS, TimeUnit.MILLISECONDS);
    } catch (TimeoutException | ExecutionException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  public static <T> Exception waitForException(Task<T> task) {
    try {
      Tasks.await(task, OPERATION_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
      throw new RuntimeException("Expected Exception but Task completed successfully.");
    } catch (ExecutionException e) {
      return (Exception) e.getCause();
    } catch (TimeoutException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  public static List<Map<String, Object>> querySnapshotToValues(QuerySnapshot querySnapshot) {
    List<Map<String, Object>> res = new ArrayList<>();
    for (DocumentSnapshot doc : querySnapshot) {
      res.add(doc.getData());
    }
    return res;
  }

  public static List<String> querySnapshotToIds(QuerySnapshot querySnapshot) {
    List<String> res = new ArrayList<>();
    for (DocumentSnapshot doc : querySnapshot) {
      res.add(doc.getId());
    }
    return res;
  }

  public static void disableNetwork(FirebaseFirestore firestore) {
    if (firestoreStatus.get(firestore)) {
      waitFor(firestore.disableNetwork());
      firestoreStatus.put(firestore, false);
    }
  }

  public static void enableNetwork(FirebaseFirestore firestore) {
    if (!firestoreStatus.get(firestore)) {
      waitFor(firestore.enableNetwork());
      // Wait for the client to connect.
      waitFor(firestore.collection("unknown").document().delete());
      firestoreStatus.put(firestore, true);
    }
  }

  public static boolean isNetworkEnabled(FirebaseFirestore firestore) {
    return firestoreStatus.get(firestore);
  }

  public static void removeFirestore(FirebaseFirestore firestore) {
    firestoreStatus.remove(firestore);
  }

  public static Map<String, Object> toDataMap(QuerySnapshot qrySnap) {
    Map<String, Object> result = new HashMap<>();
    for (DocumentSnapshot docSnap : qrySnap.getDocuments()) {
      result.put(docSnap.getId(), docSnap.getData());
    }
    return result;
  }

  public static boolean isRunningAgainstEmulator() {
    return backend.equals(TargetBackend.EMULATOR);
  }

  public static void testChangeUserTo(User user) {
    MockCredentialsProvider.instance().changeUserTo(user);
  }

  public static List<Object> nullList() {
    List<Object> nullArray = new ArrayList<>();
    nullArray.add(null);
    return nullArray;
  }

  /**
   * Checks that running the query while online (against the backend/emulator) results in the same
   * documents as running the query while offline. If `expectedDocs` is provided, it also checks
   * that both online and offline query result is equal to the expected documents.
   *
   * @param query The query to check
   * @param expectedDocs Ordered list of document keys that are expected to match the query
   */
  public static void checkOnlineAndOfflineResultsMatch(Query query, String... expectedDocs) {
    QuerySnapshot docsFromServer = waitFor(query.get(Source.SERVER));
    QuerySnapshot docsFromCache = waitFor(query.get(Source.CACHE));

    assertEquals(querySnapshotToIds(docsFromServer), querySnapshotToIds(docsFromCache));
    List<String> expected = asList(expectedDocs);
    if (!expected.isEmpty()) {
      assertEquals(expected, querySnapshotToIds(docsFromCache));
    }
  }
}
