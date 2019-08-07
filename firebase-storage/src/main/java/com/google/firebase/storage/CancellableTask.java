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
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;
import java.util.concurrent.Executor;

/**
 * Represents an asynchronous operation that can be canceled.
 *
 * @param <StateT> the type of state this operation returns in events.
 */
@SuppressWarnings("unused")
public abstract class CancellableTask<StateT> extends Task<StateT> {
  /**
   * Attempts to cancel the task. A canceled task cannot be resumed later. A canceled task calls
   * back on listeners subscribed to {@link Task#addOnFailureListener(OnFailureListener)} with an
   * exception that indicates the task was canceled.
   *
   * @return true if this task was successfully canceled or is in the process of being canceled.
   *     Returns false if the task is already completed or in a state that cannot be canceled.
   */
  public abstract boolean cancel();

  /** @return true if the task has been canceled. */
  @Override
  public abstract boolean isCanceled();

  /** @return true if the task is currently running. */
  public abstract boolean isInProgress();

  /**
   * Adds a listener that is called periodically while the ControllableTask executes.
   *
   * @return this Task
   */
  @NonNull
  public abstract CancellableTask<StateT> addOnProgressListener(
      @NonNull OnProgressListener<? super StateT> listener);

  /**
   * Adds a listener that is called periodically while the ControllableTask executes.
   *
   * @param executor the executor to use to call the listener
   * @return this Task
   */
  @NonNull
  public abstract CancellableTask<StateT> addOnProgressListener(
      @NonNull Executor executor, @NonNull OnProgressListener<? super StateT> listener);

  /**
   * Adds a listener that is called periodically while the ControllableTask executes.
   *
   * @param activity When the supplied {@link Activity} stops, this listener will automatically be
   *     removed.
   * @return this Task
   */
  @NonNull
  public abstract CancellableTask<StateT> addOnProgressListener(
      @NonNull Activity activity, @NonNull OnProgressListener<? super StateT> listener);
}
