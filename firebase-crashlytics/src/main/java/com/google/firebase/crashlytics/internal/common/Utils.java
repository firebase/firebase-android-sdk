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

package com.google.firebase.crashlytics.internal.common;

import android.os.Looper;
import androidx.annotation.NonNull;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Utils */
public final class Utils {

  private static final FilenameFilter ALL_FILES_FILTER =
      new FilenameFilter() {
        @Override
        public boolean accept(File dir, String filename) {
          return true;
        }
      };

  private Utils() {}

  /**
   * Caps the number of fatal and native sessions at maxAllowed, deleting session files with older
   * timestamps if necessary. Synchronization is up to the caller.
   *
   * @return the number of files ultimately retained.
   */
  static int capSessionCount(
      File nativeDirectory, File fatalDirectory, int maxAllowed, Comparator<File> sortComparator) {
    final List<File> allFiles = new ArrayList<>();
    File[] nativeFiles = nativeDirectory.listFiles();
    File[] fatalFiles = fatalDirectory.listFiles(ALL_FILES_FILTER);
    nativeFiles = (nativeFiles != null) ? nativeFiles : new File[0];
    fatalFiles = (fatalFiles != null) ? fatalFiles : new File[0];
    allFiles.addAll(Arrays.asList(nativeFiles));
    allFiles.addAll(Arrays.asList(fatalFiles));
    return capFileCount(allFiles, maxAllowed, sortComparator);
  }

  static int capFileCount(File directory, int maxAllowed, Comparator<File> sortComparator) {
    return capFileCount(directory, ALL_FILES_FILTER, maxAllowed, sortComparator);
  }

  /**
   * Caps the number of files matching the given filter at maxAllowed, deleting files with older
   * timestamps if necessary. Synchronization is up to the caller.
   *
   * @return the number of files ultimately retained.
   */
  static int capFileCount(
      File directory, FilenameFilter filter, int maxAllowed, Comparator<File> sortComparator) {
    final File[] sessionFiles = directory.listFiles(filter);

    if (sessionFiles == null) {
      return 0;
    }

    return capFileCount(Arrays.asList(sessionFiles), maxAllowed, sortComparator);
  }

  static int capFileCount(List<File> files, int maxAllowed, Comparator<File> sortComparator) {
    int numRetained = files.size();
    // sort so that we iterate over the oldest first
    Collections.sort(files, sortComparator);

    for (File file : files) {
      // delete until we come under the max
      if (numRetained <= maxAllowed) {
        return numRetained;
      }
      recursiveDelete(file);
      numRetained--;
    }

    return numRetained;
  }

  /** @return A tasks that is resolved when either of the given tasks is resolved. */
  public static <T> Task<T> race(Task<T> t1, Task<T> t2) {
    final TaskCompletionSource<T> result = new TaskCompletionSource<>();
    Continuation<T, Void> continuation =
        new Continuation<T, Void>() {
          @Override
          public Void then(@NonNull Task<T> task) throws Exception {
            if (task.isSuccessful()) {
              result.trySetResult(task.getResult());
            } else {
              result.trySetException(task.getException());
            }
            return null;
          }
        };
    t1.continueWith(continuation);
    t2.continueWith(continuation);
    return result.getTask();
  }

  /** Similar to Tasks.call, but takes a Callable that returns a Task. */
  public static <T> Task<T> callTask(Executor executor, Callable<Task<T>> callable) {
    final TaskCompletionSource<T> tcs = new TaskCompletionSource<T>();
    executor.execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              callable
                  .call()
                  .continueWith(
                      new Continuation<T, Void>() {
                        @Override
                        public Void then(@NonNull Task<T> task) throws Exception {
                          if (task.isSuccessful()) {
                            tcs.setResult(task.getResult());
                          } else {
                            tcs.setException(task.getException());
                          }
                          return null;
                        }
                      });
            } catch (Exception e) {
              tcs.setException(e);
            }
          }
        });
    return tcs.getTask();
  }

  /**
   * Blocks until the given Task completes, and then returns the value the Task was resolved with,
   * if successful. If the Task fails, an exception will be thrown, wrapping the Exception of the
   * Task. Blocking on Tasks is generally a bad idea, and you definitely should not block the main
   * thread waiting on one. But there are a couple of weird spots in our SDK where we really have no
   * choice. You should not use this method for any new code. And if you really do have to use it,
   * you should feel slightly bad about it.
   *
   * @param task the task to block on
   * @return the value that was returned by the task, if successful.
   * @throws InterruptedException if the method was interrupted
   * @throws TimeoutException if the method timed out while waiting for the task.
   */
  public static <T> T awaitEvenIfOnMainThread(Task<T> task)
      throws InterruptedException, TimeoutException {
    CountDownLatch latch = new CountDownLatch(1);

    task.continueWith(
        TASK_CONTINUATION_EXECUTOR_SERVICE,
        unusedTask -> {
          latch.countDown();
          return null;
        });

    if (Looper.getMainLooper() == Looper.myLooper()) {
      latch.await(CrashlyticsCore.DEFAULT_MAIN_HANDLER_TIMEOUT_SEC, TimeUnit.SECONDS);
    } else {
      latch.await();
    }

    if (task.isSuccessful()) {
      return task.getResult();
    } else if (task.isCanceled()) {
      throw new CancellationException("Task is already canceled");
    } else if (task.isComplete()) {
      throw new IllegalStateException(task.getException());
    } else {
      throw new TimeoutException();
    }
  }

  /**
   * ExecutorService that is used exclusively by the awaitEvenIfOnMainThread function. If the
   * Continuation which counts down the latch is called on the same thread which is waiting on the
   * latch, a deadlock will occur. A dedicated ExecutorService ensures that cannot happen.
   */
  private static final ExecutorService TASK_CONTINUATION_EXECUTOR_SERVICE =
      ExecutorUtils.buildSingleThreadExecutorService(
          "awaitEvenIfOnMainThread task continuation executor");

  private static void recursiveDelete(File f) {
    if (f.isDirectory()) {
      for (File s : f.listFiles()) {
        recursiveDelete(s);
      }
    }
    f.delete();
  }
}
