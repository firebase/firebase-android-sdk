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

package com.google.firebase.heartbeatinfo;

import androidx.annotation.NonNull;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Helper listener that works around a limitation of the Tasks API where await() cannot be called on
 * the main thread. This listener works around it by running itself on a different thread, thus
 * allowing the main thread to be woken up when the Tasks complete.
 */
public class TestOnCompleteListener<TResult> implements OnCompleteListener<TResult> {
  private static final long TIMEOUT_MS = 5000;
  private final CountDownLatch latch = new CountDownLatch(1);
  private Task<TResult> task;
  private volatile TResult result;
  private volatile Exception exception;
  private volatile boolean successful;

  @Override
  public void onComplete(@NonNull Task<TResult> task) {
    this.task = task;
    successful = task.isSuccessful();
    if (successful) {
      result = task.getResult();
    } else {
      exception = task.getException();
    }
    latch.countDown();
  }

  /** Blocks until the {@link #onComplete} is called. */
  public TResult await() throws InterruptedException, ExecutionException {
    if (!latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
      throw new InterruptedException("timed out waiting for result");
    }
    if (successful) {
      return result;
    } else {
      if (exception instanceof InterruptedException) {
        throw (InterruptedException) exception;
      }
      if (exception instanceof IOException) {
        throw new ExecutionException(exception);
      }
      throw new IllegalStateException("got an unexpected exception type", exception);
    }
  }
}
