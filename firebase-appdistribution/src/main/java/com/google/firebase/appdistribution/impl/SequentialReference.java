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

import androidx.annotation.VisibleForTesting;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.appdistribution.UpdateTask;
import com.google.firebase.concurrent.FirebaseExecutors;
import java.util.concurrent.Executor;

/**
 * A reference to an object that uses a sequential executor to ensure non-concurrent reads and
 * writes while preventing lock contention.
 */
class SequentialReference<T> {

  interface SequentialReferenceConsumer<T> {
    void consume(T value);
  }

  interface SequentialReferenceTransformer<T, U> {
    U transform(T value);
  }

  interface SequentialReferenceUpdateTaskTransformer<T> {
    UpdateTask transform(T value);
  }

  private final Executor sequentialExecutor;

  private T value;

  SequentialReference(Executor baseExecutor) {
    this(baseExecutor, null);
  }

  SequentialReference(Executor baseExecutor, T initialValue) {
    sequentialExecutor = FirebaseExecutors.newSequentialExecutor(baseExecutor);
    value = initialValue;
  }

  /** Enqueues a value to be set. */
  void set(T newValue) {
    sequentialExecutor.execute(() -> value = newValue);
  }

  /**
   * Enqueues a new value to be set, and returns a {@link Task} that resolves with the result of
   * applying the given {@code transformer} to the value.
   */
  <U> Task<U> setAndTransform(T newValue, SequentialReferenceTransformer<T, U> transformer) {
    TaskCompletionSource<U> taskCompletionSource = new TaskCompletionSource<>();
    sequentialExecutor.execute(
        () -> {
          value = newValue;
          taskCompletionSource.setResult(transformer.transform(value));
        });
    return taskCompletionSource.getTask();
  }

  /** Gets the value, passing it to the given {@code consumer}. */
  void get(SequentialReferenceConsumer<T> consumer) {
    sequentialExecutor.execute(() -> consumer.consume(value));
  }

  /**
   * Gets the value, returning an {@link UpdateTask} produced by applying the {@code transformer}
   * function to the value.
   */
  UpdateTask getAndTransform(SequentialReferenceUpdateTaskTransformer<T> transformer) {
    UpdateTaskImpl updateTask = new UpdateTaskImpl();
    sequentialExecutor.execute(
        () -> updateTask.shadow(sequentialExecutor, transformer.transform(value)));
    return updateTask;
  }

  @VisibleForTesting
  T getSnapshot() {
    return value;
  }
}
