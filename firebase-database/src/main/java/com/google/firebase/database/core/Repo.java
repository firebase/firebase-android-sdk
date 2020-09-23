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

package com.google.firebase.database.core;

import static com.google.firebase.database.core.utilities.Utilities.hardAssert;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseException;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.InternalHelpers;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.annotations.NotNull;
import com.google.firebase.database.connection.HostInfo;
import com.google.firebase.database.connection.ListenHashProvider;
import com.google.firebase.database.connection.PersistentConnection;
import com.google.firebase.database.connection.RequestResultCallback;
import com.google.firebase.database.core.persistence.NoopPersistenceManager;
import com.google.firebase.database.core.persistence.PersistenceManager;
import com.google.firebase.database.core.utilities.DefaultClock;
import com.google.firebase.database.core.utilities.DefaultRunLoop;
import com.google.firebase.database.core.utilities.OffsetClock;
import com.google.firebase.database.core.utilities.Tree;
import com.google.firebase.database.core.view.Event;
import com.google.firebase.database.core.view.EventRaiser;
import com.google.firebase.database.core.view.QuerySpec;
import com.google.firebase.database.logging.LogWrapper;
import com.google.firebase.database.snapshot.ChildKey;
import com.google.firebase.database.snapshot.EmptyNode;
import com.google.firebase.database.snapshot.IndexedNode;
import com.google.firebase.database.snapshot.Node;
import com.google.firebase.database.snapshot.NodeUtilities;
import com.google.firebase.database.snapshot.RangeMerge;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class Repo implements PersistentConnection.Delegate {

  private static final String INTERRUPT_REASON = "repo_interrupt";

  private final RepoInfo repoInfo;
  private final OffsetClock serverClock = new OffsetClock(new DefaultClock(), 0);
  private PersistentConnection connection;
  private SnapshotHolder infoData;
  private SparseSnapshotTree onDisconnect;
  private Tree<List<TransactionData>> transactionQueueTree;
  private boolean hijackHash = false;
  private final EventRaiser eventRaiser;
  private final Context ctx;
  private final LogWrapper operationLogger;
  private final LogWrapper transactionLogger;
  private final LogWrapper dataLogger;
  public long dataUpdateCount = 0; // for testing.
  private long nextWriteId = 1;
  private SyncTree infoSyncTree;
  private SyncTree serverSyncTree;
  private FirebaseDatabase database;
  private boolean loggedTransactionPersistenceWarning = false;

  Repo(RepoInfo repoInfo, Context ctx, FirebaseDatabase database) {
    this.repoInfo = repoInfo;
    this.ctx = ctx;
    this.database = database;

    operationLogger = this.ctx.getLogger("RepoOperation");
    transactionLogger = this.ctx.getLogger("Transaction");
    dataLogger = this.ctx.getLogger("DataOperation");

    this.eventRaiser = new EventRaiser(this.ctx);

    // Kick off any expensive additional initialization
    scheduleNow(
        new Runnable() {
          @Override
          public void run() {
            deferredInitialization();
          }
        });
  }

  /**
   * Defers any initialization that is potentially expensive (e.g. disk access) and must be run on
   * the run loop
   */
  private void deferredInitialization() {
    HostInfo hostInfo = new HostInfo(repoInfo.host, repoInfo.namespace, repoInfo.secure);
    connection = ctx.newPersistentConnection(hostInfo, this);

    this.ctx
        .getAuthTokenProvider()
        .addTokenChangeListener(
            ((DefaultRunLoop) ctx.getRunLoop()).getExecutorService(),
            new AuthTokenProvider.TokenChangeListener() {
              // TODO: Remove this once AndroidAuthTokenProvider is updated to call the
              // other overload.
              @Override
              public void onTokenChange() {
                operationLogger.debug("Auth token changed, triggering auth token refresh");
                connection.refreshAuthToken();
              }

              @Override
              public void onTokenChange(String token) {
                operationLogger.debug("Auth token changed, triggering auth token refresh");
                connection.refreshAuthToken(token);
              }
            });

    // Open connection now so that by the time we are connected the deferred init has run
    // This relies on the fact that all callbacks run on repo's runloop.
    connection.initialize();

    PersistenceManager persistenceManager = ctx.getPersistenceManager(repoInfo.host);

    infoData = new SnapshotHolder();
    onDisconnect = new SparseSnapshotTree();

    transactionQueueTree = new Tree<List<TransactionData>>();

    infoSyncTree =
        new SyncTree(
            ctx,
            new NoopPersistenceManager(),
            new SyncTree.ListenProvider() {
              @Override
              public void startListening(
                  final QuerySpec query,
                  Tag tag,
                  final ListenHashProvider hash,
                  final SyncTree.CompletionListener onComplete) {
                scheduleNow(
                    new Runnable() {
                      @Override
                      public void run() {
                        // This is possibly a hack, but we have different semantics for .info
                        // endpoints. We don't raise null events on initial data...
                        final Node node = infoData.getNode(query.getPath());
                        if (!node.isEmpty()) {
                          List<? extends Event> infoEvents =
                              infoSyncTree.applyServerOverwrite(query.getPath(), node);
                          postEvents(infoEvents);
                          onComplete.onListenComplete(null);
                        }
                      }
                    });
              }

              @Override
              public void stopListening(QuerySpec query, Tag tag) {}
            });

    serverSyncTree =
        new SyncTree(
            ctx,
            persistenceManager,
            new SyncTree.ListenProvider() {
              @Override
              public void startListening(
                  QuerySpec query,
                  Tag tag,
                  ListenHashProvider hash,
                  final SyncTree.CompletionListener onListenComplete) {
                connection.listen(
                    query.getPath().asList(),
                    query.getParams().getWireProtocolParams(),
                    hash,
                    tag != null ? tag.getTagNumber() : null,
                    new RequestResultCallback() {
                      @Override
                      public void onRequestResult(String optErrorCode, String optErrorMessage) {
                        DatabaseError error = fromErrorCode(optErrorCode, optErrorMessage);
                        List<? extends Event> events = onListenComplete.onListenComplete(error);
                        postEvents(events);
                      }
                    });
              }

              @Override
              public void stopListening(QuerySpec query, Tag tag) {
                connection.unlisten(
                    query.getPath().asList(), query.getParams().getWireProtocolParams());
              }
            });

    restoreWrites(persistenceManager);

    updateInfo(Constants.DOT_INFO_AUTHENTICATED, false);
    updateInfo(Constants.DOT_INFO_CONNECTED, false);
  }

  private void restoreWrites(PersistenceManager persistenceManager) {
    List<UserWriteRecord> writes = persistenceManager.loadUserWrites();

    Map<String, Object> serverValues = ServerValues.generateServerValues(serverClock);
    long lastWriteId = Long.MIN_VALUE;
    for (final UserWriteRecord write : writes) {
      RequestResultCallback onComplete =
          new RequestResultCallback() {
            @Override
            public void onRequestResult(String optErrorCode, String optErrorMessage) {
              DatabaseError error = fromErrorCode(optErrorCode, optErrorMessage);
              warnIfWriteFailed("Persisted write", write.getPath(), error);
              ackWriteAndRerunTransactions(write.getWriteId(), write.getPath(), error);
            }
          };
      if (lastWriteId >= write.getWriteId()) {
        throw new IllegalStateException("Write ids were not in order.");
      }
      lastWriteId = write.getWriteId();
      nextWriteId = write.getWriteId() + 1;
      if (write.isOverwrite()) {
        if (operationLogger.logsDebug()) {
          operationLogger.debug("Restoring overwrite with id " + write.getWriteId());
        }
        connection.put(write.getPath().asList(), write.getOverwrite().getValue(true), onComplete);
        Node resolved =
            ServerValues.resolveDeferredValueSnapshot(
                write.getOverwrite(), serverSyncTree, write.getPath(), serverValues);
        serverSyncTree.applyUserOverwrite(
            write.getPath(),
            write.getOverwrite(),
            resolved,
            write.getWriteId(),
            /*visible=*/ true,
            /*persist=*/ false);
      } else {
        if (operationLogger.logsDebug()) {
          operationLogger.debug("Restoring merge with id " + write.getWriteId());
        }
        connection.merge(write.getPath().asList(), write.getMerge().getValue(true), onComplete);
        CompoundWrite resolved =
            ServerValues.resolveDeferredValueMerge(
                write.getMerge(), serverSyncTree, write.getPath(), serverValues);
        serverSyncTree.applyUserMerge(
            write.getPath(), write.getMerge(), resolved, write.getWriteId(), /*persist=*/ false);
      }
    }
  }

  public FirebaseDatabase getDatabase() {
    return this.database;
  }

  @Override
  public String toString() {
    return repoInfo.toString();
  }

  public RepoInfo getRepoInfo() {
    return this.repoInfo;
  }

  // Regarding the next three methods: scheduleNow, schedule, and postEvent:
  // Please use these methods rather than accessing the context directly. This ensures that the
  // context is correctly re-initialized if it was previously shut down. In practice, this means
  // that when a task is submitted, we will guarantee at least one thread in the core pool for the
  // run loop.

  public void scheduleNow(Runnable r) {
    ctx.requireStarted();
    ctx.getRunLoop().scheduleNow(r);
  }

  public void postEvent(Runnable r) {
    ctx.requireStarted();
    ctx.getEventTarget().postEvent(r);
  }

  private void postEvents(final List<? extends Event> events) {
    if (!events.isEmpty()) {
      this.eventRaiser.raiseEvents(events);
    }
  }

  public long getServerTime() {
    return serverClock.millis();
  }

  boolean hasListeners() {
    return !(this.infoSyncTree.isEmpty() && this.serverSyncTree.isEmpty());
  }

  // PersistentConnection.Delegate methods
  @SuppressWarnings("unchecked") // For the cast on rawMergedData
  @Override
  public void onDataUpdate(
      List<String> pathSegments, Object message, boolean isMerge, Long optTag) {
    Path path = new Path(pathSegments);
    if (operationLogger.logsDebug()) {
      operationLogger.debug("onDataUpdate: " + path);
    }
    if (dataLogger.logsDebug()) {
      operationLogger.debug("onDataUpdate: " + path + " " + message);
    }
    dataUpdateCount++; // For testing.

    List<? extends Event> events;
    try {
      if (optTag != null) {
        Tag tag = new Tag(optTag);
        if (isMerge) {
          Map<Path, Node> taggedChildren = new HashMap<Path, Node>();
          Map<String, Object> rawMergeData = (Map<String, Object>) message;
          for (Map.Entry<String, Object> entry : rawMergeData.entrySet()) {
            Node newChildNode = NodeUtilities.NodeFromJSON(entry.getValue());
            taggedChildren.put(new Path(entry.getKey()), newChildNode);
          }
          events = this.serverSyncTree.applyTaggedQueryMerge(path, taggedChildren, tag);
        } else {
          Node taggedSnap = NodeUtilities.NodeFromJSON(message);
          events = this.serverSyncTree.applyTaggedQueryOverwrite(path, taggedSnap, tag);
        }
      } else if (isMerge) {
        Map<Path, Node> changedChildren = new HashMap<Path, Node>();
        Map<String, Object> rawMergeData = (Map<String, Object>) message;
        for (Map.Entry<String, Object> entry : rawMergeData.entrySet()) {
          Node newChildNode = NodeUtilities.NodeFromJSON(entry.getValue());
          changedChildren.put(new Path(entry.getKey()), newChildNode);
        }
        events = this.serverSyncTree.applyServerMerge(path, changedChildren);
      } else {
        Node snap = NodeUtilities.NodeFromJSON(message);
        events = this.serverSyncTree.applyServerOverwrite(path, snap);
      }
      if (events.size() > 0) {
        // Since we have a listener outstanding for each transaction, receiving any events
        // is a proxy for some change having occurred.
        this.rerunTransactions(path);
      }

      postEvents(events);
    } catch (DatabaseException e) {
      operationLogger.error("FIREBASE INTERNAL ERROR", e);
    }
  }

  @Override
  public void onRangeMergeUpdate(
      List<String> pathSegments,
      List<com.google.firebase.database.connection.RangeMerge> merges,
      Long tagNumber) {
    Path path = new Path(pathSegments);
    if (operationLogger.logsDebug()) {
      operationLogger.debug("onRangeMergeUpdate: " + path);
    }
    if (dataLogger.logsDebug()) {
      operationLogger.debug("onRangeMergeUpdate: " + path + " " + merges);
    }
    dataUpdateCount++; // For testing.

    List<RangeMerge> parsedMerges = new ArrayList<RangeMerge>(merges.size());
    for (com.google.firebase.database.connection.RangeMerge merge : merges) {
      parsedMerges.add(new RangeMerge(merge));
    }

    List<? extends Event> events;
    if (tagNumber != null) {
      events = this.serverSyncTree.applyTaggedRangeMerges(path, parsedMerges, new Tag(tagNumber));
    } else {
      events = this.serverSyncTree.applyServerRangeMerges(path, parsedMerges);
    }
    if (events.size() > 0) {
      // Since we have a listener outstanding for each transaction, receiving any events
      // is a proxy for some change having occurred.
      this.rerunTransactions(path);
    }

    postEvents(events);
  }

  void callOnComplete(
      final DatabaseReference.CompletionListener onComplete,
      final DatabaseError error,
      final Path path) {
    if (onComplete != null) {
      final DatabaseReference ref;
      ChildKey last = path.getBack();
      if (last != null && last.isPriorityChildName()) {
        ref = InternalHelpers.createReference(this, path.getParent());
      } else {
        ref = InternalHelpers.createReference(this, path);
      }
      postEvent(
          new Runnable() {
            @Override
            public void run() {
              onComplete.onComplete(error, ref);
            }
          });
    }
  }

  private void ackWriteAndRerunTransactions(long writeId, Path path, DatabaseError error) {
    if (error != null && error.getCode() == DatabaseError.WRITE_CANCELED) {
      // This write was already removed, we just need to ignore it...
    } else {
      boolean success = error == null;
      List<? extends Event> clearEvents =
          serverSyncTree.ackUserWrite(writeId, !success, /*persist=*/ true, serverClock);
      if (clearEvents.size() > 0) {
        rerunTransactions(path);
      }
      postEvents(clearEvents);
    }
  }

  public void setValue(
      final Path path,
      Node newValueUnresolved,
      final DatabaseReference.CompletionListener onComplete) {
    if (operationLogger.logsDebug()) {
      operationLogger.debug("set: " + path);
    }
    if (dataLogger.logsDebug()) {
      dataLogger.debug("set: " + path + " " + newValueUnresolved);
    }

    Map<String, Object> serverValues = ServerValues.generateServerValues(serverClock);
    Node existing = serverSyncTree.calcCompleteEventCache(path, new ArrayList<>());
    Node newValue =
        ServerValues.resolveDeferredValueSnapshot(newValueUnresolved, existing, serverValues);

    final long writeId = this.getNextWriteId();
    List<? extends Event> events =
        this.serverSyncTree.applyUserOverwrite(
            path, newValueUnresolved, newValue, writeId, /*visible=*/ true, /*persist=*/ true);
    this.postEvents(events);

    connection.put(
        path.asList(),
        newValueUnresolved.getValue(true),
        new RequestResultCallback() {
          @Override
          public void onRequestResult(String optErrorCode, String optErrorMessage) {
            DatabaseError error = fromErrorCode(optErrorCode, optErrorMessage);
            warnIfWriteFailed("setValue", path, error);
            ackWriteAndRerunTransactions(writeId, path, error);
            callOnComplete(onComplete, error, path);
          }
        });

    Path affectedPath = abortTransactions(path, DatabaseError.OVERRIDDEN_BY_SET);
    this.rerunTransactions(affectedPath);
  }

  public void updateChildren(
      final Path path,
      CompoundWrite updates,
      final DatabaseReference.CompletionListener onComplete,
      Map<String, Object> unParsedUpdates) {
    if (operationLogger.logsDebug()) {
      operationLogger.debug("update: " + path);
    }
    if (dataLogger.logsDebug()) {
      dataLogger.debug("update: " + path + " " + unParsedUpdates);
    }
    if (updates.isEmpty()) {
      if (operationLogger.logsDebug()) {
        operationLogger.debug("update called with no changes. No-op");
      }
      // dispatch on complete
      callOnComplete(onComplete, null, path);
      return;
    }

    // Start with our existing data and merge each child into it.
    Map<String, Object> serverValues = ServerValues.generateServerValues(serverClock);
    CompoundWrite resolved =
        ServerValues.resolveDeferredValueMerge(updates, serverSyncTree, path, serverValues);

    final long writeId = this.getNextWriteId();
    List<? extends Event> events =
        this.serverSyncTree.applyUserMerge(path, updates, resolved, writeId, /*persist=*/ true);
    this.postEvents(events);

    // TODO: DatabaseReference.CompleteionListener isn't really appropriate (the DatabaseReference
    // param is meaningless).
    connection.merge(
        path.asList(),
        unParsedUpdates,
        new RequestResultCallback() {
          @Override
          public void onRequestResult(String optErrorCode, String optErrorMessage) {
            DatabaseError error = fromErrorCode(optErrorCode, optErrorMessage);
            warnIfWriteFailed("updateChildren", path, error);
            ackWriteAndRerunTransactions(writeId, path, error);
            callOnComplete(onComplete, error, path);
          }
        });

    for (Entry<Path, Node> update : updates) {
      Path pathFromRoot = path.child(update.getKey());
      Path affectedPath = abortTransactions(pathFromRoot, DatabaseError.OVERRIDDEN_BY_SET);
      rerunTransactions(affectedPath);
    }
  }

  public void purgeOutstandingWrites() {
    if (operationLogger.logsDebug()) {
      operationLogger.debug("Purging writes");
    }
    List<? extends Event> events = serverSyncTree.removeAllWrites();
    postEvents(events);
    // Abort any transactions
    abortTransactions(Path.getEmptyPath(), DatabaseError.WRITE_CANCELED);
    // Remove outstanding writes from connection
    connection.purgeOutstandingWrites();
  }

  public void removeEventCallback(@NotNull EventRegistration eventRegistration) {
    // These are guaranteed not to raise events, since we're not passing in a cancelError. However,
    // we can future-proof a little bit by handling the return values anyways.
    List<Event> events;
    if (Constants.DOT_INFO.equals(eventRegistration.getQuerySpec().getPath().getFront())) {
      events = infoSyncTree.removeEventRegistration(eventRegistration);
    } else {
      events = serverSyncTree.removeEventRegistration(eventRegistration);
    }
    this.postEvents(events);
  }

  public void onDisconnectSetValue(
      final Path path, final Node newValue, final DatabaseReference.CompletionListener onComplete) {
    connection.onDisconnectPut(
        path.asList(),
        newValue.getValue(true),
        new RequestResultCallback() {
          @Override
          public void onRequestResult(String optErrorCode, String optErrorMessage) {
            DatabaseError error = fromErrorCode(optErrorCode, optErrorMessage);
            warnIfWriteFailed("onDisconnect().setValue", path, error);
            if (error == null) {
              onDisconnect.remember(path, newValue);
            }
            callOnComplete(onComplete, error, path);
          }
        });
  }

  public void onDisconnectUpdate(
      final Path path,
      final Map<Path, Node> newChildren,
      final DatabaseReference.CompletionListener listener,
      Map<String, Object> unParsedUpdates) {
    connection.onDisconnectMerge(
        path.asList(),
        unParsedUpdates,
        new RequestResultCallback() {
          @Override
          public void onRequestResult(String optErrorCode, String optErrorMessage) {
            DatabaseError error = fromErrorCode(optErrorCode, optErrorMessage);
            warnIfWriteFailed("onDisconnect().updateChildren", path, error);
            if (error == null) {
              for (Map.Entry<Path, Node> entry : newChildren.entrySet()) {
                onDisconnect.remember(path.child(entry.getKey()), entry.getValue());
              }
            }
            callOnComplete(listener, error, path);
          }
        });
  }

  public void onDisconnectCancel(
      final Path path, final DatabaseReference.CompletionListener onComplete) {
    connection.onDisconnectCancel(
        path.asList(),
        new RequestResultCallback() {
          @Override
          public void onRequestResult(String optErrorCode, String optErrorMessage) {
            DatabaseError error = fromErrorCode(optErrorCode, optErrorMessage);
            if (error == null) {
              onDisconnect.forget(path);
            }
            callOnComplete(onComplete, error, path);
          }
        });
  }

  @Override
  public void onConnect() {
    onServerInfoUpdate(Constants.DOT_INFO_CONNECTED, true);
  }

  @Override
  public void onDisconnect() {
    onServerInfoUpdate(Constants.DOT_INFO_CONNECTED, false);
    runOnDisconnectEvents();
  }

  @Override
  public void onAuthStatus(boolean authOk) {
    onServerInfoUpdate(Constants.DOT_INFO_AUTHENTICATED, authOk);
  }

  public void onServerInfoUpdate(ChildKey key, Object value) {
    updateInfo(key, value);
  }

  @Override
  public void onServerInfoUpdate(Map<String, Object> updates) {
    for (Map.Entry<String, Object> entry : updates.entrySet()) {
      updateInfo(ChildKey.fromString(entry.getKey()), entry.getValue());
    }
  }

  void interrupt() {
    connection.interrupt(INTERRUPT_REASON);
  }

  void resume() {
    connection.resume(INTERRUPT_REASON);
  }

  public void addEventCallback(@NotNull EventRegistration eventRegistration) {
    List<? extends Event> events;
    ChildKey front = eventRegistration.getQuerySpec().getPath().getFront();
    if (front != null && front.equals(Constants.DOT_INFO)) {
      events = this.infoSyncTree.addEventRegistration(eventRegistration);
    } else {
      events = this.serverSyncTree.addEventRegistration(eventRegistration);
    }
    this.postEvents(events);
  }

  public void keepSynced(QuerySpec query, boolean keep) {
    hardAssert(query.getPath().isEmpty() || !query.getPath().getFront().equals(Constants.DOT_INFO));

    serverSyncTree.keepSynced(query, keep);
  }

  PersistentConnection getConnection() {
    return connection;
  }

  private void updateInfo(ChildKey childKey, Object value) {
    if (childKey.equals(Constants.DOT_INFO_SERVERTIME_OFFSET)) {
      serverClock.setOffset((Long) value);
    }

    Path path = new Path(Constants.DOT_INFO, childKey);
    try {
      Node node = NodeUtilities.NodeFromJSON(value);
      infoData.update(path, node);
      List<? extends Event> events = this.infoSyncTree.applyServerOverwrite(path, node);
      this.postEvents(events);
    } catch (DatabaseException e) {
      operationLogger.error("Failed to parse info update", e);
    }
  }

  private long getNextWriteId() {
    return this.nextWriteId++;
  }

  private void runOnDisconnectEvents() {
    Map<String, Object> serverValues = ServerValues.generateServerValues(serverClock);
    final List<Event> events = new ArrayList<Event>();

    onDisconnect.forEachTree(
        Path.getEmptyPath(),
        new SparseSnapshotTree.SparseSnapshotTreeVisitor() {
          @Override
          public void visitTree(Path prefixPath, Node node) {
            Node existing = serverSyncTree.calcCompleteEventCache(prefixPath, new ArrayList<>());
            Node resolvedNode =
                ServerValues.resolveDeferredValueSnapshot(node, existing, serverValues);
            events.addAll(serverSyncTree.applyServerOverwrite(prefixPath, resolvedNode));
            Path affectedPath = abortTransactions(prefixPath, DatabaseError.OVERRIDDEN_BY_SET);
            rerunTransactions(affectedPath);
          }
        });
    onDisconnect = new SparseSnapshotTree();
    this.postEvents(events);
  }

  private void warnIfWriteFailed(String writeType, Path path, DatabaseError error) {
    // DATA_STALE is a normal, expected error during transaction processing.
    if (error != null
        && !(error.getCode() == DatabaseError.DATA_STALE
            || error.getCode() == DatabaseError.WRITE_CANCELED)) {
      operationLogger.warn(writeType + " at " + path.toString() + " failed: " + error.toString());
    }
  }

  // Transaction code

  /**
   * If a transaction does not succeed after 25 retries, we abort it. Among other things this ensure
   * that if there's ever a bug causing a mismatch between client / server hashes for some data, we
   * won't retry indefinitely.
   */
  private static final int TRANSACTION_MAX_RETRIES = 25;

  private static final String TRANSACTION_TOO_MANY_RETRIES = "maxretries";
  private static final String TRANSACTION_OVERRIDE_BY_SET = "overriddenBySet";

  private enum TransactionStatus {
    INITIALIZING,
    // We've run the transaction and updated transactionResultData_ with the result, but it isn't
    // currently sent to the server.
    // A transaction will go from RUN -> SENT -> RUN if it comes back from the server as rejected
    // due to mismatched hash.
    RUN,
    // We've run the transaction and sent it to the server and it's currently outstanding (hasn't
    // come back as accepted or rejected yet).
    SENT,
    // Temporary state used to mark completed transactions (whether successful or aborted). The
    // transaction will be removed when we get a chance to prune completed ones.
    COMPLETED,
    // Used when an already-sent transaction needs to be aborted (e.g. due to a conflicting set()
    // call that was made). If it comes back as unsuccessful, we'll abort it.
    SENT_NEEDS_ABORT,
    // Temporary state used to mark transactions that need to be aborted.
    NEEDS_ABORT
  };

  private long transactionOrder = 0;

  private static class TransactionData implements Comparable<TransactionData> {
    private Path path;
    private Transaction.Handler handler;
    private ValueEventListener outstandingListener;
    private TransactionStatus status;
    private long order;
    private boolean applyLocally;
    private int retryCount;
    private DatabaseError abortReason;
    private long currentWriteId;
    private Node currentInputSnapshot;
    private Node currentOutputSnapshotRaw;
    private Node currentOutputSnapshotResolved;

    private TransactionData(
        Path path,
        Transaction.Handler handler,
        ValueEventListener outstandingListener,
        TransactionStatus status,
        boolean applyLocally,
        long order) {
      this.path = path;
      this.handler = handler;
      this.outstandingListener = outstandingListener;
      this.status = status;
      this.retryCount = 0;
      this.applyLocally = applyLocally;
      this.order = order;
      this.abortReason = null;
      this.currentInputSnapshot = null;
      this.currentOutputSnapshotRaw = null;
      this.currentOutputSnapshotResolved = null;
    }

    @Override
    public int compareTo(TransactionData o) {
      if (order < o.order) {
        return -1;
      } else if (order == o.order) {
        return 0;
      } else {
        return 1;
      }
    }
  }

  public void startTransaction(Path path, final Transaction.Handler handler, boolean applyLocally) {
    if (operationLogger.logsDebug()) {
      operationLogger.debug("transaction: " + path);
    }
    if (dataLogger.logsDebug()) {
      operationLogger.debug("transaction: " + path);
    }

    if (this.ctx.isPersistenceEnabled() && !loggedTransactionPersistenceWarning) {
      loggedTransactionPersistenceWarning = true;
      transactionLogger.info(
          "runTransaction() usage detected while persistence is enabled. Please be aware that "
              + "transactions *will not* be persisted across database restarts.  See "
              + "https://www.firebase.com/docs/android/guide/offline-capabilities.html"
              + "#section-handling-transactions-offline for more details.");
    }

    // make sure we're listening on this node
    // Note: we can't do this asynchronously. To preserve event ordering,
    // it has to be done in this block.  This is ok, this block is
    // guaranteed to be our own event loop
    DatabaseReference watchRef = InternalHelpers.createReference(this, path);
    ValueEventListener listener =
        new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            // No-op. We don't care, this is just to make sure we have a listener outstanding
          }

          @Override
          public void onCancelled(DatabaseError error) {
            // Also a no-op? We'll cancel the transaction in this case
          }
        };
    addEventCallback(new ValueEventRegistration(this, listener, watchRef.getSpec()));

    TransactionData transaction =
        new TransactionData(
            path,
            handler,
            listener,
            TransactionStatus.INITIALIZING,
            applyLocally,
            nextTransactionOrder());

    // Run transaction initially.
    Node currentState = this.getLatestState(path);
    transaction.currentInputSnapshot = currentState;
    MutableData mutableCurrent = InternalHelpers.createMutableData(currentState);

    DatabaseError error = null;
    Transaction.Result result;
    try {
      result = handler.doTransaction(mutableCurrent);
      if (result == null) {
        throw new NullPointerException("Transaction returned null as result");
      }
    } catch (Throwable e) {
      operationLogger.error("Caught Throwable.", e);
      error = DatabaseError.fromException(e);
      result = Transaction.abort();
    }
    if (!result.isSuccess()) {
      // Abort the transaction
      transaction.currentOutputSnapshotRaw = null;
      transaction.currentOutputSnapshotResolved = null;
      final DatabaseError innerClassError = error;
      final DataSnapshot snap =
          InternalHelpers.createDataSnapshot(
              watchRef, IndexedNode.from(transaction.currentInputSnapshot));
      postEvent(
          new Runnable() {
            @Override
            public void run() {
              handler.onComplete(innerClassError, false, snap);
            }
          });
    } else {
      // Mark as run and add to our queue.
      transaction.status = TransactionStatus.RUN;

      Tree<List<TransactionData>> queueNode = transactionQueueTree.subTree(path);
      List<TransactionData> nodeQueue = queueNode.getValue();
      if (nodeQueue == null) {
        nodeQueue = new ArrayList<TransactionData>();
      }
      nodeQueue.add(transaction);
      queueNode.setValue(nodeQueue);

      Map<String, Object> serverValues = ServerValues.generateServerValues(serverClock);
      Node newNodeUnresolved = result.getNode();
      Node newNode =
          ServerValues.resolveDeferredValueSnapshot(
              newNodeUnresolved, transaction.currentInputSnapshot, serverValues);

      transaction.currentOutputSnapshotRaw = newNodeUnresolved;
      transaction.currentOutputSnapshotResolved = newNode;
      transaction.currentWriteId = this.getNextWriteId();

      List<? extends Event> events =
          this.serverSyncTree.applyUserOverwrite(
              path,
              newNodeUnresolved,
              newNode,
              transaction.currentWriteId,
              /*visible=*/ applyLocally,
              /*persist=*/ false);
      this.postEvents(events);
      sendAllReadyTransactions();
    }
  }

  private Node getLatestState(Path path) {
    return this.getLatestState(path, new ArrayList<Long>());
  }

  private Node getLatestState(Path path, List<Long> excudeSets) {
    Node state = this.serverSyncTree.calcCompleteEventCache(path, excudeSets);
    if (state == null) {
      state = EmptyNode.Empty();
    }
    return state;
  }

  public void setHijackHash(boolean hijackHash) {
    this.hijackHash = hijackHash;
  }

  private void sendAllReadyTransactions() {
    Tree<List<TransactionData>> node = transactionQueueTree;

    pruneCompletedTransactions(node);
    sendReadyTransactions(node);
  }

  private void sendReadyTransactions(Tree<List<TransactionData>> node) {
    List<TransactionData> queue = node.getValue();
    if (queue != null) {
      queue = buildTransactionQueue(node);
      hardAssert(queue.size() > 0); // Sending zero length transaction queue

      Boolean allRun = true;
      for (TransactionData transaction : queue) {
        if (transaction.status != TransactionStatus.RUN) {
          allRun = false;
          break;
        }
      }
      // If they're all run (and not sent), we can send them.  Else, we must wait.
      if (allRun) {
        sendTransactionQueue(queue, node.getPath());
      }
    } else if (node.hasChildren()) {
      node.forEachChild(
          new Tree.TreeVisitor<List<TransactionData>>() {
            @Override
            public void visitTree(Tree<List<TransactionData>> tree) {
              sendReadyTransactions(tree);
            }
          });
    }
  }

  private void sendTransactionQueue(final List<TransactionData> queue, final Path path) {
    // Mark transactions as sent and increment retry count!
    List<Long> setsToIgnore = new ArrayList<Long>();
    for (TransactionData txn : queue) {
      setsToIgnore.add(txn.currentWriteId);
    }

    Node latestState = this.getLatestState(path, setsToIgnore);
    Node snapToSend = latestState;
    String latestHash = "badhash";
    if (!hijackHash) {
      latestHash = latestState.getHash();
    }

    for (TransactionData txn : queue) {
      hardAssert(
          txn.status
              == TransactionStatus.RUN); // sendTransactionQueue: items in queue should all be run.'
      txn.status = TransactionStatus.SENT;
      txn.retryCount++;
      Path relativePath = Path.getRelative(path, txn.path);
      // If we've gotten to this point, the output snapshot must be defined.
      snapToSend = snapToSend.updateChild(relativePath, txn.currentOutputSnapshotRaw);
    }

    Object dataToSend = snapToSend.getValue(true);

    final Repo repo = this;

    // Send the put.
    connection.compareAndPut(
        path.asList(),
        dataToSend,
        latestHash,
        new RequestResultCallback() {
          @Override
          public void onRequestResult(String optErrorCode, String optErrorMessage) {
            DatabaseError error = fromErrorCode(optErrorCode, optErrorMessage);
            warnIfWriteFailed("Transaction", path, error);
            List<Event> events = new ArrayList<Event>();

            if (error == null) {
              List<Runnable> callbacks = new ArrayList<Runnable>();
              for (final TransactionData txn : queue) {
                txn.status = TransactionStatus.COMPLETED;
                events.addAll(
                    serverSyncTree.ackUserWrite(
                        txn.currentWriteId, /*revert=*/ false, /*persist=*/ false, serverClock));

                // We never unset the output snapshot, and given that this
                // transaction is complete, it should be set
                Node node = txn.currentOutputSnapshotResolved;
                final DataSnapshot snap =
                    InternalHelpers.createDataSnapshot(
                        InternalHelpers.createReference(repo, txn.path), IndexedNode.from(node));

                callbacks.add(
                    new Runnable() {
                      @Override
                      public void run() {
                        txn.handler.onComplete(null, true, snap);
                      }
                    });
                // Remove the outstanding value listener that we added
                removeEventCallback(
                    new ValueEventRegistration(
                        Repo.this,
                        txn.outstandingListener,
                        QuerySpec.defaultQueryAtPath(txn.path)));
              }

              // Now remove the completed transactions
              pruneCompletedTransactions(transactionQueueTree.subTree(path));

              // There may be pending transactions that we can now send
              sendAllReadyTransactions();

              repo.postEvents(events);

              // Finally, run the callbacks
              for (int i = 0; i < callbacks.size(); ++i) {
                postEvent(callbacks.get(i));
              }
            } else {
              // transactions are no longer sent. Update their status appropriately
              if (error.getCode() == DatabaseError.DATA_STALE) {
                for (TransactionData transaction : queue) {
                  if (transaction.status == TransactionStatus.SENT_NEEDS_ABORT) {
                    transaction.status = TransactionStatus.NEEDS_ABORT;
                  } else {
                    transaction.status = TransactionStatus.RUN;
                  }
                }
              } else {
                for (TransactionData transaction : queue) {
                  transaction.status = TransactionStatus.NEEDS_ABORT;
                  transaction.abortReason = error;
                }
              }

              // since we reverted mergedData, we should re-run any remaining
              // transactions and raise events
              rerunTransactions(path);
            }
          }
        });
  }

  private void pruneCompletedTransactions(Tree<List<TransactionData>> node) {
    List<TransactionData> queue = node.getValue();
    if (queue != null) {
      int i = 0;
      while (i < queue.size()) {
        TransactionData transaction = queue.get(i);
        if (transaction.status == TransactionStatus.COMPLETED) {
          queue.remove(i);
        } else {
          i++;
        }
      }
      if (queue.size() > 0) {
        node.setValue(queue);
      } else {
        node.setValue(null);
      }
    }

    node.forEachChild(
        new Tree.TreeVisitor<List<TransactionData>>() {
          @Override
          public void visitTree(Tree<List<TransactionData>> tree) {
            pruneCompletedTransactions(tree);
          }
        });
  }

  private long nextTransactionOrder() {
    return transactionOrder++;
  }

  private Path rerunTransactions(Path changedPath) {
    Tree<List<TransactionData>> rootMostTransactionNode = getAncestorTransactionNode(changedPath);
    Path path = rootMostTransactionNode.getPath();

    List<TransactionData> queue = buildTransactionQueue(rootMostTransactionNode);
    rerunTransactionQueue(queue, path);

    return path;
  }

  private void rerunTransactionQueue(List<TransactionData> queue, Path path) {
    if (queue.isEmpty()) {
      return; // Nothing to do!
    }

    // Queue up the callbacks and fire them after cleaning up all of our transaction state, since
    // the callback could trigger more transactions or sets
    List<Runnable> callbacks = new ArrayList<Runnable>();

    // Ignore, by default, all of the sets in this queue, since we're re-running all of them.
    // However, we want to include the results of new sets triggered as part of this re-run, so we
    // don't want to ignore a range, just these specific sets.
    List<Long> setsToIgnore = new ArrayList<Long>();
    for (TransactionData transaction : queue) {
      setsToIgnore.add(transaction.currentWriteId);
    }

    for (final TransactionData transaction : queue) {
      Path relativePath = Path.getRelative(path, transaction.path);
      boolean abortTransaction = false;
      DatabaseError abortReason = null;
      List<Event> events = new ArrayList<Event>();

      hardAssert(relativePath != null); // rerunTransactionQueue: relativePath should not be null.

      if (transaction.status == TransactionStatus.NEEDS_ABORT) {
        abortTransaction = true;
        abortReason = transaction.abortReason;
        if (abortReason.getCode() != DatabaseError.WRITE_CANCELED) {
          events.addAll(
              serverSyncTree.ackUserWrite(
                  transaction.currentWriteId, /*revert=*/ true, /*persist=*/ false, serverClock));
        }
      } else if (transaction.status == TransactionStatus.RUN) {
        if (transaction.retryCount >= TRANSACTION_MAX_RETRIES) {
          abortTransaction = true;
          abortReason = DatabaseError.fromStatus(TRANSACTION_TOO_MANY_RETRIES);
          events.addAll(
              serverSyncTree.ackUserWrite(
                  transaction.currentWriteId, /*revert=*/ true, /*persist=*/ false, serverClock));
        } else {
          // This code reruns a transaction
          Node currentNode = this.getLatestState(transaction.path, setsToIgnore);
          transaction.currentInputSnapshot = currentNode;
          MutableData mutableCurrent = InternalHelpers.createMutableData(currentNode);
          DatabaseError error = null;
          Transaction.Result result;
          try {
            result = transaction.handler.doTransaction(mutableCurrent);
          } catch (Throwable e) {
            operationLogger.error("Caught Throwable.", e);
            error = DatabaseError.fromException(e);
            result = Transaction.abort();
          }
          if (result.isSuccess()) {
            Long oldWriteId = transaction.currentWriteId;
            Map<String, Object> serverValues = ServerValues.generateServerValues(serverClock);

            Node newDataNode = result.getNode();
            Node newNodeResolved =
                ServerValues.resolveDeferredValueSnapshot(newDataNode, currentNode, serverValues);

            transaction.currentOutputSnapshotRaw = newDataNode;
            transaction.currentOutputSnapshotResolved = newNodeResolved;
            transaction.currentWriteId = this.getNextWriteId();

            // Mutates setsToIgnore in place
            setsToIgnore.remove(oldWriteId);
            events.addAll(
                serverSyncTree.applyUserOverwrite(
                    transaction.path,
                    newDataNode,
                    newNodeResolved,
                    transaction.currentWriteId,
                    transaction.applyLocally,
                    /*persist=*/ false));
            events.addAll(
                serverSyncTree.ackUserWrite(
                    oldWriteId, /*revert=*/ true, /*persist=*/ false, serverClock));
          } else {
            // The user aborted the transaction. It's not an error, so we don't need to send them
            // one
            abortTransaction = true;
            abortReason = error;
            events.addAll(
                serverSyncTree.ackUserWrite(
                    transaction.currentWriteId, /*revert=*/ true, /*persist=*/ false, serverClock));
          }
        }
      }

      this.postEvents(events);

      if (abortTransaction) {
        // Abort
        transaction.status = TransactionStatus.COMPLETED;
        final DatabaseReference ref = InternalHelpers.createReference(this, transaction.path);

        // We set this field immediately, so it's safe to cast to an actual snapshot
        Node lastInput = transaction.currentInputSnapshot;
        // TODO: In the future, perhaps this should just be KeyIndex?
        final DataSnapshot snapshot =
            InternalHelpers.createDataSnapshot(ref, IndexedNode.from(lastInput));

        // Removing a callback can trigger pruning which can muck with mergedData/visibleData (as it
        // prunes data). So defer removing the callback until later.
        this.scheduleNow(
            new Runnable() {
              @Override
              public void run() {
                removeEventCallback(
                    new ValueEventRegistration(
                        Repo.this,
                        transaction.outstandingListener,
                        QuerySpec.defaultQueryAtPath(transaction.path)));
              }
            });

        final DatabaseError callbackError = abortReason;
        callbacks.add(
            new Runnable() {
              @Override
              public void run() {
                transaction.handler.onComplete(callbackError, false, snapshot);
              }
            });
      }
    }

    // Clean up completed transactions.
    pruneCompletedTransactions(transactionQueueTree);

    // Now fire callbacks, now that we're in a good, known state.
    for (int i = 0; i < callbacks.size(); ++i) {
      postEvent(callbacks.get(i));
    }

    // Try to send the transaction result to the server.
    sendAllReadyTransactions();
  }

  private Tree<List<TransactionData>> getAncestorTransactionNode(Path path) {
    Tree<List<TransactionData>> transactionNode = transactionQueueTree;
    while (!path.isEmpty() && transactionNode.getValue() == null) {
      transactionNode = transactionNode.subTree(new Path(path.getFront()));
      path = path.popFront();
    }

    return transactionNode;
  }

  private List<TransactionData> buildTransactionQueue(Tree<List<TransactionData>> transactionNode) {
    List<TransactionData> queue = new ArrayList<TransactionData>();
    aggregateTransactionQueues(queue, transactionNode);

    Collections.sort(queue);

    return queue;
  }

  private void aggregateTransactionQueues(
      final List<TransactionData> queue, Tree<List<TransactionData>> node) {
    List<TransactionData> childQueue = node.getValue();
    if (childQueue != null) {
      queue.addAll(childQueue);
    }

    node.forEachChild(
        new Tree.TreeVisitor<List<TransactionData>>() {
          @Override
          public void visitTree(Tree<List<TransactionData>> tree) {
            aggregateTransactionQueues(queue, tree);
          }
        });
  }

  private Path abortTransactions(Path path, final int reason) {
    Path affectedPath = getAncestorTransactionNode(path).getPath();

    if (transactionLogger.logsDebug()) {
      operationLogger.debug(
          "Aborting transactions for path: " + path + ". Affected: " + affectedPath);
    }

    Tree<List<TransactionData>> transactionNode = transactionQueueTree.subTree(path);
    transactionNode.forEachAncestor(
        new Tree.TreeFilter<List<TransactionData>>() {
          @Override
          public boolean filterTreeNode(Tree<List<TransactionData>> tree) {
            abortTransactionsAtNode(tree, reason);
            return false;
          }
        });

    abortTransactionsAtNode(transactionNode, reason);

    transactionNode.forEachDescendant(
        new Tree.TreeVisitor<List<TransactionData>>() {
          @Override
          public void visitTree(Tree<List<TransactionData>> tree) {
            abortTransactionsAtNode(tree, reason);
          }
        });

    return affectedPath;
  }

  private void abortTransactionsAtNode(Tree<List<TransactionData>> node, int reason) {
    List<TransactionData> queue = node.getValue();
    List<Event> events = new ArrayList<Event>();

    if (queue != null) {
      List<Runnable> callbacks = new ArrayList<Runnable>();
      final DatabaseError abortError;
      if (reason == DatabaseError.OVERRIDDEN_BY_SET) {
        abortError = DatabaseError.fromStatus(TRANSACTION_OVERRIDE_BY_SET);
      } else {
        hardAssert(
            reason == DatabaseError.WRITE_CANCELED, "Unknown transaction abort reason: " + reason);
        abortError = DatabaseError.fromCode(DatabaseError.WRITE_CANCELED);
      }

      int lastSent = -1;
      for (int i = 0; i < queue.size(); ++i) {
        final TransactionData transaction = queue.get(i);
        if (transaction.status == TransactionStatus.SENT_NEEDS_ABORT) {
          // No-op. Already marked
        } else if (transaction.status == TransactionStatus.SENT) {
          hardAssert(lastSent == i - 1); // All SENT items should be at beginning of queue.
          lastSent = i;
          // Mark transaction for abort when it comes back.
          transaction.status = TransactionStatus.SENT_NEEDS_ABORT;
          transaction.abortReason = abortError;
        } else {
          hardAssert(
              transaction.status
                  == TransactionStatus.RUN); // Unexpected transaction status in abort
          // We can abort this immediately.
          removeEventCallback(
              new ValueEventRegistration(
                  Repo.this,
                  transaction.outstandingListener,
                  QuerySpec.defaultQueryAtPath(transaction.path)));
          if (reason == DatabaseError.OVERRIDDEN_BY_SET) {
            events.addAll(
                serverSyncTree.ackUserWrite(
                    transaction.currentWriteId, /*revert=*/ true, /*persist=*/ false, serverClock));
          } else {
            hardAssert(
                reason == DatabaseError.WRITE_CANCELED,
                "Unknown transaction abort reason: " + reason);
            // If it was cancelled, it was already removed from the sync tree
          }
          callbacks.add(
              new Runnable() {
                @Override
                public void run() {
                  transaction.handler.onComplete(abortError, false, null);
                }
              });
        }
      }

      if (lastSent == -1) {
        // We're not waiting for any sent transactions. We can clear the queue
        node.setValue(null);
      } else {
        // Remove the transactions we aborted
        node.setValue(queue.subList(0, lastSent + 1));
      }

      // Now fire the callbacks.
      this.postEvents(events);
      for (Runnable r : callbacks) {
        postEvent(r);
      }
    }
  }

  // Package private for testing purposes only
  SyncTree getServerSyncTree() {
    return serverSyncTree;
  }

  // Package private for testing purposes only
  SyncTree getInfoSyncTree() {
    return infoSyncTree;
  }

  private static DatabaseError fromErrorCode(String optErrorCode, String optErrorReason) {
    if (optErrorCode != null) {
      return DatabaseError.fromStatus(optErrorCode, optErrorReason);
    } else {
      return null;
    }
  }
}
