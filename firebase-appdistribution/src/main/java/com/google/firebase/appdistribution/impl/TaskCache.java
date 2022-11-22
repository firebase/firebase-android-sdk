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

/**
 * A cache for Tasks, for use in cases where we only ever want one active task at a time for a
 * particular operation.
 */
class TaskCache<T extends Task> {

  /** A functional interface for a producer of a new Task. */
  interface TaskProducer<T extends Task> {

    /** Produce a new Task. */
    T produce();
  }

  private T cachedTask;

  /**
   * Gets a cached task, if there is one and it is not completed, or else calls the given {@code
   * producer} and caches the returned task.
   *
   * @return the cached task if there is one and it is not completed, or else the result from {@code
   *     producer.produce()}
   */
  synchronized T getOrCreateTask(TaskProducer<T> producer) {
    if (cachedTask == null || cachedTask.isComplete()) {
      cachedTask = producer.produce();
    }
    return cachedTask;
  }
}
