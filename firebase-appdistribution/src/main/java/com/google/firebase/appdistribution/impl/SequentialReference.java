// Copyright 2022 Google LLC
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

package com.google.firebase.appdistribution.impl;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.concurrent.FirebaseExecutors;
import java.util.concurrent.Executor;

/**
 * A reference to an object that uses a sequential executor to ensure non-concurrent reads and
 * writes while preventing lock contention.
 */
class SequentialReference<T> {

  private final Executor sequentialExecutor;
  private T value;

  /**
   * Constructor for a {@link SequentialReference} that controls access using its own sequential
   * executor backed by the given base executor.
   */
  SequentialReference(Executor baseExecutor) {
    this.sequentialExecutor = FirebaseExecutors.newSequentialExecutor(baseExecutor);
  }

  /**
   * Sets the value, returning a {@link Task} will complete once it is set.
   *
   * <p>For convenience when chaining tasks together, the result of the returned task is the value.
   */
  Task<T> set(T newValue) {
    TaskCompletionSource<T> taskCompletionSource = new TaskCompletionSource<>();
    sequentialExecutor.execute(
        () -> {
          value = newValue;
          taskCompletionSource.setResult(value);
        });
    return taskCompletionSource.getTask();
  }

  /**
   * Gets a {@link Task} that will complete with the value.
   *
   * <p>In some cases the value may have changed by the time any callbacks attached to the returned
   * {@link Task} are called.
   */
  Task<T> get() {
    TaskCompletionSource<T> taskCompletionSource = new TaskCompletionSource<>();
    sequentialExecutor.execute(() -> taskCompletionSource.setResult(value));
    return taskCompletionSource.getTask();
  }
}
