// Copyright 2024 Google LLC
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
package com.google.firebase.firestore.integration;

import androidx.annotation.NonNull;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Collects asynchronous `onResult` and `onException` callback invocations.
 *
 * <p> As part of a test, a callback can be asynchronously invoked many times. This class retains
 * all callback invocations as a List of Task. The test code can await a future callback.
 *
 * <p> Just like a stream, no more results are expected after an exception.
 */
public class AsyncTaskAccumulator<T> implements Iterable<Task<T>> {

  private int eventCount;
  private List<TaskCompletionSource<T>> events;

  public AsyncTaskAccumulator() {
    eventCount = 0;
    events = new ArrayList<>();
  }

  /**
   * Callback for next `onResult` or `onException`. Calling this method repeatedly will
   * provide callbacks further into the future. Each callback should only be exactly once.
   */
  public synchronized TaskCompletionSource<T> next() {
    return computeIfAbsentIndex(eventCount++);
  }

  /**
   * Callback that can be invoked as part of test code.
   */
  public void onResult(T result) {
    next().setResult(result);
  }

  /**
   * Callback that can be invoked as part of test code.
   */
  public void onException(Exception e) {
    next().setException(e);
  }

  private TaskCompletionSource<T> computeIfAbsentIndex(int i) {
    while (events.size() <= i) {
      events.add(new TaskCompletionSource<>());
    }
    return events.get(i);
  }

  /**
   * Get task that completes when result arrives.
   *
   * @param index 0 indexed arrival sequence of results.
   * @return Task.
   */
  @NonNull
  public synchronized Task<T> get(int index) {
    return computeIfAbsentIndex(index).getTask();
  }

  /**
   * Iterates over results.
   * <p>
   * The Iterator is thread safe.
   * Iteration will stop upon task that is failed, cancelled or incomplete.
   * <p>
   * A loop that waits for task to complete before getting next task will continue to iterate
   * indefinitely. Attempting to iterate past a task that is not yet successful will throw
   * {#code NoSuchElementException} and {@code #hasNext()} will be false. In this way, iteration
   * in nonblocking. Last element will be failed, cancelled or awaiting result.
   *
   * @return Iterator of Tasks that complete.
   */
  @NonNull
  @Override
  public Iterator<Task<T>> iterator() {
    return new Iterator<Task<T>>() {

      private int i = -1;
      private Task<T> current;

      @Override
      public synchronized boolean hasNext() {
        // We always return first, and continue to return tasks so long as previous
        // is successful. A task that hasn't completed, will also mark the end of
        // iteration.
        return i < 0 || current.isSuccessful();
      }

      @Override
      public synchronized Task<T> next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        i++;
        return current = get(i);
      }
    };
  }
}
