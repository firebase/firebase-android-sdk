// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.remoteconfig.internal;

import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.TAG;

import android.util.Log;
import androidx.annotation.AnyThread;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.tasks.OnCanceledListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Cache client for managing an in-memory {@link ConfigContainer} backed by disk.
 *
 * <p>The in-memory and file {@link ConfigContainer}s are always synced by the client, so the
 * in-memory container returned by the client will be the same as the container stored in disk.
 *
 * <p>Since there's a one to one mapping between files and storage clients, as well between files
 * and cache clients, and every method in both clients is synchronized, two threads in the same
 * process should never write to the same file simultaneously.
 *
 * @author Miraziz Yusupov
 */
@AnyThread
public class ConfigCacheClient {
  /** How long a method should block on a file read. */
  static final long DISK_READ_TIMEOUT_IN_SECONDS = 5L;

  @GuardedBy("ConfigCacheClient.class")
  private static final Map<String, ConfigCacheClient> clientInstances = new HashMap<>();

  private final ExecutorService executorService;
  private final ConfigStorageClient storageClient;

  /**
   * Represents the {@link ConfigContainer} stored in disk. If the value is null, then there have
   * been no file reads or writes yet.
   */
  @GuardedBy("this")
  @Nullable
  private Task<ConfigContainer> cachedContainerTask;

  /**
   * Creates a new cache client that executes async calls through {@code executorService} and is
   * backed by {@code storageClient}.
   */
  private ConfigCacheClient(ExecutorService executorService, ConfigStorageClient storageClient) {
    this.executorService = executorService;
    this.storageClient = storageClient;

    cachedContainerTask = null;
  }

  /**
   * Returns the cached {@link ConfigContainer}, blocking on a file read if necessary.
   *
   * <p>If no {@link ConfigContainer} has been read from disk yet, blocks on a {@link #get()} call.
   * Returns null if the file read does not succeed within {@link #DISK_READ_TIMEOUT_IN_SECONDS}.
   */
  @Nullable
  public ConfigContainer getBlocking() {
    return getBlocking(DISK_READ_TIMEOUT_IN_SECONDS);
  }

  @VisibleForTesting
  @Nullable
  ConfigContainer getBlocking(long diskReadTimeoutInSeconds) {
    synchronized (this) {
      if (cachedContainerTask != null && cachedContainerTask.isSuccessful()) {
        return cachedContainerTask.getResult();
      }
    }

    try {
      return await(get(), diskReadTimeoutInSeconds, TimeUnit.SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      Log.d(TAG, "Reading from storage file failed.", e);
      return null;
    }
  }

  /**
   * Writes {@code configContainer} to disk and caches it to memory if the write is successful.
   *
   * @param configContainer the container to write to disk.
   * @return A {@link Task} with the {@link ConfigContainer} that was written to disk.
   */
  public Task<ConfigContainer> put(ConfigContainer configContainer) {
    return put(configContainer, /*shouldUpdateInMemoryContainer=*/ true);
  }

  /**
   * Writes {@code configContainer} to disk and caches it to memory if the write is successful.
   *
   * @param configContainer the container to write to disk.
   * @param shouldUpdateInMemoryContainer whether the in-memory container should be updated on a
   *     successful file write.
   * @return A {@link Task} with the {@link ConfigContainer} that was written to disk.
   */
  public Task<ConfigContainer> put(
      ConfigContainer configContainer, boolean shouldUpdateInMemoryContainer) {
    return Tasks.call(executorService, () -> storageClient.write(configContainer))
        .onSuccessTask(
            executorService,
            (unusedVoid) -> {
              if (shouldUpdateInMemoryContainer) {
                updateInMemoryConfigContainer(configContainer);
              }
              return Tasks.forResult(configContainer);
            });
  }

  /**
   * Returns the cached {@link Task} that contains a {@link ConfigContainer}.
   *
   * <p>If no {@link Task} is cached or the cached {@link Task} has failed, makes an async call to
   * read the container in disk and sets the cache to the resulting {@link Task}.
   */
  public synchronized Task<ConfigContainer> get() {
    /*
     * The first call to this method will encounter a null cachedContainerTask, so the code below
     * will start an async task and assign the result to cachedContainerTask. Since this method is
     * synchronized, all subsequent calls to get() will be blocked on the first call, after which
     * point cachedContainerTask will be non-null. So, instead of starting their own async tasks,
     * the subsequent get() calls will simply return the ongoing cachedContainerTask.
     *
     * In the case of file I/O failure, the first get() method to recognize that the current
     * cachedContainerTask failed will start a new async task. All other get() calls will be
     * blocked on that first get(), after which point cachedContainerTask will be a non-null
     * non-failing task again.
     *
     * If clear() is called, the next get() call will see a null cachedContainerTask and start
     * an async task as described above.
     *
     * If no clears are called, there will never be more than 1 call to storageClient::read from
     * this instance. Otherwise, in all cases, the number of active async calls to
     * storageClient::read will be at most one higher than the number of clear() calls made so far.
     */
    if (cachedContainerTask == null
        || (cachedContainerTask.isComplete() && !cachedContainerTask.isSuccessful())) {
      cachedContainerTask = Tasks.call(executorService, storageClient::read);
    }
    return cachedContainerTask;
  }

  /** Clears the cache and the {@link ConfigContainer} stored in disk. */
  public void clear() {
    synchronized (this) {
      /*
       * A null Task means the file has not been loaded yet, which will cause the get() method to
       * start a new file read. So, to prevent unnecessary reads of an empty file, set to a Task
       * with a null value, which will simply return a null container when get() is called.
       */
      cachedContainerTask = Tasks.forResult(null);
    }
    storageClient.clear();
  }

  /** Sets {@link #cachedContainerTask} to a {@link Task} containing {@code configContainer}. */
  private synchronized void updateInMemoryConfigContainer(ConfigContainer configContainer) {
    cachedContainerTask = Tasks.forResult(configContainer);
  }

  @VisibleForTesting
  @Nullable
  synchronized Task<ConfigContainer> getCachedContainerTask() {
    return cachedContainerTask;
  }

  /**
   * Returns an instance of {@link ConfigCacheClient} for the given {@link Executor} and {@link
   * ConfigStorageClient}. The same instance is always returned for all calls with the same
   * underlying file name.
   */
  public static synchronized ConfigCacheClient getInstance(
      ExecutorService executorService, ConfigStorageClient storageClient) {
    String fileName = storageClient.getFileName();
    if (!clientInstances.containsKey(fileName)) {
      clientInstances.put(fileName, new ConfigCacheClient(executorService, storageClient));
    }
    return clientInstances.get(fileName);
  }

  @VisibleForTesting
  public static synchronized void clearInstancesForTest() {
    clientInstances.clear();
  }

  /**
   * Reimplementation of {@link Tasks#await(Task, long, TimeUnit)} because that method has a
   * precondition that fails when run on the main thread.
   *
   * <p>This blocking method is required because the current FRC API has synchronous getters that
   * read from a cache that is loaded from disk. In other words, the synchronous methods rely on an
   * async task, so the getters have to block at some point.
   *
   * <p>Until the next breaking change in the API, this use case must be implemented, even though it
   * is against Android best practices.
   */
  private static <TResult> TResult await(Task<TResult> task, long timeout, TimeUnit unit)
      throws ExecutionException, InterruptedException, TimeoutException {
    AwaitListener<TResult> waiter = new AwaitListener<>();

    task.addOnSuccessListener(DIRECT_EXECUTOR, waiter);
    task.addOnFailureListener(DIRECT_EXECUTOR, waiter);
    task.addOnCanceledListener(DIRECT_EXECUTOR, waiter);

    if (!waiter.await(timeout, unit)) {
      throw new TimeoutException("Task await timed out.");
    }

    if (task.isSuccessful()) {
      return task.getResult();
    } else {
      throw new ExecutionException(task.getException());
    }
  }

  /** An Executor that uses the calling thread. */
  private static final Executor DIRECT_EXECUTOR = Runnable::run;

  private static class AwaitListener<TResult>
      implements OnSuccessListener<TResult>, OnFailureListener, OnCanceledListener {
    private final CountDownLatch latch = new CountDownLatch(1);

    @Override
    public void onSuccess(TResult o) {
      latch.countDown();
    }

    @Override
    public void onFailure(@NonNull Exception e) {
      latch.countDown();
    }

    @Override
    public void onCanceled() {
      latch.countDown();
    }

    public void await() throws InterruptedException {
      latch.await();
    }

    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
      return latch.await(timeout, unit);
    }
  }
}
