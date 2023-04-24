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

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import com.google.firebase.concurrent.FirebaseExecutors;
import java.util.concurrent.Executor;

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

  private static final int COMMAND_POOL_SIZE = 5;
  private static final int DOWNLOAD_POOL_SIZE = 3;
  private static final int UPLOAD_POOL_SIZE = 2;

  private static Executor COMMAND_POOL_EXECUTOR;
  private static Executor UPLOAD_QUEUE_EXECUTOR;
  private static Executor DOWNLOAD_QUEUE_EXECUTOR;
  private static Executor CALLBACK_QUEUE_EXECUTOR;
  private static Executor MAIN_THREAD_EXECUTOR;

  public static void initializeExecutors(
      @NonNull Executor firebaseExecutor, @NonNull Executor uiExecutor) {
    COMMAND_POOL_EXECUTOR =
        FirebaseExecutors.newLimitedConcurrencyExecutor(firebaseExecutor, COMMAND_POOL_SIZE);
    DOWNLOAD_QUEUE_EXECUTOR =
        FirebaseExecutors.newLimitedConcurrencyExecutor(firebaseExecutor, DOWNLOAD_POOL_SIZE);
    UPLOAD_QUEUE_EXECUTOR =
        FirebaseExecutors.newLimitedConcurrencyExecutor(firebaseExecutor, UPLOAD_POOL_SIZE);
    CALLBACK_QUEUE_EXECUTOR = FirebaseExecutors.newSequentialExecutor(firebaseExecutor);
    MAIN_THREAD_EXECUTOR = uiExecutor;
  }

  public static StorageTaskScheduler getInstance() {
    return sInstance;
  }

  public void scheduleCommand(Runnable task) {
    COMMAND_POOL_EXECUTOR.execute(task);
  }

  public void scheduleUpload(Runnable task) {
    UPLOAD_QUEUE_EXECUTOR.execute(task);
  }

  public Executor getMainThreadExecutor() {
    return MAIN_THREAD_EXECUTOR;
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
}
