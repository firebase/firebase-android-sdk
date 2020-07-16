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

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.Executor;

/** Manages the on-disk topic operations. */
final class TopicsStore {
  // TODO(b/139083476): create FCM's own SharedPreference store topics. This involves creating a
  // pipeline to migrate the topics stored in the IID SharedPreference to FCM SharedPreference.
  // This file is also used by the legacy GCM / IID libraries.
  @VisibleForTesting static final String PREFERENCES = "com.google.android.gms.appid";
  @VisibleForTesting static final String KEY_TOPIC_OPERATIONS_QUEUE = "topic_operation_queue";

  private static final String DIVIDER_QUEUE_OPERATIONS = ",";

  @GuardedBy("TopicsStore.class")
  private static WeakReference<TopicsStore> topicsStoreWeakReference;

  private final SharedPreferences sharedPreferences;
  private SharedPreferencesQueue topicOperationsQueue;
  private final Executor syncExecutor;

  private TopicsStore(SharedPreferences sharedPrefs, Executor executor) {
    this.syncExecutor = executor;
    this.sharedPreferences = sharedPrefs;
  }

  @WorkerThread
  private synchronized void initStore() {
    topicOperationsQueue =
        SharedPreferencesQueue.createInstance(
            sharedPreferences, KEY_TOPIC_OPERATIONS_QUEUE, DIVIDER_QUEUE_OPERATIONS, syncExecutor);
  }

  /**
   * Provides instance of TopicsStore.
   *
   * <p>The instance is cached and would be returned for subsequent calls until cache is stale.
   *
   * @param context application context
   */
  @WorkerThread
  public static synchronized TopicsStore getInstance(Context context, Executor executor) {
    TopicsStore store = null;
    if (topicsStoreWeakReference != null) {
      store = topicsStoreWeakReference.get();
    }

    if (store == null) {
      SharedPreferences sharedPrefs =
          context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
      store = new TopicsStore(sharedPrefs, executor);
      store.initStore();
      topicsStoreWeakReference = new WeakReference<>(store);
    }
    return store;
  }

  /** Test only method to clear cached instances of TopicStore. */
  @VisibleForTesting
  static synchronized void clearCaches() {
    if (topicsStoreWeakReference != null) {
      topicsStoreWeakReference.clear();
    }
  }

  /** Returns the next topic operation to perform. */
  @Nullable
  synchronized TopicOperation getNextTopicOperation() {
    String entry = topicOperationsQueue.peek();
    return TopicOperation.from(entry);
  }

  /**
   * Adds a topic operation to the operations queue.
   *
   * @param topicOperation Topic operation
   * @return true if success else false
   */
  synchronized boolean addTopicOperation(TopicOperation topicOperation) {
    return topicOperationsQueue.add(topicOperation.serialize());
  }

  /**
   * Removes topic operation.
   *
   * @param topicOperation operation to be removed
   * @return true if the operation is successfully removed, else false
   */
  synchronized boolean removeTopicOperation(TopicOperation topicOperation) {
    return topicOperationsQueue.remove(topicOperation.serialize());
  }

  /**
   * Fetches and removes topic operation from the head of the queue.
   *
   * <p>If the queue is empty, it will return null.
   */
  @Nullable
  synchronized TopicOperation pollTopicOperation() {
    try {
      return TopicOperation.from(topicOperationsQueue.remove());
    } catch (NoSuchElementException e) {
      Log.e(TAG, "Polling operation queue failed");
    }
    return null;
  }

  /**
   * Returns list of all saved operations.
   *
   * @return List of operations
   */
  @NonNull
  synchronized List<TopicOperation> getOperations() {
    List<String> items = topicOperationsQueue.toList();
    List<TopicOperation> operations = new ArrayList<>(items.size());
    for (String item : items) {
      operations.add(TopicOperation.from(item));
    }
    return operations;
  }

  /** Clears topic operations queue. */
  synchronized void clearTopicOperations() {
    topicOperationsQueue.clear();
  }
}
