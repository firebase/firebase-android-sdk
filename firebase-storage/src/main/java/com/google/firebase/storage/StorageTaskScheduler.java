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

package com.google.firebase.storage;

import android.annotation.SuppressLint;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A class used to schedule long running operations (upload/download) and operations that are
 * intended to be short lived (list/get/delete)
 *
 * @hide
 */
@SuppressWarnings("JavaDoc")
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class StorageTaskScheduler {
  public static StorageTaskScheduler sInstance = new StorageTaskScheduler();

  private static BlockingQueue<Runnable> mCommandQueue = new LinkedBlockingQueue<>();

  public static int maxDownloadExecutors = 3;
  public static int maxUploadExecutors = 2;

  // TODO(b/258426744): Migrate to go/firebase-android-executors
  @SuppressLint("ThreadPoolCreation")
  private static final ThreadPoolExecutor COMMAND_POOL_EXECUTOR =
      new ThreadPoolExecutor(
          5, 5, 5, TimeUnit.SECONDS, mCommandQueue, new StorageThreadFactory("Command-"));

  private static BlockingQueue<Runnable> mUploadQueue = new LinkedBlockingQueue<>();

  // TODO(b/258426744): Migrate to go/firebase-android-executors
  @SuppressLint("ThreadPoolCreation")
  private static final ThreadPoolExecutor UPLOAD_QUEUE_EXECUTOR =
      new ThreadPoolExecutor(
              2, 2, 5, TimeUnit.SECONDS, mUploadQueue, new StorageThreadFactory("Upload-"));

  private static BlockingQueue<Runnable> mDownloadQueue = new LinkedBlockingQueue<>();

  // TODO(b/258426744): Migrate to go/firebase-android-executors
  @SuppressLint("ThreadPoolCreation")
  private static final ThreadPoolExecutor DOWNLOAD_QUEUE_EXECUTOR =
      new ThreadPoolExecutor(
              3, 3, 5, TimeUnit.SECONDS, mDownloadQueue, new StorageThreadFactory("Download-"));

  private static BlockingQueue<Runnable> mCallbackQueue = new LinkedBlockingQueue<>();

  // TODO(b/258426744): Migrate to go/firebase-android-executors
  @SuppressLint("ThreadPoolCreation")
  private static final ThreadPoolExecutor CALLBACK_QUEUE_EXECUTOR =
      new ThreadPoolExecutor(
          1, 1, 5, TimeUnit.SECONDS, mCallbackQueue, new StorageThreadFactory("Callbacks-"));

  static {
    COMMAND_POOL_EXECUTOR.allowCoreThreadTimeOut(true);
    UPLOAD_QUEUE_EXECUTOR.allowCoreThreadTimeOut(true);
    DOWNLOAD_QUEUE_EXECUTOR.allowCoreThreadTimeOut(true);
    CALLBACK_QUEUE_EXECUTOR.allowCoreThreadTimeOut(true);
  }

  public static void setCallbackQueueKeepAlive(long keepAliveTime, TimeUnit timeUnit) {
    CALLBACK_QUEUE_EXECUTOR.setKeepAliveTime(keepAliveTime, timeUnit);
  }

  public static StorageTaskScheduler getInstance() {
    return sInstance;
  }

  public void scheduleCommand(Runnable task) {
    COMMAND_POOL_EXECUTOR.execute(task);
  }

  public void scheduleUpload(Runnable task) {
    int corePoolSize = UPLOAD_QUEUE_EXECUTOR.getCorePoolSize();
    int maxPoolSize = UPLOAD_QUEUE_EXECUTOR.getMaximumPoolSize();
    if (corePoolSize != maxUploadExecutors && maxPoolSize != maxUploadExecutors) {
      UPLOAD_QUEUE_EXECUTOR.setCorePoolSize(maxUploadExecutors);
      UPLOAD_QUEUE_EXECUTOR.setMaximumPoolSize(maxUploadExecutors);
    }
    UPLOAD_QUEUE_EXECUTOR.execute(task);
  }

  public void scheduleDownload(Runnable task) {
    int corePoolSize = DOWNLOAD_QUEUE_EXECUTOR.getCorePoolSize();
    int maxPoolSize = DOWNLOAD_QUEUE_EXECUTOR.getMaximumPoolSize();
    if (corePoolSize != maxDownloadExecutors && maxPoolSize != maxDownloadExecutors) {
      DOWNLOAD_QUEUE_EXECUTOR.setCorePoolSize(maxDownloadExecutors);
      DOWNLOAD_QUEUE_EXECUTOR.setMaximumPoolSize(maxDownloadExecutors);
    }
    DOWNLOAD_QUEUE_EXECUTOR.execute(task);
  }

  public void scheduleCallback(Runnable task) {
    CALLBACK_QUEUE_EXECUTOR.execute(task);
  }

  public Executor getCommandPoolExecutor() {
    return COMMAND_POOL_EXECUTOR;
  }

  /** The thread factory for Storage threads. */
  static class StorageThreadFactory implements ThreadFactory {
    private final AtomicInteger threadNumber = new AtomicInteger(1);
    private final String mNameSuffix;

    StorageThreadFactory(@NonNull String suffix) {
      mNameSuffix = suffix;
    }

    @Override
    @SuppressWarnings("ThreadPriorityCheck")
    // TODO(b/258426744): Migrate to go/firebase-android-executors
    @SuppressLint("ThreadPoolCreation")
    public Thread newThread(@NonNull Runnable r) {
      Thread t = new Thread(r, "FirebaseStorage-" + mNameSuffix + threadNumber.getAndIncrement());
      t.setDaemon(false);
      t.setPriority(
          android.os.Process.THREAD_PRIORITY_BACKGROUND
              + android.os.Process.THREAD_PRIORITY_MORE_FAVORABLE);
      return t;
    }
  }
}
