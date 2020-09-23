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

package com.google.firebase.firestore.spec;

import static com.google.common.base.Strings.emptyToNull;
import static com.google.firebase.firestore.TestUtil.waitFor;
import static com.google.firebase.firestore.testutil.TestUtil.ARBITRARY_SEQUENCE_NUMBER;
import static com.google.firebase.firestore.testutil.TestUtil.deleteMutation;
import static com.google.firebase.firestore.testutil.TestUtil.deletedDoc;
import static com.google.firebase.firestore.testutil.TestUtil.doc;
import static com.google.firebase.firestore.testutil.TestUtil.key;
import static com.google.firebase.firestore.testutil.TestUtil.patchMutation;
import static com.google.firebase.firestore.testutil.TestUtil.setMutation;
import static com.google.firebase.firestore.testutil.TestUtil.version;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.util.Pair;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.common.collect.Sets;
import com.google.firebase.database.collection.ImmutableSortedSet;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.auth.User;
import com.google.firebase.firestore.core.ComponentProvider;
import com.google.firebase.firestore.core.DatabaseInfo;
import com.google.firebase.firestore.core.DocumentViewChange;
import com.google.firebase.firestore.core.DocumentViewChange.Type;
import com.google.firebase.firestore.core.EventManager;
import com.google.firebase.firestore.core.EventManager.ListenOptions;
import com.google.firebase.firestore.core.OnlineState;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.core.QueryListener;
import com.google.firebase.firestore.core.SyncEngine;
import com.google.firebase.firestore.local.Persistence;
import com.google.firebase.firestore.local.PersistenceTestHelpers;
import com.google.firebase.firestore.local.QueryPurpose;
import com.google.firebase.firestore.local.TargetData;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.MaybeDocument;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.model.mutation.Mutation;
import com.google.firebase.firestore.model.mutation.MutationBatchResult;
import com.google.firebase.firestore.model.mutation.MutationResult;
import com.google.firebase.firestore.remote.ExistenceFilter;
import com.google.firebase.firestore.remote.MockDatastore;
import com.google.firebase.firestore.remote.RemoteEvent;
import com.google.firebase.firestore.remote.RemoteStore;
import com.google.firebase.firestore.remote.RemoteStore.RemoteStoreCallback;
import com.google.firebase.firestore.remote.WatchChange;
import com.google.firebase.firestore.remote.WatchChange.DocumentChange;
import com.google.firebase.firestore.remote.WatchChange.ExistenceFilterWatchChange;
import com.google.firebase.firestore.remote.WatchChange.WatchTargetChange;
import com.google.firebase.firestore.remote.WatchChange.WatchTargetChangeType;
import com.google.firebase.firestore.remote.WatchStream;
import com.google.firebase.firestore.testutil.TestUtil;
import com.google.firebase.firestore.util.Assert;
import com.google.firebase.firestore.util.AsyncQueue;
import com.google.firebase.firestore.util.AsyncQueue.TimerId;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.robolectric.android.util.concurrent.RoboExecutorService;

/**
 * Subclasses of SpecTestCase run a set of portable event specifications from JSON spec files
 * against a special isolated version of the Firestore client that allows precise control over when
 * events are delivered. This allows us to test client behavior in a very reliable, deterministic
 * way, including edge cases that would be difficult to reliably reproduce in a full integration
 * test.
 *
 * <p>Both events from user code (adding/removing listens, performing mutations) and events from the
 * Datastore are simulated, while installing as much of the system in between as possible.
 *
 * <p>SpecTestCase is an abstract base class that must be subclassed to test against a specific
 * local store implementation. To create a new variant of SpecTestCase:
 *
 * <ol>
 *   <li>Subclass SpecTestCase.
 *   <li>override {@link #initializeComponentProvider(ComponentProvider.Configuration, boolean)} to
 *       return appropriate components.
 * </ol>
 */
public abstract class SpecTestCase implements RemoteStoreCallback {
  /** Set this to true when debugging test failures. */
  private static final boolean DEBUG = false;

  // TODO: Make this configurable with JUnit options.
  private static final boolean RUN_BENCHMARK_TESTS = false;
  private static final String BENCHMARK_TAG = "benchmark";

  // Disables all other tests; useful for debugging. Multiple tests can have
  // this tag and they'll all be run (but all others won't).
  private static final String EXCLUSIVE_TAG = "exclusive";

  // The name of a Java system property ({@link System#getProperty(String)}) whose value is a filter
  // that specifies which tests to execute. The value of this property is a regular expression that
  // is matched against the name of each test. Using this property is an alternative to setting the
  // {@link #EXCLUSIVE_TAG} tag, which requires modifying the JSON file. To use this property,
  // specify -DspecTestFilter=<Regex> to the Java runtime, replacing <Regex> with a regular
  // expression; a test will be executed if and only if its name matches this regular expression.
  // In this context, a test's "name" is the result of appending its "itName" to its "describeName",
  // separated by a space character.
  private static final String TEST_FILTER_PROPERTY = "specTestFilter";

  // Tags on tests that should be excluded from execution, useful to allow the platforms to
  // temporarily diverge or for features that are designed to be platform specific (such as
  // 'multi-client').
  private static final Set<String> DISABLED_TAGS =
      RUN_BENCHMARK_TESTS
          ? Sets.newHashSet("no-android", "multi-client")
          : Sets.newHashSet("no-android", BENCHMARK_TAG, "multi-client");

  private boolean garbageCollectionEnabled;
  private int maxConcurrentLimboResolutions;
  private boolean networkEnabled = true;

  //
  // Parts of the Firestore system that the spec tests need to control.
  //
  private Persistence localPersistence;

  private AsyncQueue queue;
  private MockDatastore datastore;
  private RemoteStore remoteStore;
  private SyncEngine syncEngine;
  private EventManager eventManager;
  private DatabaseInfo databaseInfo;

  /** Events to be checked by the expectations. */
  private List<QueryEvent> events;

  /**
   * A dictionary for tracking the listens on queries. Note that the identity of the listeners is
   * used to remove them.
   */
  private Map<Query, QueryListener> queryListeners;

  /**
   * Set of documents that are expected to be in limbo with an active target. Verified at every
   * step.
   */
  private Set<DocumentKey> expectedActiveLimboDocs;

  /**
   * Set of documents that are expected to be in limbo, enqueued for resolution and, therefore,
   * without an active target. Verified at every step.
   */
  private Set<DocumentKey> expectedEnqueuedLimboDocs;

  /** Set of expected active targets, keyed by target ID. */
  private Map<Integer, Pair<List<TargetData>, String>> expectedActiveTargets;

  /**
   * The writes that have been sent to the SyncEngine via {@link SyncEngine#writeMutations} but not
   * yet acknowledged by calling receiveWriteAck/Error. They are tracked per-user.
   *
   * <p>It is mostly an implementation detail used internally to validate that the writes sent to
   * the mock backend by the SyncEngine match the user mutations that initiated them.
   *
   * <p>It is exposed specifically for use doRestart to test persistence scenarios where the
   * SyncEngine is restarted while the Persistence implementation still has outstanding persisted
   * mutations.
   *
   * <p>Note: The size of the list for the current user will generally be the same as {@link
   * #writesSent}, but not necessarily, since the RemoteStore limits the number of outstanding
   * writes to the backend at a given time.
   */
  private Map<User, List<Pair<Mutation, Task<Void>>>> outstandingWrites;

  private final List<DocumentKey> acknowledgedDocs =
      Collections.synchronizedList(new ArrayList<>());
  private final List<DocumentKey> rejectedDocs = Collections.synchronizedList(new ArrayList<>());
  private List<EventListener<Void>> snapshotsInSyncListeners;
  private int waitForPendingWriteEvents = 0;
  private int snapshotsInSyncEvents = 0;

  /** An executor to use for test callbacks. */
  private final RoboExecutorService backgroundExecutor = new RoboExecutorService();

  /** The current user for the SyncEngine. Determines which mutation queue is active. */
  private User currentUser;

  public static void info(String line) {
    if (DEBUG) {
      // Print log information out directly to cut down on logger-related cruft like the extra
      // line for the date and class method which are always SpecTestCase+info
      System.err.println(line);
    } else {
      Logger.getLogger(SpecTestCase.class.getSimpleName()).info(line);
    }
  }

  public static void log(String line) {
    if (DEBUG) {
      info(line);
    }
  }

  //
  // Methods for tracking state of writes.
  //

  protected abstract ComponentProvider initializeComponentProvider(
      ComponentProvider.Configuration configuration, boolean garbageCollectionEnabled);

  private boolean shouldRun(Set<String> tags) {
    for (String tag : tags) {
      if (DISABLED_TAGS.contains(tag)) {
        return false;
      }
    }

    return !isExcluded(tags);
  }

  protected abstract boolean isExcluded(Set<String> tags);

  protected void specSetUp(JSONObject config) {
    log("    Clearing all state.");

    outstandingWrites = new HashMap<>();

    this.garbageCollectionEnabled = config.optBoolean("useGarbageCollection", false);
    this.maxConcurrentLimboResolutions =
        config.optInt("maxConcurrentLimboResolutions", Integer.MAX_VALUE);

    currentUser = User.UNAUTHENTICATED;
    databaseInfo = PersistenceTestHelpers.nextDatabaseInfo();

    if (config.optInt("numClients", 1) != 1) {
      throw Assert.fail("The Android client does not support multi-client tests");
    }

    initClient();

    // Set up internal event tracking for the spec tests.
    events = new ArrayList<>();
    queryListeners = new HashMap<>();

    expectedActiveLimboDocs = new HashSet<>();
    expectedEnqueuedLimboDocs = new HashSet<>();
    expectedActiveTargets = new HashMap<>();

    snapshotsInSyncListeners = Collections.synchronizedList(new ArrayList<>());
  }

  protected void specTearDown() throws Exception {
    queue.runSync(
        () -> {
          remoteStore.shutdown();
          localPersistence.shutdown();
        });
  }

  /**
   * Sets up a new client. Is used to initially setup the client initially and after every restart.
   */
  private void initClient() {
    queue = new AsyncQueue();
    datastore = new MockDatastore(databaseInfo, queue, ApplicationProvider.getApplicationContext());

    ComponentProvider.Configuration configuration =
        new ComponentProvider.Configuration(
            ApplicationProvider.getApplicationContext(),
            queue,
            databaseInfo,
            datastore,
            currentUser,
            maxConcurrentLimboResolutions,
            new FirebaseFirestoreSettings.Builder().build());

    ComponentProvider provider =
        initializeComponentProvider(configuration, garbageCollectionEnabled);
    localPersistence = provider.getPersistence();
    remoteStore = provider.getRemoteStore();
    syncEngine = provider.getSyncEngine();
    eventManager = provider.getEventManager();
  }

  @Override
  public void handleOnlineStateChange(OnlineState onlineState) {
    syncEngine.handleOnlineStateChange(onlineState);
  }

  private List<Pair<Mutation, Task<Void>>> getCurrentOutstandingWrites() {
    List<Pair<Mutation, Task<Void>>> writes = outstandingWrites.get(currentUser);
    if (writes == null) {
      writes = new ArrayList<>();
      outstandingWrites.put(currentUser, writes);
    }
    return writes;
  }

  //
  // Methods for mocking out the grpc streams.
  //

  /** Validates that a write was sent and matches the expected write. */
  private void validateNextWriteSent(Mutation expectedWrite) {
    List<Mutation> request = datastore.waitForWriteSend();
    // TODO: Batch writes not supported yet
    assertEquals(1, request.size());
    Mutation actualWrite = request.get(0);
    assertEquals(expectedWrite, actualWrite);
    log("      This write was sent: " + actualWrite);
  }

  private int writesSent() {
    return datastore.writesSent();
  }

  //
  // Methods for constructing objects from specs.
  //

  /**
   * The format for a query is string|{path, limit?}.
   * https://github.com/firebase/firebase-js-sdk/blob/master/packages/firestore/test/unit/specs/spec_test_runner.ts#L1115
   */
  private Query parseQuery(Object querySpec) throws JSONException {
    if (querySpec instanceof String) {
      return Query.atPath(ResourcePath.fromString((String) querySpec));
    } else if (querySpec instanceof JSONObject) {
      JSONObject queryDict = (JSONObject) querySpec;
      String path = queryDict.getString("path");
      String collectionGroup =
          queryDict.has("collectionGroup") ? queryDict.getString("collectionGroup") : null;
      Query query = new Query(ResourcePath.fromString(path), collectionGroup);

      if (queryDict.has("limit")) {
        if (queryDict.getString("limitType").equals("LimitToFirst")) {
          query = query.limitToFirst(queryDict.getLong("limit"));
        } else {
          query = query.limitToLast(queryDict.getLong("limit"));
        }
      }

      if (queryDict.has("filters")) {
        JSONArray array = queryDict.getJSONArray("filters");
        for (int i = 0; i < array.length(); i++) {
          JSONArray filter = array.getJSONArray(i);
          String field = filter.getString(0);
          String op = filter.getString(1);
          Object value = filter.get(2);
          query = query.filter(TestUtil.filter(field, op, value));
        }
      }

      if (queryDict.has("orderBys")) {
        JSONArray array = queryDict.getJSONArray("orderBys");
        for (int i = 0; i < array.length(); i++) {
          JSONArray orderBy = array.getJSONArray(i);
          String field = orderBy.getString(0);
          String direction = orderBy.getString(1);
          query = query.orderBy(TestUtil.orderBy(field, direction));
        }
      }

      return query;
    } else {
      throw Assert.fail("Invalid query: %s", querySpec);
    }
  }

  private DocumentViewChange parseChange(JSONObject jsonDoc, DocumentViewChange.Type type)
      throws JSONException {
    long version = jsonDoc.getLong("version");
    JSONObject options = jsonDoc.getJSONObject("options");
    Document.DocumentState documentState =
        options.optBoolean("hasLocalMutations")
            ? Document.DocumentState.LOCAL_MUTATIONS
            : (options.optBoolean("hasCommittedMutations")
                ? Document.DocumentState.COMMITTED_MUTATIONS
                : Document.DocumentState.SYNCED);
    Map<String, Object> values = parseMap(jsonDoc.getJSONObject("value"));
    Document doc = doc(jsonDoc.getString("key"), version, values, documentState);
    return DocumentViewChange.create(type, doc);
  }

  /** Deeply parses a JSONObject or JSONArray into a Map or List. */
  private Object parseObject(Object obj) throws JSONException {
    if (obj instanceof JSONArray) {
      return parseList((JSONArray) obj);
    } else if (obj instanceof JSONObject) {
      return parseMap((JSONObject) obj);
    } else {
      return obj;
    }
  }

  /** Deeply parses a JSONArray into a List, recursively parsing its children. */
  private List<Object> parseList(JSONArray arr) throws JSONException {
    List<Object> result = new ArrayList<>(arr.length());
    for (int i = 0; i < arr.length(); ++i) {
      result.add(parseObject(arr.get(i)));
    }
    return result;
  }

  /** Deeply parses a JSONObject into a Map, recursively parsing its children. */
  private Map<String, Object> parseMap(JSONObject obj) throws JSONException {
    Map<String, Object> values = new HashMap<>();
    Iterator<String> keys = obj.keys();
    while (keys.hasNext()) {
      String key = keys.next();
      values.put(key, parseObject(obj.get(key)));
    }
    return values;
  }

  /** Deeply parses a JSONArray into a List<Integer>. */
  private List<Integer> parseIntList(@Nullable JSONArray arr) throws JSONException {
    List<Integer> result = new ArrayList<>();
    if (arr == null) {
      return result;
    }
    for (int i = 0; i < arr.length(); ++i) {
      result.add(arr.getInt(i));
    }
    return result;
  }

  //
  // Methods for doing the steps of the spec test.
  //

  private void doListen(JSONArray listenSpec) throws Exception {
    int expectedId = listenSpec.getInt(0);
    Query query = parseQuery(listenSpec.get(1));
    // TODO: Allow customizing listen options in spec tests
    ListenOptions options = new ListenOptions();
    options.includeDocumentMetadataChanges = true;
    options.includeQueryMetadataChanges = true;
    QueryListener listener =
        new QueryListener(
            query,
            options,
            (value, error) -> {
              QueryEvent event = new QueryEvent();
              event.query = query;
              if (value != null) {
                event.view = value;
              } else {
                event.error = error;
              }
              events.add(event);
            });
    queryListeners.put(query, listener);
    queue.runSync(
        () -> {
          int actualId = eventManager.addQueryListener(listener);
          assertEquals(expectedId, actualId);
        });
  }

  private void doUnlisten(JSONArray unlistenSpec) throws Exception {
    Query query = parseQuery(unlistenSpec.get(1));
    QueryListener listener = queryListeners.remove(query);
    queue.runSync(() -> eventManager.removeQueryListener(listener));
  }

  private void doMutation(Mutation mutation) throws Exception {
    DocumentKey documentKey = mutation.getKey();
    TaskCompletionSource<Void> callback = new TaskCompletionSource<>();
    Task<Void> writeProcessed =
        callback
            .getTask()
            .continueWith(
                backgroundExecutor,
                task -> {
                  if (task.isSuccessful()) {
                    SpecTestCase.this.acknowledgedDocs.add(documentKey);
                  } else {
                    SpecTestCase.this.rejectedDocs.add(documentKey);
                  }
                  return null;
                });

    getCurrentOutstandingWrites().add(new Pair<>(mutation, writeProcessed));
    log("      Sending this write: " + mutation);
    queue.runSync(() -> syncEngine.writeMutations(singletonList(mutation), callback));
  }

  private void doSet(JSONArray setSpec) throws Exception {
    doMutation(setMutation(setSpec.getString(0), parseMap(setSpec.getJSONObject(1))));
  }

  private void doPatch(JSONArray patchSpec) throws Exception {
    doMutation(patchMutation(patchSpec.getString(0), parseMap(patchSpec.getJSONObject(1))));
  }

  private void doDelete(String key) throws Exception {
    doMutation(deleteMutation(key));
  }

  private void doWaitForPendingWrites() {
    final TaskCompletionSource<Void> source = new TaskCompletionSource<>();
    source
        .getTask()
        .addOnSuccessListener(backgroundExecutor, result -> waitForPendingWriteEvents += 1);
    syncEngine.registerPendingWritesTask(source);
  }

  private void doAddSnapshotsInSyncListener() {
    EventListener<Void> eventListener =
        (Void v, FirebaseFirestoreException error) -> snapshotsInSyncEvents += 1;
    snapshotsInSyncListeners.add(eventListener);
    eventManager.addSnapshotsInSyncListener(eventListener);
  }

  private void doRemoveSnapshotsInSyncListener() throws Exception {
    if (snapshotsInSyncListeners.size() == 0) {
      throw Assert.fail("There must be a listener to unlisten to");
    } else {
      EventListener<Void> listenerToRemove = snapshotsInSyncListeners.remove(0);
      eventManager.removeSnapshotsInSyncListener(listenerToRemove);
    }
  }

  // Helper for calling datastore.writeWatchChange() on the AsyncQueue.
  private void writeWatchChange(WatchChange change, SnapshotVersion version) throws Exception {
    queue.runSync(() -> datastore.writeWatchChange(change, version));
  }

  private void doWatchAck(JSONArray ackedTargets) throws Exception {
    WatchTargetChange change =
        new WatchTargetChange(WatchTargetChangeType.Added, parseIntList(ackedTargets));
    writeWatchChange(change, SnapshotVersion.NONE);
  }

  private void doWatchCurrent(JSONArray currentSpec) throws Exception {
    List<Integer> currentTargets = parseIntList(currentSpec.getJSONArray(0));
    ByteString resumeToken = ByteString.copyFromUtf8(currentSpec.getString(1));
    WatchTargetChange change =
        new WatchTargetChange(WatchTargetChangeType.Current, currentTargets, resumeToken);
    writeWatchChange(change, SnapshotVersion.NONE);
  }

  private void doWatchRemove(JSONObject watchRemoveSpec) throws Exception {
    Status error = null;
    JSONObject cause = watchRemoveSpec.optJSONObject("cause");
    if (cause != null) {
      int code = cause.optInt("code");
      if (code != 0) {
        error = Status.fromCodeValue(code);
      }
    }
    List<Integer> targetIds = parseIntList(watchRemoveSpec.getJSONArray("targetIds"));
    WatchTargetChange change =
        new WatchTargetChange(
            WatchTargetChangeType.Removed, targetIds, WatchStream.EMPTY_RESUME_TOKEN, error);
    writeWatchChange(change, SnapshotVersion.NONE);
    // Unlike web, the MockDatastore detects a watch removal with cause and will remove active
    // targets
  }

  private void doWatchEntity(JSONObject watchEntity) throws Exception {
    if (watchEntity.has("docs")) {
      Assert.hardAssert(!watchEntity.has("doc"), "Exactly one of |doc| or |docs| needs to be set.");
      JSONArray docs = watchEntity.getJSONArray("docs");
      for (int i = 0; i < docs.length(); ++i) {
        JSONObject doc = docs.getJSONObject(i);
        JSONObject watchSpec = new JSONObject();
        watchSpec.put("doc", doc);
        if (watchEntity.has("targets")) {
          watchSpec.put("targets", watchEntity.get("targets"));
        }
        if (watchEntity.has("removedTargets")) {
          watchSpec.put("removedTargets", watchEntity.get("removedTargets"));
        }
        doWatchEntity(watchSpec);
      }
    } else if (watchEntity.has("doc")) {
      JSONObject docSpec = watchEntity.getJSONObject("doc");
      String key = docSpec.getString("key");
      @Nullable
      Map<String, Object> value =
          !docSpec.isNull("value") ? parseMap(docSpec.getJSONObject("value")) : null;
      long version = docSpec.getLong("version");
      MaybeDocument doc = value != null ? doc(key, version, value) : deletedDoc(key, version);
      List<Integer> updated = parseIntList(watchEntity.optJSONArray("targets"));
      List<Integer> removed = parseIntList(watchEntity.optJSONArray("removedTargets"));
      WatchChange change = new DocumentChange(updated, removed, doc.getKey(), doc);
      writeWatchChange(change, SnapshotVersion.NONE);
    } else if (watchEntity.has("key")) {
      String key = watchEntity.getString("key");
      List<Integer> removed = parseIntList(watchEntity.optJSONArray("removedTargets"));
      WatchChange change = new DocumentChange(Collections.emptyList(), removed, key(key), null);
      writeWatchChange(change, SnapshotVersion.NONE);
    } else {
      throw Assert.fail("Either key, doc or docs must be set.");
    }
  }

  private void doWatchFilter(JSONArray watchFilter) throws Exception {
    List<Integer> targets = parseIntList(watchFilter.getJSONArray(0));
    Assert.hardAssert(
        targets.size() == 1, "ExistenceFilters currently support exactly one target only.");

    int keyCount = watchFilter.length() == 0 ? 0 : watchFilter.length() - 1;

    // TODO: extend this with different existence filters over time.
    ExistenceFilter filter = new ExistenceFilter(keyCount);
    ExistenceFilterWatchChange change = new ExistenceFilterWatchChange(targets.get(0), filter);
    writeWatchChange(change, SnapshotVersion.NONE);
  }

  private void doWatchReset(JSONArray targetIds) throws Exception {
    List<Integer> targets = parseIntList(targetIds);
    WatchChange change = new WatchTargetChange(WatchTargetChangeType.Reset, targets);
    writeWatchChange(change, SnapshotVersion.NONE);
  }

  private void doWatchSnapshot(JSONObject watchSnapshot) throws Exception {
    // The client will only respond to watchSnapshots if they are on a target change with an empty
    // set of target IDs.
    List<Integer> targets =
        watchSnapshot.has("targetIds")
            ? parseIntList(watchSnapshot.getJSONArray("targetIds"))
            : Collections.emptyList();
    String resumeToken = watchSnapshot.optString("resumeToken");
    WatchChange change =
        new WatchTargetChange(
            WatchTargetChangeType.NoChange, targets, ByteString.copyFromUtf8(resumeToken));
    writeWatchChange(change, version(watchSnapshot.getLong("version")));
  }

  private void doWatchStreamClose(JSONObject spec) throws Exception {
    JSONObject error = spec.getJSONObject("error");

    boolean runBackoffTimer = spec.getBoolean("runBackoffTimer");
    // TODO: Incorporate backoff in Android Spec Tests.
    assertTrue(runBackoffTimer);

    Status status =
        Status.fromCodeValue(error.getInt("code")).withDescription(error.getString("message"));
    queue.runSync(() -> datastore.failWatchStream(status));
    // Unlike web, stream should re-open synchronously (if we have active listeners).
    if (!this.queryListeners.isEmpty()) {
      assertTrue("Watch stream is open", datastore.isWatchStreamOpen());
    }
  }

  private void doWriteAck(JSONObject writeAckSpec) throws Exception {
    long version = writeAckSpec.getLong("version");
    boolean keepInQueue = writeAckSpec.optBoolean("keepInQueue", false);
    assertFalse(
        "'keepInQueue=true' is not supported on Android and should only be set in multi-client tests",
        keepInQueue);
    Pair<Mutation, Task<Void>> write = getCurrentOutstandingWrites().remove(0);
    validateNextWriteSent(write.first);

    MutationResult mutationResult =
        new MutationResult(version(version), /*transformResults=*/ null);
    queue.runSync(() -> datastore.ackWrite(version(version), singletonList(mutationResult)));
  }

  private void doFailWrite(JSONObject writeFailureSpec) throws Exception {
    JSONObject errorSpec = writeFailureSpec.getJSONObject("error");
    boolean keepInQueue = writeFailureSpec.optBoolean("keepInQueue", false);

    int code = errorSpec.getInt("code");
    Status error = Status.fromCodeValue(code);

    Pair<Mutation, Task<Void>> write = getCurrentOutstandingWrites().get(0);
    validateNextWriteSent(write.first);

    // If this is a permanent error, the write is not expected to be sent again.
    if (!keepInQueue) {
      getCurrentOutstandingWrites().remove(0);
    }

    log("      Failing a write.");
    queue.runSync(() -> datastore.failWrite(error));
  }

  private void doRunTimer(String timer) throws Exception {
    TimerId timerId;
    switch (timer) {
      case "all":
        timerId = TimerId.ALL;
        break;
      case "listen_stream_idle":
        timerId = TimerId.LISTEN_STREAM_IDLE;
        break;
      case "listen_stream_connection_backoff":
        timerId = TimerId.LISTEN_STREAM_CONNECTION_BACKOFF;
        break;
      case "write_stream_idle":
        timerId = TimerId.WRITE_STREAM_IDLE;
        break;
      case "write_stream_connection_backoff":
        timerId = TimerId.WRITE_STREAM_CONNECTION_BACKOFF;
        break;
      case "online_state_timeout":
        timerId = TimerId.ONLINE_STATE_TIMEOUT;
        break;
      default:
        throw Assert.fail("runTimer spec step specified unknown timer: %s", timer);
    }

    queue.runDelayedTasksUntil(timerId);
  }

  private void doDrainQueue() throws Exception {
    queue.runSync(() -> {});
  }

  private void doDisableNetwork() throws Exception {
    networkEnabled = false;
    queue.runSync(
        () -> {
          // Make sure to execute all writes that are currently queued. This allows us
          // to assert on the total number of requests sent before shutdown.
          remoteStore.fillWritePipeline();
          remoteStore.disableNetwork();
        });
  }

  private void doEnableNetwork() throws Exception {
    networkEnabled = true;
    queue.runSync(() -> remoteStore.enableNetwork());
  }

  private void doChangeUser(@Nullable String uid) throws Exception {
    currentUser = new User(uid);
    queue.runSync(() -> syncEngine.handleCredentialChange(currentUser));
  }

  private void doRestart() throws Exception {
    queue.runSync(
        () -> {
          remoteStore.shutdown();
          localPersistence.shutdown();
          initClient();
        });
  }

  private void doStep(JSONObject step) throws Exception {
    if (step.optInt("clientIndex", 0) != 0) {
      throw Assert.fail("The Android client does not support switching clients");
    }

    if (step.has("userListen")) {
      doListen(step.getJSONArray("userListen"));
    } else if (step.has("userUnlisten")) {
      doUnlisten(step.getJSONArray("userUnlisten"));
    } else if (step.has("userSet")) {
      doSet(step.getJSONArray("userSet"));
    } else if (step.has("userPatch")) {
      doPatch(step.getJSONArray("userPatch"));
    } else if (step.has("userDelete")) {
      doDelete(step.getString("userDelete"));
    } else if (step.has("addSnapshotsInSyncListener")) {
      doAddSnapshotsInSyncListener();
    } else if (step.has("removeSnapshotsInSyncListener")) {
      doRemoveSnapshotsInSyncListener();
    } else if (step.has("drainQueue")) {
      doDrainQueue();
    } else if (step.has("watchAck")) {
      doWatchAck(step.getJSONArray("watchAck"));
    } else if (step.has("watchCurrent")) {
      doWatchCurrent(step.getJSONArray("watchCurrent"));
    } else if (step.has("watchRemove")) {
      doWatchRemove(step.getJSONObject("watchRemove"));
    } else if (step.has("watchEntity")) {
      doWatchEntity(step.getJSONObject("watchEntity"));
    } else if (step.has("watchFilter")) {
      doWatchFilter(step.getJSONArray("watchFilter"));
    } else if (step.has("watchReset")) {
      doWatchReset(step.getJSONArray("watchReset"));
    } else if (step.has("watchSnapshot")) {
      doWatchSnapshot(step.getJSONObject("watchSnapshot"));
    } else if (step.has("watchStreamClose")) {
      doWatchStreamClose(step.getJSONObject("watchStreamClose"));
    } else if (step.has("watchProto")) {
      // watchProto isn't yet used, and it's unclear how to create arbitrary protos from JSON.
      throw Assert.fail("watchProto is not yet supported.");
    } else if (step.has("writeAck")) {
      doWriteAck(step.getJSONObject("writeAck"));
    } else if (step.has("failWrite")) {
      doFailWrite(step.getJSONObject("failWrite"));
    } else if (step.has("waitForPendingWrites")) {
      doWaitForPendingWrites();
    } else if (step.has("runTimer")) {
      doRunTimer(step.getString("runTimer"));
    } else if (step.has("enableNetwork")) {
      if (step.getBoolean("enableNetwork")) {
        doEnableNetwork();
      } else {
        doDisableNetwork();
      }
    } else if (step.has("changeUser")) {
      // NOTE: JSONObject.getString("foo") where "foo" is mapped to null will return "null".
      // Explicitly testing for isNull here allows the null value to be preserved. This is important
      // because the unauthenticated user is represented as having a null uid as a value for
      // "changeUser".
      String uid = step.isNull("changeUser") ? null : step.getString("changeUser");
      doChangeUser(uid);
    } else if (step.has("restart")) {
      doRestart();
    } else if (step.has("applyClientState")) {
      throw Assert.fail(
          "'applyClientState' is not supported on Android and should only be used in multi-client tests");
    } else {
      throw Assert.fail("Unknown step: %s", step);
    }
  }

  //
  // Methods for validating expectations.
  //

  private void assertEventMatches(JSONObject expected, QueryEvent actual) throws JSONException {
    Query expectedQuery = parseQuery(expected.get("query"));
    assertEquals(expectedQuery, actual.query);
    if (expected.has("errorCode") && !Status.fromCodeValue(expected.getInt("errorCode")).isOk()) {
      assertNotNull(actual.error);
      assertEquals(expected.getInt("errorCode"), actual.error.getCode().value());
    } else {
      List<DocumentViewChange> expectedChanges = new ArrayList<>();
      JSONArray removed = expected.optJSONArray("removed");
      for (int i = 0; removed != null && i < removed.length(); ++i) {
        expectedChanges.add(parseChange(removed.getJSONObject(i), Type.REMOVED));
      }
      JSONArray added = expected.optJSONArray("added");
      for (int i = 0; added != null && i < added.length(); ++i) {
        expectedChanges.add(parseChange(added.getJSONObject(i), Type.ADDED));
      }
      JSONArray modified = expected.optJSONArray("modified");
      for (int i = 0; modified != null && i < modified.length(); ++i) {
        expectedChanges.add(parseChange(modified.getJSONObject(i), Type.MODIFIED));
      }
      JSONArray metadata = expected.optJSONArray("metadata");
      for (int i = 0; metadata != null && i < metadata.length(); ++i) {
        expectedChanges.add(parseChange(metadata.getJSONObject(i), Type.METADATA));
      }
      assertEquals(expectedChanges, actual.view.getChanges());

      boolean expectedHasPendingWrites = expected.optBoolean("hasPendingWrites", false);
      boolean expectedFromCache = expected.optBoolean("fromCache", false);
      assertEquals("hasPendingWrites", expectedHasPendingWrites, actual.view.hasPendingWrites());
      assertEquals("fromCache", expectedFromCache, actual.view.isFromCache());
    }
  }

  private void validateExpectedSnapshotEvents(@Nullable JSONArray expectedEventsJson)
      throws JSONException {
    if (expectedEventsJson == null) {
      for (QueryEvent event : events) {
        fail("Unexpected event: " + event);
      }
      return;
    }

    // Sort both the expected and actual events by the query's canonical ID.
    events.sort((q1, q2) -> q1.query.getCanonicalId().compareTo(q2.query.getCanonicalId()));

    List<JSONObject> expectedEvents = new ArrayList<>();
    for (int i = 0; i < expectedEventsJson.length(); ++i) {
      expectedEvents.add(expectedEventsJson.getJSONObject(i));
    }
    expectedEvents.sort(
        (left, right) -> {
          try {
            Query leftQuery = parseQuery(left.get("query"));
            Query rightQuery = parseQuery(right.get("query"));
            return leftQuery.getCanonicalId().compareTo(rightQuery.getCanonicalId());
          } catch (JSONException e) {
            throw new RuntimeException("Failed to parse JSON during event sorting", e);
          }
        });

    int i = 0;
    for (; i < expectedEvents.size() && i < events.size(); ++i) {
      assertEventMatches(expectedEvents.get(i), events.get(i));
    }
    for (; i < expectedEventsJson.length(); ++i) {
      fail("Missing event: " + expectedEventsJson.get(i));
    }
    for (; i < events.size(); ++i) {
      fail("Unexpected event: " + events.get(i));
    }
  }

  private void validateExpectedState(@Nullable JSONObject expectedState) throws JSONException {
    if (expectedState != null) {
      if (expectedState.has("numOutstandingWrites")) {
        assertEquals(expectedState.getInt("numOutstandingWrites"), writesSent());
      }
      if (expectedState.has("writeStreamRequestCount")) {
        assertEquals(
            expectedState.getInt("writeStreamRequestCount"),
            datastore.getWriteStreamRequestCount());
      }
      if (expectedState.has("watchStreamRequestCount")) {
        assertEquals(
            expectedState.getInt("watchStreamRequestCount"),
            datastore.getWatchStreamRequestCount());
      }
      if (expectedState.has("activeLimboDocs")) {
        expectedActiveLimboDocs = new HashSet<>();
        JSONArray limboDocs = expectedState.getJSONArray("activeLimboDocs");
        for (int i = 0; i < limboDocs.length(); i++) {
          expectedActiveLimboDocs.add(key((String) limboDocs.get(i)));
        }
      }
      if (expectedState.has("enqueuedLimboDocs")) {
        expectedEnqueuedLimboDocs = new HashSet<>();
        JSONArray limboDocs = expectedState.getJSONArray("enqueuedLimboDocs");
        for (int i = 0; i < limboDocs.length(); i++) {
          expectedEnqueuedLimboDocs.add(key((String) limboDocs.get(i)));
        }
      }
      if (expectedState.has("activeTargets")) {
        expectedActiveTargets = new HashMap<>();
        JSONObject activeTargets = expectedState.getJSONObject("activeTargets");
        Iterator<String> keys = activeTargets.keys();
        while (keys.hasNext()) {
          String targetIdString = keys.next();
          int targetId = Integer.parseInt(targetIdString);

          JSONObject queryDataJson = activeTargets.getJSONObject(targetIdString);
          String resumeToken = queryDataJson.getString("resumeToken");
          JSONArray queryArrayJson = queryDataJson.getJSONArray("queries");

          expectedActiveTargets.put(targetId, new Pair<>(new ArrayList<>(), resumeToken));
          for (int i = 0; i < queryArrayJson.length(); i++) {
            Query query = parseQuery(queryArrayJson.getJSONObject(i));
            // TODO: populate the purpose of the target once it's possible to encode that in the
            // spec tests. For now, hard-code that it's a listen despite the fact that it's not
            // always the right value.
            TargetData targetData =
                new TargetData(
                        query.toTarget(), targetId, ARBITRARY_SEQUENCE_NUMBER, QueryPurpose.LISTEN)
                    .withResumeToken(ByteString.copyFromUtf8(resumeToken), SnapshotVersion.NONE);

            expectedActiveTargets.get(targetId).first.add(targetData);
          }
        }
      }
    }

    // Always validate the we received the expected number of events.
    validateUserCallbacks(expectedState);
    // Always validate that the expected limbo docs match the actual limbo docs.
    validateActiveLimboDocs();
    validateEnqueuedLimboDocs();
    // Always validate that the expected active targets match the actual active targets.
    validateActiveTargets();
  }

  private void validateSnapshotsInSyncEvents(int expectedCount) {
    assertEquals(expectedCount, snapshotsInSyncEvents);
    snapshotsInSyncEvents = 0;
  }

  private void validateWaitForPendingWritesEvents(int expectedCount) {
    assertEquals(expectedCount, waitForPendingWriteEvents);
    waitForPendingWriteEvents = 0;
  }

  private void validateUserCallbacks(@Nullable JSONObject expected) throws JSONException {
    if (expected != null && expected.has("userCallbacks")) {
      JSONObject userCallbacks = expected.getJSONObject("userCallbacks");

      JSONArray expectedAcknowledgedDocs = userCallbacks.optJSONArray("acknowledgedDocs");
      for (int i = 0; i < expectedAcknowledgedDocs.length(); i++) {
        String documentKey = (String) expectedAcknowledgedDocs.get(i);
        assertTrue(
            "Expected acknowledgment for " + documentKey,
            this.acknowledgedDocs.contains(key(documentKey)));
      }

      JSONArray expectedRejectedDocs = userCallbacks.optJSONArray("rejectedDocs");
      for (int i = 0; i < expectedRejectedDocs.length(); i++) {
        String documentKey = (String) expectedRejectedDocs.get(i);
        assertTrue(
            "Expected rejection for " + documentKey, this.rejectedDocs.contains(key(documentKey)));
      }
    } else {
      assertTrue(this.acknowledgedDocs.isEmpty());
      assertTrue(this.rejectedDocs.isEmpty());
    }
  }

  private void validateActiveLimboDocs() {
    // Make a copy so it can modified while checking against the expected limbo docs.
    @SuppressWarnings("VisibleForTests")
    Map<DocumentKey, Integer> actualLimboDocs =
        new HashMap<>(syncEngine.getActiveLimboDocumentResolutions());

    // Validate that each active limbo doc has an expected active target
    for (Map.Entry<DocumentKey, Integer> limboDoc : actualLimboDocs.entrySet()) {
      assertTrue(
          "Found limbo doc "
              + limboDoc.getKey()
              + ", but its target ID "
              + limboDoc.getValue()
              + " was not in the set of expected active target IDs "
              + expectedActiveTargets.keySet().stream()
                  .sorted()
                  .map(String::valueOf)
                  .collect(Collectors.joining(", ")),
          expectedActiveTargets.containsKey(limboDoc.getValue()));
    }

    for (DocumentKey expectedLimboDoc : expectedActiveLimboDocs) {
      assertTrue(
          "Expected doc to be in limbo, but was not: " + expectedLimboDoc,
          actualLimboDocs.containsKey(expectedLimboDoc));
      actualLimboDocs.remove(expectedLimboDoc);
    }
    assertTrue("Unexpected active docs in limbo: " + actualLimboDocs, actualLimboDocs.isEmpty());
  }

  private void validateEnqueuedLimboDocs() {
    Set<DocumentKey> actualLimboDocs =
        new HashSet<>(syncEngine.getEnqueuedLimboDocumentResolutions());

    for (DocumentKey key : actualLimboDocs) {
      assertTrue(
          "Found enqueued limbo doc "
              + key.getPath().canonicalString()
              + ", but it was not in the set of expected enqueued limbo documents ("
              + expectedEnqueuedLimboDocs.stream()
                  .sorted()
                  .map(String::valueOf)
                  .collect(Collectors.joining(", "))
              + ")",
          expectedEnqueuedLimboDocs.contains(key));
    }

    for (DocumentKey key : expectedEnqueuedLimboDocs) {
      assertTrue(
          "Expected doc "
              + key.getPath().canonicalString()
              + " to be enqueued for limbo resolution, but it was not in the queue ("
              + actualLimboDocs.stream()
                  .sorted()
                  .map(String::valueOf)
                  .collect(Collectors.joining(", "))
              + ")",
          actualLimboDocs.contains(key));
    }
  }

  private void validateActiveTargets() {
    if (!networkEnabled) {
      return;
    }

    // Create a copy so we can modify it in tests
    Map<Integer, TargetData> actualTargets = new HashMap<>(datastore.activeTargets());

    for (Map.Entry<Integer, Pair<List<TargetData>, String>> expected :
        expectedActiveTargets.entrySet()) {
      assertTrue(
          "Expected active target not found: " + expected.getValue(),
          actualTargets.containsKey(expected.getKey()));

      List<TargetData> expectedQueries = expected.getValue().first;
      TargetData expectedTarget = expectedQueries.get(0);
      TargetData actualTarget = actualTargets.get(expected.getKey());

      // TODO: validate the purpose of the target once it's possible to encode that in the
      // spec tests. For now, only validate properties that can be validated.
      // assertEquals(expectedTarget, actualTarget);
      assertEquals(expectedTarget.getTarget(), actualTarget.getTarget());
      assertEquals(expectedTarget.getTargetId(), actualTarget.getTargetId());
      assertEquals(expectedTarget.getSnapshotVersion(), actualTarget.getSnapshotVersion());
      assertEquals(
          expectedTarget.getResumeToken().toStringUtf8(),
          actualTarget.getResumeToken().toStringUtf8());

      actualTargets.remove(expected.getKey());
    }

    assertTrue("Unexpected active targets: " + actualTargets, actualTargets.isEmpty());
  }

  private void runSteps(JSONArray steps, JSONObject config) throws Exception {
    try {
      specSetUp(config);
      for (int i = 0; i < steps.length(); ++i) {
        JSONObject step = steps.getJSONObject(i);
        @Nullable JSONArray expectedSnapshotEvents = step.optJSONArray("expectedSnapshotEvents");
        step.remove("expectedSnapshotEvents");
        @Nullable JSONObject expectedState = step.optJSONObject("expectedState");
        step.remove("expectedState");
        int expectedSnapshotsInSyncEvents = step.optInt("expectedSnapshotsInSyncEvents");
        step.remove("expectedSnapshotsInSyncEvents");
        int expectedWaitForPendingWritesEvents = step.optInt("expectedWaitForPendingWritesEvents");
        step.remove("expectedWaitForPendingWritesEvents");

        log("    Doing step " + step);
        doStep(step);

        TaskCompletionSource<Void> drainBackgroundQueue = new TaskCompletionSource<>();
        backgroundExecutor.execute(() -> drainBackgroundQueue.setResult(null));
        waitFor(drainBackgroundQueue.getTask());

        if (expectedSnapshotEvents != null) {
          log("      Validating expected snapshot events " + expectedSnapshotEvents);
        }
        validateExpectedSnapshotEvents(expectedSnapshotEvents);
        if (expectedState != null) {
          log("      Validating state expectations " + expectedState);
        }
        validateExpectedState(expectedState);
        validateSnapshotsInSyncEvents(expectedSnapshotsInSyncEvents);
        validateWaitForPendingWritesEvents(expectedWaitForPendingWritesEvents);
        events.clear();
        acknowledgedDocs.clear();
        rejectedDocs.clear();
      }
    } finally {
      // Ensure that Persistence is torn down even if the test is failing due to a thrown exception
      // so that any open databases are closed. This is important when the LocalStore is backed by
      // SQLite because SQLite opens databases in exclusive mode. If tearDownForSpec were not called
      // after an exception then subsequent attempts to open the SQLite database will fail, making
      // it harder to zero in on the spec tests as a culprit.
      specTearDown();
    }
  }

  @Test
  @SuppressWarnings("DefaultCharset")
  public void testSpecTests() throws Exception {
    boolean ranAtLeastOneTest = false;

    // Enumerate the .json files containing the spec tests.
    List<Pair<String, JSONObject>> parsedSpecFiles = new ArrayList<>();
    File jsonDir = new File("src/test/resources/json");
    File[] jsonFiles = jsonDir.listFiles();
    Arrays.sort(jsonFiles);
    boolean exclusiveMode = false;
    for (File f : jsonFiles) {
      if (!f.toString().endsWith(".json")) {
        continue;
      }

      // Read the file into a string.
      StringBuilder builder = new StringBuilder();
      FileReader fr = new FileReader(f);
      BufferedReader reader = new BufferedReader(fr);
      Stream<String> lines = reader.lines();
      lines.forEach(builder::append);
      String json = builder.toString();
      JSONObject fileJSON = new JSONObject(json);
      exclusiveMode = exclusiveMode || anyTestsAreMarkedExclusive(fileJSON);
      parsedSpecFiles.add(new Pair<>(f.getName(), fileJSON));
    }

    String testNameFilterFromSystemProperty = emptyToNull(System.getProperty(TEST_FILTER_PROPERTY));
    Pattern testNameFilter;
    if (testNameFilterFromSystemProperty == null) {
      testNameFilter = null;
    } else {
      exclusiveMode = true;
      testNameFilter = Pattern.compile(testNameFilterFromSystemProperty);
    }

    int testPassCount = 0;
    int testSkipCount = 0;

    for (Pair<String, JSONObject> parsedSpecFile : parsedSpecFiles) {
      String fileName = parsedSpecFile.first;
      JSONObject fileJSON = parsedSpecFile.second;

      // Print the names of the files and tests regardless of whether verbose logging is enabled.
      info("Spec test file: " + fileName);

      // Iterate over the tests in the file and run them.
      Iterator<String> keys = fileJSON.keys();
      while (keys.hasNext()) {
        JSONObject testJSON = fileJSON.getJSONObject(keys.next());
        String describeName = testJSON.getString("describeName");
        String itName = testJSON.getString("itName");
        String name = describeName + " " + itName;
        JSONObject config = testJSON.getJSONObject("config");
        JSONArray steps = testJSON.getJSONArray("steps");
        Set<String> tags = getTestTags(testJSON);

        boolean runTest;
        if (!shouldRunTest(tags)) {
          runTest = false;
        } else if (!exclusiveMode) {
          runTest = true;
        } else if (tags.contains(EXCLUSIVE_TAG)) {
          runTest = true;
        } else if (testNameFilter != null) {
          runTest = testNameFilter.matcher(name).find();
        } else {
          runTest = false;
        }

        boolean measureRuntime = tags.contains(BENCHMARK_TAG);
        if (runTest) {
          long start = System.currentTimeMillis();
          try {
            info("Spec test: " + name);
            runSteps(steps, config);
            ranAtLeastOneTest = true;
            testPassCount++;
          } catch (AssertionError e) {
            throw new AssertionError("Spec test failure: " + name + " (" + fileName + ")", e);
          }
          long end = System.currentTimeMillis();
          if (measureRuntime) {
            info("Runtime: " + (end - start) + " ms");
          }
        } else {
          testSkipCount++;
          info("  [SKIPPED] Spec test: " + name);
        }
      }
    }
    info(getClass().getName() + " completed; pass=" + testPassCount + " skip=" + testSkipCount);
    assertTrue(ranAtLeastOneTest);
  }

  private static boolean anyTestsAreMarkedExclusive(JSONObject fileJSON) throws JSONException {
    Iterator<String> keys = fileJSON.keys();
    while (keys.hasNext()) {
      JSONObject testJSON = fileJSON.getJSONObject(keys.next());
      if (getTestTags(testJSON).contains(EXCLUSIVE_TAG)) {
        return true;
      }
    }
    return false;
  }

  /** Called before executing each test to see if it should be run. */
  private boolean shouldRunTest(Set<String> tags) {
    return shouldRun(tags);
  }

  private static Set<String> getTestTags(JSONObject testJSON) throws JSONException {
    JSONArray tagsJSON = testJSON.getJSONArray("tags");
    HashSet<String> tags = new HashSet<>();
    for (int i = 0; i < tagsJSON.length(); i++) {
      tags.add(tagsJSON.getString(i));
    }
    return tags;
  }

  //
  // RemoteStoreCallback Methods
  //

  @Override
  public void handleRemoteEvent(RemoteEvent remoteEvent) {
    syncEngine.handleRemoteEvent(remoteEvent);
  }

  @Override
  public void handleRejectedListen(int targetId, Status error) {
    syncEngine.handleRejectedListen(targetId, error);
  }

  @Override
  public void handleSuccessfulWrite(MutationBatchResult mutationBatchResult) {
    syncEngine.handleSuccessfulWrite(mutationBatchResult);
  }

  @Override
  public void handleRejectedWrite(int batchId, Status error) {
    syncEngine.handleRejectedWrite(batchId, error);
  }

  @Override
  public ImmutableSortedSet<DocumentKey> getRemoteKeysForTarget(int targetId) {
    return syncEngine.getRemoteKeysForTarget(targetId);
  }
}
