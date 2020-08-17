// Copyright 2020 Google LLC
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
package com.google.firebase.messaging;

import static com.google.firebase.messaging.Constants.TAG;
import static com.google.firebase.messaging.TopicOperation.TopicOperations.OPERATION_SUBSCRIBE;
import static com.google.firebase.messaging.TopicOperation.TopicOperations.OPERATION_UNSUBSCRIBE;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.SECONDS;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.collection.ArrayMap;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.heartbeatinfo.HeartBeatInfo;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.GmsRpc;
import com.google.firebase.iid.InstanceIdResult;
import com.google.firebase.iid.Metadata;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.platforminfo.UserAgentPublisher;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeoutException;

/**
 * Manages pending topics subscriptions and unsubscriptions.
 *
 * <p>Pending requests are persisted in the store, and executed by {@link #syncTopics}.
 */
class TopicsSubscriber {

  static final String ERROR_INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";
  static final String ERROR_SERVICE_NOT_AVAILABLE = "SERVICE_NOT_AVAILABLE";

  private static final long RPC_TIMEOUT_SEC = 30;
  private static final long MIN_DELAY_SEC = 30;
  private static final long MAX_DELAY_SEC = HOURS.toSeconds(8);

  private final FirebaseInstanceId iid;
  private final Context context;
  private final Metadata metadata;
  private final GmsRpc rpc;

  @GuardedBy("pendingOperations")
  private final Map<String, ArrayDeque<TaskCompletionSource<Void>>> pendingOperations =
      new ArrayMap<>();

  private final ScheduledExecutorService syncExecutor;

  @GuardedBy("this")
  private boolean syncScheduledOrRunning = false;

  private final TopicsStore store;

  static Task<TopicsSubscriber> createInstance(
      FirebaseApp firebaseApp,
      FirebaseInstanceId iid,
      Metadata metadata,
      UserAgentPublisher userAgentPublisher,
      HeartBeatInfo heartBeatInfo,
      FirebaseInstallationsApi firebaseInstallationsApi,
      Context context,
      @NonNull ScheduledExecutorService syncExecutor) {
    return createInstance(
        iid,
        metadata,
        new GmsRpc(
            firebaseApp, metadata, userAgentPublisher, heartBeatInfo, firebaseInstallationsApi),
        context,
        syncExecutor);
  }

  @VisibleForTesting
  static Task<TopicsSubscriber> createInstance(
      FirebaseInstanceId iid,
      Metadata metadata,
      GmsRpc rpc,
      Context context,
      @NonNull ScheduledExecutorService syncExecutor) {
    return Tasks.call(
        syncExecutor,
        () -> {
          TopicsStore topicsStore = TopicsStore.getInstance(context, syncExecutor);
          TopicsSubscriber topicsSubscriber =
              new TopicsSubscriber(iid, metadata, topicsStore, rpc, context, syncExecutor);
          return topicsSubscriber;
        });
  }

  private TopicsSubscriber(
      FirebaseInstanceId iid,
      Metadata metadata,
      TopicsStore store,
      GmsRpc rpc,
      Context context,
      @NonNull ScheduledExecutorService syncExecutor) {
    this.iid = iid;
    this.metadata = metadata;
    this.store = store;
    this.rpc = rpc;
    this.context = context;
    this.syncExecutor = syncExecutor;
  }

  Task<Void> subscribeToTopic(String topic) {
    Task<Void> task = scheduleTopicOperation(TopicOperation.subscribe(topic));
    startTopicsSyncIfNecessary();
    return task;
  }

  Task<Void> unsubscribeFromTopic(String topic) {
    Task<Void> task = scheduleTopicOperation(TopicOperation.unsubscribe(topic));
    startTopicsSyncIfNecessary();
    return task;
  }

  @VisibleForTesting
  Task<Void> scheduleTopicOperation(TopicOperation topicOperation) {
    store.addTopicOperation(topicOperation);
    TaskCompletionSource<Void> taskCompletionSource = new TaskCompletionSource<>();
    addToPendingOperations(topicOperation, taskCompletionSource);
    return taskCompletionSource.getTask();
  }

  private void addToPendingOperations(
      TopicOperation topicOperation, TaskCompletionSource<Void> taskCompletionSource) {
    synchronized (pendingOperations) {
      ArrayDeque<TaskCompletionSource<Void>> list;
      String key = topicOperation.serialize();
      if (pendingOperations.containsKey(key)) {
        list = pendingOperations.get(key);
      } else {
        list = new ArrayDeque<>();
        pendingOperations.put(key, list);
      }
      list.add(taskCompletionSource);
    }
  }

  boolean hasPendingOperation() {
    return store.getNextTopicOperation() != null;
  }

  void startTopicsSyncIfNecessary() {
    if (hasPendingOperation()) {
      startSync();
    }
  }

  private void startSync() {
    if (!isSyncScheduledOrRunning()) {
      syncWithDelaySecondsInternal(0 /* start sync task now */);
    }
  }

  void syncWithDelaySecondsInternal(long delaySeconds) {
    long retryDelaySeconds = Math.min(Math.max(MIN_DELAY_SEC, delaySeconds * 2), MAX_DELAY_SEC);
    TopicsSyncTask syncTask = new TopicsSyncTask(this, context, metadata, retryDelaySeconds);
    scheduleSyncTaskWithDelaySeconds(syncTask, delaySeconds);
    setSyncScheduledOrRunning(true);
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  void scheduleSyncTaskWithDelaySeconds(Runnable task, long delaySeconds) {
    syncExecutor.schedule(task, delaySeconds, SECONDS);
  }

  /**
   * Syncs the topics on a worker thread. Called from a TopicsSyncTask.
   *
   * @return true if successful, false if needs to be reschedule
   * @throws IOException if topics sync failed and shouldn't retry. This can happen when:
   *     <li>Rpc request returned failure responses that indicate that we should not retry.
   */
  @WorkerThread
  boolean syncTopics() throws IOException {
    while (true) {
      TopicOperation pendingTopicOperation;
      synchronized (this) {
        pendingTopicOperation = store.getNextTopicOperation();
        if (pendingTopicOperation == null) {
          if (isDebugLogEnabled()) {
            Log.d(TAG, "topic sync succeeded");
          }

          return true;
        }
      }

      if (!performTopicOperation(pendingTopicOperation)) {
        return false;
      }

      // Topic operation succeeded or entry was invalid, complete the corresponding Task if it
      // exists, remove it and try the next
      store.removeTopicOperation(pendingTopicOperation);
      markCompletePendingOperation(pendingTopicOperation);
    }
  }

  private void markCompletePendingOperation(TopicOperation topicOperation) {
    synchronized (pendingOperations) {
      String key = topicOperation.serialize();
      if (!pendingOperations.containsKey(key)) {
        return;
      }

      ArrayDeque<TaskCompletionSource<Void>> list = pendingOperations.get(key);

      // Following poll operation will never returns null since once we have empty queue, we
      // are removing it from the pending operations.
      TaskCompletionSource<Void> taskCompletionSource = list.poll();

      if (taskCompletionSource != null) {
        taskCompletionSource.setResult(null);
      }
      if (list.isEmpty()) {
        pendingOperations.remove(key);
      }
    }
  }

  /**
   * Performs one topic operation.
   *
   * @return true if successful, false if needs to be rescheduled
   * @throws IOException on a hard failure that should not be retried. Hard failures are failures
   *     except {@link TopicsSubscriber#ERROR_SERVICE_NOT_AVAILABLE} and {@link
   *     TopicsSubscriber#ERROR_INTERNAL_SERVER_ERROR}
   */
  @WorkerThread
  boolean performTopicOperation(TopicOperation topicOperation) throws IOException {
    try {
      switch (topicOperation.getOperation()) {
        case OPERATION_SUBSCRIBE:
          blockingSubscribeToTopic(topicOperation.getTopic());
          if (isDebugLogEnabled()) {
            Log.d(TAG, "Subscribe to topic: " + topicOperation.getTopic() + " succeeded.");
          }
          break;
        case OPERATION_UNSUBSCRIBE:
          blockingUnsubscribeFromTopic(topicOperation.getTopic());
          if (isDebugLogEnabled()) {
            Log.d(TAG, "Unsubscribe from topic: " + topicOperation.getTopic() + " succeeded.");
          }
          break;
        default:
          // fall out
          if (isDebugLogEnabled()) {
            Log.d(TAG, "Unknown topic operation" + topicOperation + ".");
          }
      }
    } catch (IOException e) {
      // Operation failed, retry failed only if errors from backend are server related error
      if (GmsRpc.ERROR_SERVICE_NOT_AVAILABLE.equals(e.getMessage())
          || GmsRpc.ERROR_INTERNAL_SERVER_ERROR.equals(e.getMessage())) {
        Log.e(TAG, "Topic operation failed: " + e.getMessage() + ". Will retry Topic operation.");

        return false; // will retry
      } else if (e.getMessage() == null) {
        Log.e(TAG, "Topic operation failed without exception message. Will retry Topic operation.");

        return false; // will retry
      } else {
        // rethrow for SyncTask to log error and handle in its retry mechanism
        throw e;
      }
    }

    return true;
  }

  @WorkerThread
  // TODO: (b/148494404) refactor so we only block once on this code path
  private void blockingSubscribeToTopic(String topic) throws IOException {
    InstanceIdResult instanceIdResult = awaitTask(iid.getInstanceId());
    awaitTask(rpc.subscribeToTopic(instanceIdResult.getId(), instanceIdResult.getToken(), topic));
  }

  @WorkerThread
  private void blockingUnsubscribeFromTopic(String topic) throws IOException {
    InstanceIdResult instanceIdResult = awaitTask(iid.getInstanceId());
    awaitTask(
        rpc.unsubscribeFromTopic(instanceIdResult.getId(), instanceIdResult.getToken(), topic));
  }

  /** Awaits an RPC task, rethrowing any IOExceptions or RuntimeExceptions. */
  @WorkerThread
  private static <T> T awaitTask(Task<T> task) throws IOException {
    try {
      return Tasks.await(task, RPC_TIMEOUT_SEC, SECONDS);
    } catch (ExecutionException e) {
      // The underlying exception should always be an IOException or RuntimeException, which we
      // rethrow.
      Throwable cause = e.getCause();
      if (cause instanceof IOException) {
        throw (IOException) cause;
      } else if (cause instanceof RuntimeException) {
        throw (RuntimeException) cause;
      }
      // should not happen but for safety
      throw new IOException(e);
    } catch (InterruptedException | TimeoutException e) {
      throw new IOException(ERROR_SERVICE_NOT_AVAILABLE, e);
    }
  }

  synchronized boolean isSyncScheduledOrRunning() {
    return syncScheduledOrRunning;
  }

  synchronized void setSyncScheduledOrRunning(boolean value) {
    syncScheduledOrRunning = value;
  }

  static boolean isDebugLogEnabled() {
    // special workaround for Log.isLoggable being flaky in Android M: b/27572147
    return Log.isLoggable(TAG, Log.DEBUG)
        || (Build.VERSION.SDK_INT == Build.VERSION_CODES.M && Log.isLoggable(TAG, Log.DEBUG));
  }

  @VisibleForTesting
  TopicsStore getStore() {
    return store;
  }
}
