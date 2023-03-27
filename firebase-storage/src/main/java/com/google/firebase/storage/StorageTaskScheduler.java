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

import com.google.firebase.concurrent.FirebaseExecutors;

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

  public static void initializeExecutors(Executor firebaseExecutor) {
    // TODO(mtewani): Check if concurrency can be modified externally
    COMMAND_POOL_EXECUTOR = FirebaseExecutors.newLimitedConcurrencyExecutor(firebaseExecutor, 5);
    DOWNLOAD_QUEUE_EXECUTOR = FirebaseExecutors.newLimitedConcurrencyExecutor(firebaseExecutor, 5);
    UPLOAD_QUEUE_EXECUTOR = FirebaseExecutors.newLimitedConcurrencyExecutor(firebaseExecutor, 5);
    CALLBACK_QUEUE_EXECUTOR = FirebaseExecutors.newLimitedConcurrencyExecutor(firebaseExecutor, 5);
  }

  // TODO(b/258426744): Migrate to go/firebase-android-executors
  @SuppressLint("ThreadPoolCreation")
  private static Executor COMMAND_POOL_EXECUTOR;
  private static Executor UPLOAD_QUEUE_EXECUTOR;
  private static Executor DOWNLOAD_QUEUE_EXECUTOR;
  private static Executor CALLBACK_QUEUE_EXECUTOR;

//
//  public static void setCallbackQueueKeepAlive(long keepAliveTime, TimeUnit timeUnit) {
//    CALLBACK_QUEUE_EXECUTOR.setKeepAliveTime(keepAliveTime, timeUnit);
//  }

  public static StorageTaskScheduler getInstance() {
    return sInstance;
  }

  public void scheduleCommand(Runnable task) {
    COMMAND_POOL_EXECUTOR.execute(task);
  }

  public void scheduleUpload(Runnable task) {
    UPLOAD_QUEUE_EXECUTOR.execute(task);
  }

  public void scheduleDownload(Runnable task) {
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
