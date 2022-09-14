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
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Helper listener that works around a limitation of the Tasks API where await() cannot be called on
 * the main thread.
 */
public class TaskWaiter<TResult> implements OnCompleteListener<TResult> {
  private static final long TIMEOUT_MS = 500000;
  private final CountDownLatch latch = new CountDownLatch(1);
  private final Task<TResult> task;

  private TaskWaiter(Task<TResult> task) {
    this.task = task;
    task.addOnCompleteListener(Runnable::run, this);
  }

  @Override
  public void onComplete(@NonNull Task<TResult> task) {
    latch.countDown();
  }

  public TResult await() throws InterruptedException, TimeoutException {
    if (!task.isComplete() && !latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
      throw new TimeoutException("timed out waiting for result");
    }
    return task.getResult();
  }

  @CanIgnoreReturnValue
  public static <TResult> TResult await(Task<TResult> task)
      throws InterruptedException, TimeoutException {
    return new TaskWaiter<>(task).await();
  }
}
