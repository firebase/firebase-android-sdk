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

import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/** Class for maintaining strings queued in shared preference. */
final class SharedPreferencesQueue {

  private final SharedPreferences sharedPreferences;
  private final String queueName;
  private final String itemSeparator;

  @GuardedBy("internalQueue")
  private final ArrayDeque<String> internalQueue = new ArrayDeque<>();

  private final Executor syncExecutor;

  @GuardedBy("internalQueue")
  private boolean bulkOperation = false;

  /**
   * Constructor.
   *
   * @param sharedPreferences shared preferences
   * @param queueName preferences key to store and retrieve the queue
   * @param itemSeparator character to separate multiple items
   * @param syncExecutor executor for performing sync operations
   */
  private SharedPreferencesQueue(
      SharedPreferences sharedPreferences,
      String queueName,
      String itemSeparator,
      Executor syncExecutor) {
    this.sharedPreferences = sharedPreferences;
    this.queueName = queueName;
    this.itemSeparator = itemSeparator;
    this.syncExecutor = syncExecutor;
  }

  /**
   * Creates new instance of a SharedPreferencesQueue.
   *
   * @param sharedPreferences shared preferences
   * @param queueName preferences key to store and retrieve the queue
   * @param itemSeparator character to separate multiple items
   * @param syncExecutor executor for performing sync operations
   * @return SharedPreferencesQueue instance
   */
  @WorkerThread
  static SharedPreferencesQueue createInstance(
      SharedPreferences sharedPreferences,
      String queueName,
      String itemSeparator,
      Executor syncExecutor) {
    SharedPreferencesQueue queue =
        new SharedPreferencesQueue(sharedPreferences, queueName, itemSeparator, syncExecutor);
    queue.initQueue();
    return queue;
  }

  @WorkerThread
  private void initQueue() {
    synchronized (internalQueue) {
      internalQueue.clear();
      String queue = sharedPreferences.getString(queueName, "");
      if (TextUtils.isEmpty(queue) || !queue.contains(itemSeparator)) {
        return;
      }
      String[] queueItems = queue.split(itemSeparator, -1);

      if (queueItems.length == 0) {
        Log.e(TAG, "Corrupted queue. Please check the queue contents and item separator provided");
      }

      for (String item : queueItems) {
        if (!TextUtils.isEmpty(item)) {
          internalQueue.add(item);
        }
      }
    }
  }

  @NonNull
  public List<String> toList() {
    synchronized (internalQueue) {
      return new ArrayList<>(internalQueue);
    }
  }

  /**
   * Adds item to queue.
   *
   * <p>If contains item separator, this method will be no-op
   *
   * @param item item to be added.
   * @return {@code true} if added, else {@code false}.
   */
  public boolean add(@NonNull String item) {
    if (TextUtils.isEmpty(item) || item.contains(itemSeparator)) {
      return false;
    }
    synchronized (internalQueue) {
      return checkAndSyncState(internalQueue.add(item));
    }
  }

  @GuardedBy("internalQueue")
  private String checkAndSyncState(String transactionValue) {
    checkAndSyncState(transactionValue != null);
    return transactionValue;
  }

  /**
   * Checks for valid transaction and call sync if needed.
   *
   * @param transactionState whether its valid transaction
   */
  @GuardedBy("internalQueue")
  private boolean checkAndSyncState(boolean transactionState) {
    // If the transaction is valid and if its not part of bulk operation, then only we will be
    // calling sync.
    if (transactionState && !bulkOperation) {
      syncStateAsync();
    }
    return transactionState;
  }

  private void syncStateAsync() {
    syncExecutor.execute(this::syncState);
  }

  @WorkerThread
  private void syncState() {
    synchronized (internalQueue) {
      sharedPreferences.edit().putString(queueName, serialize()).commit();
    }
  }

  @GuardedBy("internalQueue")
  @NonNull
  public String serialize() {
    StringBuilder builder = new StringBuilder();
    for (String item : internalQueue) {
      builder.append(item).append(itemSeparator);
    }
    return builder.toString();
  }

  /** Use this for bulk operation followed by {@link SharedPreferencesQueue#commitTransaction()}. */
  @GuardedBy("internalQueue")
  public void beginTransaction() {
    bulkOperation = true;
  }

  /** Commits bulk operation transactions. See {@link SharedPreferencesQueue#beginTransaction()}. */
  @GuardedBy("internalQueue")
  public void commitTransaction() {
    bulkOperation = false;
    syncStateAsync();
  }

  public boolean remove(@Nullable Object o) {
    synchronized (internalQueue) {
      return checkAndSyncState(internalQueue.remove(o));
    }
  }

  public String remove() {
    synchronized (internalQueue) {
      return checkAndSyncState(internalQueue.remove());
    }
  }

  public void clear() {
    synchronized (internalQueue) {
      internalQueue.clear();
      checkAndSyncState(true);
    }
  }

  @Nullable
  public String peek() {
    synchronized (internalQueue) {
      return internalQueue.peek();
    }
  }

  public int size() {
    synchronized (internalQueue) {
      return internalQueue.size();
    }
  }
}
