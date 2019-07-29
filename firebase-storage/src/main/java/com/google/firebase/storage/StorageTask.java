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
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.common.internal.Preconditions;
import com.google.android.gms.tasks.CancellationToken;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCanceledListener;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.RuntimeExecutionException;
import com.google.android.gms.tasks.SuccessContinuation;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.Executor;

/** A controllable Task that has a synchronized state machine. */
@SuppressWarnings({"unused", "TypeParameterUnusedInFormals"})
public abstract class StorageTask<ResultT extends StorageTask.ProvideError>
    extends ControllableTask<ResultT> {
  private static final String TAG = "StorageTask";
  static final int INTERNAL_STATE_NOT_STARTED = 1;
  static final int INTERNAL_STATE_QUEUED = 2;
  static final int INTERNAL_STATE_IN_PROGRESS = 4;
  static final int INTERNAL_STATE_PAUSING = 8;
  static final int INTERNAL_STATE_PAUSED = 16;
  static final int INTERNAL_STATE_CANCELING = 32;
  static final int INTERNAL_STATE_FAILURE = 64;
  static final int INTERNAL_STATE_SUCCESS = 128;
  static final int INTERNAL_STATE_CANCELED = 256;
  static final int STATES_SUCCESS = INTERNAL_STATE_SUCCESS;
  static final int STATES_PAUSED = INTERNAL_STATE_PAUSED;
  static final int STATES_FAILURE = INTERNAL_STATE_FAILURE;
  static final int STATES_CANCELED = INTERNAL_STATE_CANCELED;
  static final int STATES_COMPLETE = STATES_SUCCESS | STATES_FAILURE | STATES_CANCELED;
  static final int STATES_INPROGRESS = ~(STATES_COMPLETE | STATES_PAUSED);
  private static final HashMap<Integer, HashSet<Integer>> ValidUserInitiatedStateChanges =
      new HashMap<>();
  private static final HashMap<Integer, HashSet<Integer>> ValidTaskInitiatedStateChanges =
      new HashMap<>();

  static {
    ValidUserInitiatedStateChanges.put(
        INTERNAL_STATE_NOT_STARTED,
        new HashSet<>(Arrays.asList(INTERNAL_STATE_PAUSED, INTERNAL_STATE_CANCELED)));

    ValidUserInitiatedStateChanges.put(
        INTERNAL_STATE_QUEUED,
        new HashSet<>(Arrays.asList(INTERNAL_STATE_PAUSING, INTERNAL_STATE_CANCELING)));

    ValidUserInitiatedStateChanges.put(
        INTERNAL_STATE_IN_PROGRESS,
        new HashSet<>(Arrays.asList(INTERNAL_STATE_PAUSING, INTERNAL_STATE_CANCELING)));

    ValidUserInitiatedStateChanges.put(
        INTERNAL_STATE_PAUSED,
        new HashSet<>(Arrays.asList(INTERNAL_STATE_QUEUED, INTERNAL_STATE_CANCELED)));

    ValidUserInitiatedStateChanges.put(
        INTERNAL_STATE_FAILURE,
        new HashSet<>(Arrays.asList(INTERNAL_STATE_QUEUED, INTERNAL_STATE_CANCELED)));

    ValidTaskInitiatedStateChanges.put(
        INTERNAL_STATE_NOT_STARTED,
        new HashSet<>(Arrays.asList(INTERNAL_STATE_QUEUED, INTERNAL_STATE_FAILURE)));

    ValidTaskInitiatedStateChanges.put(
        INTERNAL_STATE_QUEUED,
        new HashSet<>(
            Arrays.asList(
                INTERNAL_STATE_IN_PROGRESS, INTERNAL_STATE_FAILURE, INTERNAL_STATE_SUCCESS)));

    ValidTaskInitiatedStateChanges.put(
        INTERNAL_STATE_IN_PROGRESS,
        new HashSet<>(
            Arrays.asList(
                INTERNAL_STATE_IN_PROGRESS, INTERNAL_STATE_FAILURE, INTERNAL_STATE_SUCCESS)));

    ValidTaskInitiatedStateChanges.put(
        INTERNAL_STATE_PAUSING,
        new HashSet<>(
            Arrays.asList(INTERNAL_STATE_PAUSED, INTERNAL_STATE_FAILURE, INTERNAL_STATE_SUCCESS)));

    ValidTaskInitiatedStateChanges.put(
        INTERNAL_STATE_CANCELING,
        new HashSet<>(
            Arrays.asList(
                INTERNAL_STATE_CANCELED, INTERNAL_STATE_FAILURE, INTERNAL_STATE_SUCCESS)));
  }

  protected final Object syncObject = new Object();

  @VisibleForTesting
  final TaskListenerImpl<OnSuccessListener<? super ResultT>, ResultT> successManager =
      new TaskListenerImpl<>(
          this,
          STATES_SUCCESS,
          (listener, snappedState) -> {
            StorageTaskManager.getInstance().unRegister(StorageTask.this);
            listener.onSuccess(snappedState);
          });

  @VisibleForTesting
  final TaskListenerImpl<OnFailureListener, ResultT> failureManager =
      new TaskListenerImpl<>(
          this,
          STATES_FAILURE,
          (listener, snappedState) -> {
            StorageTaskManager.getInstance().unRegister(StorageTask.this);
            listener.onFailure(snappedState.getError());
          });

  @VisibleForTesting
  final TaskListenerImpl<OnCompleteListener<ResultT>, ResultT> completeListener =
      new TaskListenerImpl<>(
          this,
          STATES_COMPLETE,
          (listener, snappedState) -> {
            StorageTaskManager.getInstance().unRegister(StorageTask.this);
            listener.onComplete(StorageTask.this);
          });

  @VisibleForTesting
  final TaskListenerImpl<OnCanceledListener, ResultT> cancelManager =
      new TaskListenerImpl<>(
          this,
          STATES_CANCELED,
          (listener, snappedState) -> {
            StorageTaskManager.getInstance().unRegister(StorageTask.this);
            listener.onCanceled();
          });

  @VisibleForTesting
  final TaskListenerImpl<OnProgressListener<? super ResultT>, ResultT> progressManager =
      new TaskListenerImpl<>(this, STATES_INPROGRESS, OnProgressListener::onProgress);

  @VisibleForTesting
  final TaskListenerImpl<OnPausedListener<? super ResultT>, ResultT> pausedManager =
      new TaskListenerImpl<>(this, STATES_PAUSED, OnPausedListener::onPaused);

  private volatile int currentState;
  private ResultT finalResult;

  protected StorageTask() {
    currentState = INTERNAL_STATE_NOT_STARTED;
  }

  /**
   * Starts this task by scheduling it with the {@link StorageTaskScheduler}
   *
   * @return {@code true} if successful or {@code false} if the {@link #getInternalState()} is one
   *     which does not allow the task to be queued.
   */
  @VisibleForTesting
  boolean queue() {
    if (tryChangeState(INTERNAL_STATE_QUEUED, false)) {
      schedule();
      return true;
    }
    return false;
  }

  @VisibleForTesting
  void resetState() {}

  @VisibleForTesting
  abstract StorageReference getStorage();

  /** @hide */
  @SuppressWarnings("JavaDoc")
  @VisibleForTesting
  abstract void schedule();

  /**
   * Attempts to resume a paused task.
   *
   * @return {@code true} if the task is successfully resumed. {@code false} if the task has an
   *     unrecoverable error or has entered another state that precludes resume.
   */
  @Override
  public boolean resume() {
    if (tryChangeState(INTERNAL_STATE_QUEUED, true)) {
      resetState();
      schedule();
      return true;
    }
    return false;
  }

  /**
   * Attempts to pause the task. A paused task can later be resumed.
   *
   * @return {@code true} if this task is successfully being paused. Note that a task may not be
   *     immediately paused if it was executing another action and can still fail or complete.
   */
  @Override
  public boolean pause() {
    return tryChangeState(new int[] {INTERNAL_STATE_PAUSED, INTERNAL_STATE_PAUSING}, true);
  }

  /**
   * Attempts to cancel the task. A canceled task cannot be resumed later.
   *
   * @return {@code true} if this task is successfully being canceled.
   */
  @Override
  public boolean cancel() {
    return tryChangeState(new int[] {INTERNAL_STATE_CANCELED, INTERNAL_STATE_CANCELING}, true);
  }

  /** Returns {@code true} if the Task is complete; {@code false} otherwise. */
  @Override
  public boolean isComplete() {
    return (getInternalState() & STATES_COMPLETE) != 0;
  }

  /** Returns {@code true} if the Task has completed successfully; {@code false} otherwise. */
  @Override
  public boolean isSuccessful() {
    return (getInternalState() & STATES_SUCCESS) != 0;
  }

  /** Returns {@code true} if the task has been canceled. */
  @Override
  public boolean isCanceled() {
    return getInternalState() == INTERNAL_STATE_CANCELED;
  }

  /** Returns {@code true} if the task is currently running. */
  @Override
  public boolean isInProgress() {
    return (getInternalState() & STATES_INPROGRESS) != 0;
  }

  /** Returns {@code true} if the task has been paused. */
  @Override
  public boolean isPaused() {
    return (getInternalState() & STATES_PAUSED) != 0;
  }

  /**
   * Gets the result of the Task, if it has already completed.
   *
   * @throws IllegalStateException if the Task is not yet complete
   * @throws RuntimeExecutionException if the Task failed with an exception
   */
  @NonNull
  @Override
  public ResultT getResult() {
    if (getFinalResult() == null) {
      throw new IllegalStateException();
    }
    Throwable t = getFinalResult().getError();
    if (t != null) {
      throw new RuntimeExecutionException(t);
    }
    return getFinalResult();
  }

  /**
   * Gets the result of the Task, if it has already completed.
   *
   * @throws IllegalStateException if the Task is not yet complete
   * @throws X if the Task failed with an exception of type X
   * @throws RuntimeExecutionException if the Task failed with an exception that was not of type X
   */
  @NonNull
  @Override
  public <X extends Throwable> ResultT getResult(@NonNull Class<X> exceptionType) throws X {
    if (getFinalResult() == null) {
      throw new IllegalStateException();
    }
    if (exceptionType.isInstance(getFinalResult().getError())) {
      throw exceptionType.cast(getFinalResult().getError());
    }
    Throwable t = getFinalResult().getError();
    if (t != null) {
      throw new RuntimeExecutionException(t);
    }
    return getFinalResult();
  }

  /**
   * Returns the exception that caused the Task to fail. Returns {@code null} if the Task is not yet
   * complete, or completed successfully.
   */
  @Nullable
  @Override
  public Exception getException() {
    if (getFinalResult() == null) {
      return null;
    }
    return getFinalResult().getError();
  }

  /**
   * Returns the current state of the task. This method will return state at any point of the tasks
   * execution and may not be the final result..
   */
  @NonNull
  public ResultT getSnapshot() {
    return snapState();
  }

  /** Returns the current internal state of this operation. */
  @VisibleForTesting
  int getInternalState() {
    return currentState;
  }

  @VisibleForTesting
  Object getSyncObject() {
    return syncObject;
  }

  @NonNull
  @VisibleForTesting
  ResultT snapState() {
    synchronized (syncObject) {
      return snapStateImpl();
    }
  }

  @NonNull
  @VisibleForTesting
  abstract ResultT snapStateImpl();

  /**
   * Tries to change the current state into one of the requested states. State transitions are
   * attempted in order (index 0 is first).
   *
   * @return Whether at least one state transition was successful.
   */
  @VisibleForTesting
  boolean tryChangeState(int[] requestedStates, boolean userInitiated) {
    HashMap<Integer, HashSet<Integer>> table =
        userInitiated ? ValidUserInitiatedStateChanges : ValidTaskInitiatedStateChanges;

    synchronized (syncObject) {
      for (int newState : requestedStates) {
        HashSet<Integer> validStates = table.get(getInternalState());
        if (validStates != null && validStates.contains(newState)) {
          currentState = newState;
          switch (currentState) {
            case INTERNAL_STATE_QUEUED:
              StorageTaskManager.getInstance().ensureRegistered(this);
              onQueued();
              break;
            case INTERNAL_STATE_IN_PROGRESS:
              onProgress();
              break;
            case INTERNAL_STATE_PAUSED:
              onPaused();
              break;
            case INTERNAL_STATE_FAILURE:
              onFailure();
              break;
            case INTERNAL_STATE_SUCCESS:
              onSuccess();
              break;
            case INTERNAL_STATE_CANCELED:
              onCanceled();
              break;
            default: // fall out
          }
          successManager.onInternalStateChanged();
          failureManager.onInternalStateChanged();
          cancelManager.onInternalStateChanged();
          completeListener.onInternalStateChanged();
          pausedManager.onInternalStateChanged();
          progressManager.onInternalStateChanged();

          if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(
                TAG,
                "changed internal state to: "
                    + getStateString(newState)
                    + " isUser: "
                    + userInitiated
                    + " from state:"
                    + getStateString(currentState));
          }

          return true;
        }
      }

      Log.w(
          TAG,
          "unable to change internal state to: "
              + getStateString(requestedStates)
              + " isUser: "
              + userInitiated
              + " from state:"
              + getStateString(currentState));

      return false;
    }
  }

  @VisibleForTesting
  boolean tryChangeState(int newState, boolean userInitiated) {
    return tryChangeState(new int[] {newState}, userInitiated);
  }

  // These callbacks get executed in a synchronized block

  protected void onQueued() {}

  protected void onProgress() {}

  protected void onPaused() {}

  protected void onFailure() {}

  protected void onSuccess() {}

  protected void onCanceled() {}

  private ResultT getFinalResult() {
    if (finalResult != null) {
      return finalResult;
    }

    if (!isComplete()) {
      return null;
    }

    if (finalResult == null) {
      finalResult = snapState();
    }
    return finalResult;
  }

  /**
   * Adds a listener that is called when the Task becomes paused.
   *
   * @return this Task
   */
  @NonNull
  @Override
  public StorageTask<ResultT> addOnPausedListener(
      @NonNull OnPausedListener<? super ResultT> listener) {
    Preconditions.checkNotNull(listener);
    pausedManager.addListener(null, null, listener);
    return this;
  }

  /**
   * Adds a listener that is called when the Task becomes paused.
   *
   * @param executor the executor to use to call the listener
   * @return this Task
   */
  @NonNull
  @Override
  public StorageTask<ResultT> addOnPausedListener(
      @NonNull Executor executor, @NonNull OnPausedListener<? super ResultT> listener) {
    Preconditions.checkNotNull(listener);
    Preconditions.checkNotNull(executor);
    pausedManager.addListener(null, executor, listener);
    return this;
  }

  // region listener handling

  /**
   * Adds a listener that is called when the Task becomes paused.
   *
   * @param activity When the supplied {@link Activity} stops, this listener will automatically be
   *     removed.
   * @return this Task
   */
  @NonNull
  @Override
  public StorageTask<ResultT> addOnPausedListener(
      @NonNull Activity activity, @NonNull OnPausedListener<? super ResultT> listener) {
    Preconditions.checkNotNull(listener);
    Preconditions.checkNotNull(activity);
    pausedManager.addListener(activity, null, listener);
    return this;
  }

  /** Removes a listener. */
  @NonNull
  public StorageTask<ResultT> removeOnPausedListener(
      @NonNull OnPausedListener<? super ResultT> listener) {
    Preconditions.checkNotNull(listener);
    pausedManager.removeListener(listener);
    return this;
  }

  /**
   * Adds a listener that is called periodically while the ControllableTask executes.
   *
   * @return this Task
   */
  @NonNull
  @Override
  public StorageTask<ResultT> addOnProgressListener(
      @NonNull OnProgressListener<? super ResultT> listener) {
    Preconditions.checkNotNull(listener);
    progressManager.addListener(null, null, listener);
    return this;
  }

  /**
   * Adds a listener that is called periodically while the ControllableTask executes.
   *
   * @param executor the executor to use to call the listener
   * @return this Task
   */
  @NonNull
  @Override
  public StorageTask<ResultT> addOnProgressListener(
      @NonNull Executor executor, @NonNull OnProgressListener<? super ResultT> listener) {
    Preconditions.checkNotNull(listener);
    Preconditions.checkNotNull(executor);
    progressManager.addListener(null, executor, listener);
    return this;
  }

  /**
   * Adds a listener that is called periodically while the ControllableTask executes.
   *
   * @param activity When the supplied {@link Activity} stops, this listener will automatically be
   *     removed.
   * @return this Task
   */
  @NonNull
  @Override
  public StorageTask<ResultT> addOnProgressListener(
      @NonNull Activity activity, @NonNull OnProgressListener<? super ResultT> listener) {
    Preconditions.checkNotNull(listener);
    Preconditions.checkNotNull(activity);
    progressManager.addListener(activity, null, listener);
    return this;
  }

  /** Removes a listener. */
  @NonNull
  public StorageTask<ResultT> removeOnProgressListener(
      @NonNull OnProgressListener<? super ResultT> listener) {
    Preconditions.checkNotNull(listener);
    progressManager.removeListener(listener);
    return this;
  }

  /**
   * Adds a listener that is called if the Task completes successfully. The listener will be called
   * on the main application thread. If the task has already completed successfully, a call to the
   * listener will be immediately scheduled. If multiple listeners are added, they will be called in
   * the order in which they were added.
   *
   * @return this Task
   */
  @Override
  @NonNull
  public StorageTask<ResultT> addOnSuccessListener(
      @NonNull OnSuccessListener<? super ResultT> listener) {
    Preconditions.checkNotNull(listener);
    successManager.addListener(null, null, listener);
    return this;
  }

  /**
   * Adds a listener that is called if the Task completes successfully.
   *
   * <p>
   *
   * <p>If multiple listeners are added, they will be called in the order in which they were added.
   * If the task has already completed successfully, a call to the listener will be immediately
   * scheduled.
   *
   * @param executor the executor to use to call the listener
   * @return this Task
   */
  @Override
  @NonNull
  public StorageTask<ResultT> addOnSuccessListener(
      @NonNull Executor executor, @NonNull OnSuccessListener<? super ResultT> listener) {
    Preconditions.checkNotNull(executor);
    Preconditions.checkNotNull(listener);
    successManager.addListener(null, executor, listener);
    return this;
  }

  /**
   * Adds a listener that is called if the Task completes successfully.
   *
   * <p>
   *
   * <p>If multiple listeners are added, they will be called in the order in which they were added.
   * If the task has already completed successfully, a call to the listener will be immediately
   * scheduled.
   *
   * @param activity When the supplied {@link Activity} stops, this listener will automatically be
   *     removed.
   * @return this Task
   */
  @Override
  @NonNull
  public StorageTask<ResultT> addOnSuccessListener(
      @NonNull Activity activity, @NonNull OnSuccessListener<? super ResultT> listener) {
    Preconditions.checkNotNull(activity);
    Preconditions.checkNotNull(listener);
    successManager.addListener(activity, null, listener);
    return this;
  }

  /** Removes a listener. */
  @NonNull
  public StorageTask<ResultT> removeOnSuccessListener(
      @NonNull OnSuccessListener<? super ResultT> listener) {
    Preconditions.checkNotNull(listener);
    successManager.removeListener(listener);
    return this;
  }

  /**
   * Adds a listener that is called if the Task fails.
   *
   * <p>
   *
   * <p>The listener will be called on main application thread. If the task has already failed, a
   * call to the listener will be immediately scheduled. If multiple listeners are added, they will
   * be called in the order in which they were added.
   *
   * @return this Task
   */
  @Override
  @NonNull
  public StorageTask<ResultT> addOnFailureListener(@NonNull OnFailureListener listener) {
    Preconditions.checkNotNull(listener);
    failureManager.addListener(null, null, listener);
    return this;
  }

  /**
   * Adds a listener that is called if the Task fails.
   *
   * <p>
   *
   * <p>If the task has already failed, a call to the listener will be immediately scheduled. If
   * multiple listeners are added, they will be called in the order in which they were added.
   *
   * @param executor the executor to use to call the listener
   * @return this Task
   */
  @Override
  @NonNull
  public StorageTask<ResultT> addOnFailureListener(
      @NonNull Executor executor, @NonNull OnFailureListener listener) {
    Preconditions.checkNotNull(listener);
    Preconditions.checkNotNull(executor);

    failureManager.addListener(null, executor, listener);
    return this;
  }

  /**
   * Adds a listener that is called if the Task fails.
   *
   * <p>
   *
   * <p>If the task has already failed, a call to the listener will be immediately scheduled. If
   * multiple listeners are added, they will be called in the order in which they were added.
   *
   * @param activity When the supplied {@link Activity} stops, this listener will automatically be
   *     removed.
   * @return this Task
   */
  @Override
  @NonNull
  public StorageTask<ResultT> addOnFailureListener(
      @NonNull Activity activity, @NonNull OnFailureListener listener) {
    Preconditions.checkNotNull(listener);
    Preconditions.checkNotNull(activity);

    failureManager.addListener(activity, null, listener);
    return this;
  }

  /** Removes a listener. */
  @NonNull
  public StorageTask<ResultT> removeOnFailureListener(@NonNull OnFailureListener listener) {
    Preconditions.checkNotNull(listener);
    failureManager.removeListener(listener);
    return this;
  }

  /**
   * Adds a listener that is called when the Task succeeds or fails.
   *
   * <p>
   *
   * <p>The listener will be called on main application thread. If the task has already failed, a
   * call to the listener will be immediately scheduled. If multiple listeners are added, they will
   * be called in the order in which they were added.
   *
   * @return this Task
   */
  @Override
  @NonNull
  public StorageTask<ResultT> addOnCompleteListener(@NonNull OnCompleteListener<ResultT> listener) {
    Preconditions.checkNotNull(listener);
    completeListener.addListener(null, null, listener);
    return this;
  }

  /**
   * Adds a listener that is called when the Task succeeds or fails.
   *
   * <p>
   *
   * <p>If the task has already failed, a call to the listener will be immediately scheduled. If
   * multiple listeners are added, they will be called in the order in which they were added.
   *
   * @param executor the executor to use to call the listener
   * @return this Task
   */
  @Override
  @NonNull
  public StorageTask<ResultT> addOnCompleteListener(
      @NonNull Executor executor, @NonNull OnCompleteListener<ResultT> listener) {
    Preconditions.checkNotNull(listener);
    Preconditions.checkNotNull(executor);

    completeListener.addListener(null, executor, listener);
    return this;
  }
  /**
   * Adds a listener that is called when the Task succeeds or fails.
   *
   * <p>
   *
   * <p>If the task has already failed, a call to the listener will be immediately scheduled. If
   * multiple listeners are added, they will be called in the order in which they were added.
   *
   * @param activity When the supplied {@link Activity} stops, this listener will automatically be
   *     removed.
   * @return this Task
   */
  @Override
  @NonNull
  public StorageTask<ResultT> addOnCompleteListener(
      @NonNull Activity activity, @NonNull OnCompleteListener<ResultT> listener) {
    Preconditions.checkNotNull(listener);
    Preconditions.checkNotNull(activity);

    completeListener.addListener(activity, null, listener);
    return this;
  }

  /** Removes a listener. */
  @NonNull
  public StorageTask<ResultT> removeOnCompleteListener(
      @NonNull OnCompleteListener<ResultT> listener) {
    Preconditions.checkNotNull(listener);
    completeListener.removeListener(listener);
    return this;
  }

  /**
   * Adds a listener that is called if the Task is canceled.
   *
   * <p>The listener will be called on main application thread. If the Task has already been
   * canceled, a call to the listener will be immediately scheduled. If multiple listeners are
   * added, they will be called in the order in which they were added.
   *
   * @return this Task
   */
  @NonNull
  @Override
  public StorageTask<ResultT> addOnCanceledListener(@NonNull OnCanceledListener listener) {
    Preconditions.checkNotNull(listener);
    cancelManager.addListener(null, null, listener);
    return this;
  }

  /**
   * Adds a listener that is called if the Task is canceled.
   *
   * <p>If the Task has already been canceled, a call to the listener will be immediately scheduled.
   * If multiple listeners are added, they will be called in the order in which they were added.
   *
   * @param executor the executor to use to call the listener
   * @return this Task
   */
  @NonNull
  @Override
  public StorageTask<ResultT> addOnCanceledListener(
      @NonNull Executor executor, @NonNull OnCanceledListener listener) {
    Preconditions.checkNotNull(listener);
    Preconditions.checkNotNull(executor);
    cancelManager.addListener(null, executor, listener);
    return this;
  }

  /**
   * Adds an Activity-scoped listener that is called if the Task is canceled.
   *
   * <p>The listener will be called on main application thread. If the Task has already been
   * canceled, a call to the listener will be immediately scheduled. If multiple listeners are
   * added, they will be called in the order in which they were added.
   *
   * <p>The listener will be automatically removed during {@link Activity#onStop}.
   *
   * @return this Task
   */
  @NonNull
  @Override
  public StorageTask<ResultT> addOnCanceledListener(
      @NonNull Activity activity, @NonNull OnCanceledListener listener) {
    Preconditions.checkNotNull(listener);
    Preconditions.checkNotNull(activity);
    cancelManager.addListener(activity, null, listener);
    return this;
  }

  /** Removes a listener. */
  @NonNull
  public StorageTask<ResultT> removeOnCanceledListener(@NonNull OnCanceledListener listener) {
    Preconditions.checkNotNull(listener);
    cancelManager.removeListener(listener);
    return this;
  }

  /**
   * Returns a new Task that will be completed with the result of applying the specified
   * Continuation to this Task.
   *
   * <p>The Continuation will be called on the main application thread.
   *
   * @see Continuation#then(Task)
   */
  @NonNull
  @Override
  public <ContinuationResultT> Task<ContinuationResultT> continueWith(
      @NonNull Continuation<ResultT, ContinuationResultT> continuation) {
    return continueWithImpl(null, continuation);
  }

  /**
   * Returns a new Task that will be completed with the result of applying the specified
   * Continuation to this Task.
   *
   * @param executor the executor to use to call the Continuation
   * @see Continuation#then(Task)
   */
  @NonNull
  @Override
  public <ContinuationResultT> Task<ContinuationResultT> continueWith(
      @NonNull final Executor executor,
      @NonNull final Continuation<ResultT, ContinuationResultT> continuation) {
    return continueWithImpl(executor, continuation);
  }

  @NonNull
  private <ContinuationResultT> Task<ContinuationResultT> continueWithImpl(
      @Nullable final Executor executor,
      @NonNull final Continuation<ResultT, ContinuationResultT> continuation) {

    final TaskCompletionSource<ContinuationResultT> source = new TaskCompletionSource<>();
    completeListener.addListener(
        null,
        executor,
        task -> {
          ContinuationResultT result;
          try {
            result = continuation.then(StorageTask.this);
          } catch (RuntimeExecutionException e) {
            if (e.getCause() instanceof Exception) {
              source.setException((Exception) e.getCause());
            } else {
              source.setException(e);
            }
            return;
          } catch (Exception e) {
            source.setException(e);
            return;
          }
          if (!source.getTask().isComplete()) {
            source.setResult(result);
          }
        });
    return source.getTask();
  }

  /**
   * Returns a new Task that will be completed with the result of applying the specified
   * Continuation to this Task.
   *
   * <p>The Continuation will be called on the main application thread.
   *
   * @see Continuation#then(Task)
   */
  @NonNull
  @Override
  public <ContinuationResultT> Task<ContinuationResultT> continueWithTask(
      @NonNull Continuation<ResultT, Task<ContinuationResultT>> continuation) {
    return continueWithTaskImpl(null, continuation);
  }

  /**
   * Returns a new Task that will be completed with the result of applying the specified
   * Continuation to this Task.
   *
   * @param executor the executor to use to call the Continuation
   * @see Continuation#then(Task)
   */
  @NonNull
  @Override
  public <ContinuationResultT> Task<ContinuationResultT> continueWithTask(
      @NonNull final Executor executor,
      @NonNull final Continuation<ResultT, Task<ContinuationResultT>> continuation) {
    return continueWithTaskImpl(executor, continuation);
  }

  /**
   * Returns a new Task that will be completed with the result of applying the specified
   * SuccessContinuation to this Task when this Task completes successfully. If the previous Task
   * fails, the onSuccessTask completion will be skipped and failure listeners will be invoked.
   *
   * <p>The SuccessContinuation will be called on the main application thread.
   *
   * <p>If the previous Task is canceled, the returned Task will also be canceled and the
   * SuccessContinuation would not execute.
   *
   * @see SuccessContinuation#then(ResultT)
   */
  @NonNull
  @Override
  public <ContinuationResultT> Task<ContinuationResultT> onSuccessTask(
      @NonNull SuccessContinuation<ResultT, ContinuationResultT> continuation) {
    return successTaskImpl(null, continuation);
  }

  /**
   * Returns a new Task that will be completed with the result of applying the specified
   * SuccessContinuation to this Task when this Task completes successfully. If the previous Task
   * fails, the onSuccessTask completion will be skipped and failure listeners will be invoked.
   *
   * <p>If the previous Task is canceled, the returned Task will also be canceled and the
   * SuccessContinuation would not execute.
   *
   * @param executor the executor to use to call the SuccessContinuation
   * @see SuccessContinuation#then(ResultT)
   */
  @NonNull
  @Override
  public <ContinuationResultT> Task<ContinuationResultT> onSuccessTask(
      @NonNull Executor executor,
      @NonNull SuccessContinuation<ResultT, ContinuationResultT> continuation) {
    return successTaskImpl(executor, continuation);
  }

  @NonNull
  private <ContinuationResultT> Task<ContinuationResultT> continueWithTaskImpl(
      @Nullable final Executor executor,
      @NonNull final Continuation<ResultT, Task<ContinuationResultT>> continuation) {
    final CancellationTokenSource cancellationTokenSource = new CancellationTokenSource();
    final CancellationToken cancellationToken = cancellationTokenSource.getToken();
    final TaskCompletionSource<ContinuationResultT> source =
        new TaskCompletionSource<>(cancellationToken);
    completeListener.addListener(
        null,
        executor,
        task -> {
          Task<ContinuationResultT> resultTask;
          try {
            resultTask = continuation.then(StorageTask.this);
          } catch (RuntimeExecutionException e) {
            if (e.getCause() instanceof Exception) {
              source.setException((Exception) e.getCause());
            } else {
              source.setException(e);
            }
            return;
          } catch (Exception e) {
            source.setException(e);
            return;
          }

          if (!source.getTask().isComplete()) {
            if (resultTask == null) {
              source.setException(new NullPointerException("Continuation returned null"));
              return;
            }

            resultTask.addOnSuccessListener(source::setResult);
            resultTask.addOnFailureListener(source::setException);
            resultTask.addOnCanceledListener(cancellationTokenSource::cancel);
          }
        });

    return source.getTask();
  }

  @NonNull
  private <ContinuationResultT> Task<ContinuationResultT> successTaskImpl(
      @Nullable final Executor executor,
      @NonNull final SuccessContinuation<ResultT, ContinuationResultT> continuation) {
    final CancellationTokenSource cancellationTokenSource = new CancellationTokenSource();
    final CancellationToken cancellationToken = cancellationTokenSource.getToken();
    final TaskCompletionSource<ContinuationResultT> source =
        new TaskCompletionSource<>(cancellationToken);

    successManager.addListener(
        null,
        executor,
        result -> {
          Task<ContinuationResultT> resultTask;
          try {
            resultTask = continuation.then(result);
          } catch (RuntimeExecutionException e) {
            if (e.getCause() instanceof Exception) {
              source.setException((Exception) e.getCause());
            } else {
              source.setException(e);
            }
            return;
          } catch (Exception e) {
            source.setException(e);
            return;
          }

          resultTask.addOnSuccessListener(source::setResult);
          resultTask.addOnFailureListener(source::setException);
          resultTask.addOnCanceledListener(cancellationTokenSource::cancel);
        });
    return source.getTask();
  }

  // endregion

  /** An object that returns an exception. */
  protected interface ProvideError {
    Exception getError();
  }

  @VisibleForTesting
  abstract void run();

  @VisibleForTesting
  Runnable getRunnable() {
    return () -> {
      try {
        StorageTask.this.run();
      } finally {
        ensureFinalState();
      }
    };
  }

  private void ensureFinalState() {
    // Ensure that we have entered into a final state.
    // Worst case, we enter a failure final state to indicate something bad happened
    // but we need to ensure the user was notified that we are no longer running.
    // There is also a chance the task was re-queued before run() finished.
    // this might be ok -- so we allow queued.  Tasks should immediately switch to
    // "in progress" as their first action to ensure this doesn't cause an issue.
    if (!isComplete() && !isPaused() && getInternalState() != INTERNAL_STATE_QUEUED) {
      // we first try to complete a cancel operation and if that fails, we just fail
      // the operation.
      if (!tryChangeState(INTERNAL_STATE_CANCELED, false)) {
        tryChangeState(INTERNAL_STATE_FAILURE, false);
      }
    }
  }

  private String getStateString(int[] states) {
    if (states.length == 0) {
      return "";
    }

    StringBuilder builder = new StringBuilder();

    for (int state : states) {
      builder.append(getStateString(state)).append(", ");
    }

    return builder.substring(0, builder.length() - 2);
  }

  private String getStateString(int state) {
    switch (state) {
      case INTERNAL_STATE_NOT_STARTED:
        return "INTERNAL_STATE_NOT_STARTED";
      case INTERNAL_STATE_QUEUED:
        return "INTERNAL_STATE_QUEUED";
      case INTERNAL_STATE_IN_PROGRESS:
        return "INTERNAL_STATE_IN_PROGRESS";
      case INTERNAL_STATE_PAUSING:
        return "INTERNAL_STATE_PAUSING";
      case INTERNAL_STATE_PAUSED:
        return "INTERNAL_STATE_PAUSED";
      case INTERNAL_STATE_CANCELING:
        return "INTERNAL_STATE_CANCELING";
      case INTERNAL_STATE_FAILURE:
        return "INTERNAL_STATE_FAILURE";
      case INTERNAL_STATE_SUCCESS:
        return "INTERNAL_STATE_SUCCESS";
      case INTERNAL_STATE_CANCELED:
        return "INTERNAL_STATE_CANCELED";
      default:
        return "Unknown Internal State!";
    }
  }

  /** Base class for state. */
  public class SnapshotBase implements StorageTask.ProvideError {
    private final Exception error;

    public SnapshotBase(@Nullable Exception error) {
      if (error == null) {
        if (isCanceled()) {
          // give the developer a canceled exception.
          this.error = StorageException.fromErrorStatus(Status.RESULT_CANCELED);
        } else if (getInternalState() == INTERNAL_STATE_FAILURE) {
          // this is unexpected and a bug.
          this.error = StorageException.fromErrorStatus(Status.RESULT_INTERNAL_ERROR);
        } else {
          this.error = null;
        }
      } else {
        this.error = error;
      }
    }

    /** Returns the {@link StorageTask} for this state. */
    @NonNull
    public StorageTask<ResultT> getTask() {
      return StorageTask.this;
    }

    /** Returns the target of the upload. */
    @NonNull
    public StorageReference getStorage() {
      return getTask().getStorage();
    }

    /** Returns the last error encountered. */
    @Override
    @Nullable
    public Exception getError() {
      return error;
    }
  }
}
