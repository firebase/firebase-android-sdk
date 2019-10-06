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

import android.app.Activity;
import androidx.annotation.NonNull;
import java.util.concurrent.Executor;

/**
 * Represents an asynchronous operation that can be paused, resumed and canceled. This task also
 * receives progress and other state change notifications.
 *
 * @param <StateT> the type of state this operation returns in events.
 */
@SuppressWarnings("unused")
public abstract class ControllableTask<StateT> extends CancellableTask<StateT> {

  /**
   * Attempts to pause the task. A paused task can later be resumed.
   *
   * @return true if this task was successfully paused or is in the process of being paused. Returns
   *     false if the task is already completed or in a state that cannot be paused.
   */
  public abstract boolean pause();

  /**
   * Attempts to resume this task.
   *
   * @return true if the task is successfully resumed or is in the process of being resumed. Returns
   *     false if the task is already completed or in a state that cannot be resumed.
   */
  public abstract boolean resume();

  /** @return true if the task has been paused. */
  public abstract boolean isPaused();

  /**
   * Adds a listener that is called when the Task becomes paused.
   *
   * @return this Task
   */
  @NonNull
  public abstract ControllableTask<StateT> addOnPausedListener(
      @NonNull OnPausedListener<? super StateT> listener);

  /**
   * Adds a listener that is called when the Task becomes paused.
   *
   * @param executor the executor to use to call the listener
   * @return this Task
   */
  @NonNull
  public abstract ControllableTask<StateT> addOnPausedListener(
      @NonNull Executor executor, @NonNull OnPausedListener<? super StateT> listener);

  /**
   * Adds a listener that is called when the Task becomes paused.
   *
   * @param activity When the supplied {@link Activity} stops, this listener will automatically be
   *     removed.
   * @return this Task
   */
  @NonNull
  public abstract ControllableTask<StateT> addOnPausedListener(
      @NonNull Activity activity, @NonNull OnPausedListener<? super StateT> listener);
}
