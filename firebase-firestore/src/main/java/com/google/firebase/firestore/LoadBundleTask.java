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

package com.google.firebase.firestore;

import static com.google.firebase.firestore.util.Assert.hardAssert;

import android.app.Activity;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import com.google.android.gms.common.api.internal.ActivityLifecycleObserver;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCanceledListener;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.RuntimeExecutionException;
import com.google.android.gms.tasks.SuccessContinuation;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.TaskExecutors;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;

/**
 * Represents the task of loading a Firestore bundle. It provides progress of bundle loading, as
 * well as task completion and error events.
 */
public class LoadBundleTask extends Task<LoadBundleTaskProgress> {
  private final Object lock = new Object();

  /** The last progress update, or {@code null} if not yet available. */
  @GuardedBy("lock")
  private LoadBundleTaskProgress snapshot;

  /**
   * A TaskCompletionSource that is used to deliver all standard Task API events (such as,
   * `onComplete`).
   */
  private final TaskCompletionSource<LoadBundleTaskProgress> completionSource;

  /**
   * A delegate task derived from {@code completionSource}. All API events that don't involve
   * progress updates are handled by this delegate
   */
  private final Task<LoadBundleTaskProgress> delegate;

  /** A queue of active progress listeners. */
  @GuardedBy("lock")
  private final Queue<ManagedListener> progressListeners;

  /** @hide */
  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  public LoadBundleTask() {
    snapshot = LoadBundleTaskProgress.INITIAL;
    completionSource = new TaskCompletionSource<>();
    delegate = completionSource.getTask();
    progressListeners = new ArrayDeque<>();
  }

  /** Returns {@code true} if the {@code Task} is complete; {@code false} otherwise. */
  @Override
  public boolean isComplete() {
    return delegate.isComplete();
  }

  /**
   * Returns {@code true} if the {@code Task} has completed successfully; {@code false} otherwise.
   */
  @Override
  public boolean isSuccessful() {
    return delegate.isSuccessful();
  }

  /** Returns {@code true} if the task has been canceled. */
  @Override
  public boolean isCanceled() {
    return delegate.isCanceled();
  }

  /**
   * Gets the result of the {@code Task}, if it has already completed.
   *
   * @throws IllegalStateException if the Task is not yet complete
   * @throws RuntimeExecutionException if the Task failed with an exception
   */
  @NonNull
  @Override
  public LoadBundleTaskProgress getResult() {
    return delegate.getResult();
  }

  /**
   * Gets the result of the {@code Task}, if it has already completed.
   *
   * @throws IllegalStateException if the Task is not yet complete
   * @throws X if the Task failed with an exception of type X
   * @throws RuntimeExecutionException if the Task failed with an exception that was not of type X
   */
  @NonNull
  @Override
  public <X extends Throwable> LoadBundleTaskProgress getResult(@NonNull Class<X> exceptionType)
      throws X {
    return delegate.getResult(exceptionType);
  }

  /**
   * Returns the exception that caused the {@code Task} to fail. Returns {@code null} if the Task is
   * not yet complete, or completed successfully.
   */
  @Nullable
  @Override
  public Exception getException() {
    return delegate.getException();
  }

  /**
   * Adds a listener that is called if the {@code Task} completes successfully. The listener will be
   * called on the main application thread. If the task has already completed successfully, a call
   * to the listener will be immediately scheduled. If multiple listeners are added, they will be
   * called in the order in which they were added.
   *
   * @return this {@code Task}
   */
  @NonNull
  @Override
  public Task<LoadBundleTaskProgress> addOnSuccessListener(
      @NonNull OnSuccessListener<? super LoadBundleTaskProgress> onSuccessListener) {
    return delegate.addOnSuccessListener(onSuccessListener);
  }

  /**
   * Adds a listener that is called if the {@code Task} completes successfully.
   *
   * <p>If multiple listeners are added, they will be called in the order in which they were added.
   * If the task has already completed successfully, a call to the listener will be immediately
   * scheduled.
   *
   * @param executor the executor to use to call the listener
   * @return this {@code Task}
   */
  @NonNull
  @Override
  public Task<LoadBundleTaskProgress> addOnSuccessListener(
      @NonNull Executor executor,
      @NonNull OnSuccessListener<? super LoadBundleTaskProgress> onSuccessListener) {
    return delegate.addOnSuccessListener(executor, onSuccessListener);
  }

  /**
   * Adds a listener that is called if the {@code Task} completes successfully.
   *
   * <p>If multiple listeners are added, they will be called in the order in which they were added.
   * If the task has already completed successfully, a call to the listener will be immediately
   * scheduled.
   *
   * @param activity When the supplied {@link Activity} stops, this listener will automatically be
   *     removed.
   * @return this {@code Task}
   */
  @NonNull
  @Override
  public Task<LoadBundleTaskProgress> addOnSuccessListener(
      @NonNull Activity activity,
      @NonNull OnSuccessListener<? super LoadBundleTaskProgress> onSuccessListener) {
    return delegate.addOnSuccessListener(activity, onSuccessListener);
  }

  /**
   * Adds a listener that is called if the {@code Task} fails.
   *
   * <p>The listener will be called on main application thread. If the task has already failed, a
   * call to the listener will be immediately scheduled. If multiple listeners are added, they will
   * be called in the order in which they were added.
   *
   * @return this {@code Task}
   */
  @NonNull
  @Override
  public Task<LoadBundleTaskProgress> addOnFailureListener(
      @NonNull OnFailureListener onFailureListener) {
    return delegate.addOnFailureListener(onFailureListener);
  }

  /**
   * Adds a listener that is called if the {@code Task} fails.
   *
   * <p>If the task has already failed, a call to the listener will be immediately scheduled. If
   * multiple listeners are added, they will be called in the order in which they were added.
   *
   * @param executor the executor to use to call the listener
   * @return this {@code Task}
   */
  @NonNull
  @Override
  public Task<LoadBundleTaskProgress> addOnFailureListener(
      @NonNull Executor executor, @NonNull OnFailureListener onFailureListener) {
    return delegate.addOnFailureListener(executor, onFailureListener);
  }

  /**
   * Adds a listener that is called if the {@code Task} fails.
   *
   * <p>If the task has already failed, a call to the listener will be immediately scheduled. If
   * multiple listeners are added, they will be called in the order in which they were added.
   *
   * @param activity When the supplied {@link Activity} stops, this listener will automatically be
   *     removed.
   * @return this {@code Task}
   */
  @NonNull
  @Override
  public Task<LoadBundleTaskProgress> addOnFailureListener(
      @NonNull Activity activity, @NonNull OnFailureListener onFailureListener) {
    return delegate.addOnFailureListener(activity, onFailureListener);
  }

  /**
   * Adds a listener that is called when the {@code Task} succeeds or fails.
   *
   * <p>The listener will be called on main application thread. If the task has already failed, a
   * call to the listener will be immediately scheduled. If multiple listeners are added, they will
   * be called in the order in which they were added.
   *
   * @return this {@code Task}
   */
  @NonNull
  @Override
  public Task<LoadBundleTaskProgress> addOnCompleteListener(
      @NonNull OnCompleteListener<LoadBundleTaskProgress> onCompleteListener) {
    return delegate.addOnCompleteListener(onCompleteListener);
  }

  /**
   * Adds a listener that is called when the {@code Task} succeeds or fails.
   *
   * <p>If the task has already failed, a call to the listener will be immediately scheduled. If
   * multiple listeners are added, they will be called in the order in which they were added.
   *
   * @param executor the executor to use to call the listener
   * @return this {@code Task}
   */
  @NonNull
  @Override
  public Task<LoadBundleTaskProgress> addOnCompleteListener(
      @NonNull Executor executor,
      @NonNull OnCompleteListener<LoadBundleTaskProgress> onCompleteListener) {
    return delegate.addOnCompleteListener(executor, onCompleteListener);
  }

  /**
   * Adds a listener that is called when the {@code Task} succeeds or fails.
   *
   * <p>If the task has already failed, a call to the listener will be immediately scheduled. If
   * multiple listeners are added, they will be called in the order in which they were added.
   *
   * @param activity When the supplied {@link Activity} stops, this listener will automatically be
   *     removed.
   * @return this {@code Task}
   */
  @NonNull
  @Override
  public Task<LoadBundleTaskProgress> addOnCompleteListener(
      @NonNull Activity activity,
      @NonNull OnCompleteListener<LoadBundleTaskProgress> onCompleteListener) {
    return delegate.addOnCompleteListener(activity, onCompleteListener);
  }

  /**
   * Adds a listener that is called if the {@code Task} is canceled.
   *
   * <p>The listener will be called on main application thread. If the {@code Task} has already been
   * canceled, a call to the listener will be immediately scheduled. If multiple listeners are
   * added, they will be called in the order in which they were added.
   *
   * @return this {@code Task}
   */
  @NonNull
  @Override
  public Task<LoadBundleTaskProgress> addOnCanceledListener(
      @NonNull OnCanceledListener onCanceledListener) {
    return delegate.addOnCanceledListener(onCanceledListener);
  }

  /**
   * Adds a listener that is called if the {@code Task} is canceled.
   *
   * <p>If the task has already been canceled, a call to the listener will be immediately scheduled.
   * If multiple listeners are added, they will be called in the order in which they were added.
   *
   * @param executor the executor to use to call the listener
   * @return this Task
   */
  @NonNull
  @Override
  public Task<LoadBundleTaskProgress> addOnCanceledListener(
      @NonNull Executor executor, @NonNull OnCanceledListener onCanceledListener) {
    return delegate.addOnCanceledListener(executor, onCanceledListener);
  }

  /**
   * Adds an Activity-scoped listener that is called if the {@code Task} is canceled.
   *
   * <p>The listener will be called on main application thread. If the task has already been
   * canceled, a call to the listener will be immediately scheduled. If multiple listeners are
   * added, they will be called in the order in which they were added.
   *
   * <p>The listener will be automatically removed during {@link Activity#onStop}.
   *
   * @return this Task
   */
  @NonNull
  @Override
  public Task<LoadBundleTaskProgress> addOnCanceledListener(
      @NonNull Activity activity, @NonNull OnCanceledListener onCanceledListener) {
    return delegate.addOnCanceledListener(activity, onCanceledListener);
  }

  /**
   * Returns a new {@code Task} that will be completed with the result of applying the specified
   * Continuation to this Task.
   *
   * <p>The {@code Continuation} will be called on the main application thread.
   *
   * @see Continuation#then(Task)
   */
  @NonNull
  @Override
  public <TContinuationResult> Task<TContinuationResult> continueWith(
      @NonNull Continuation<LoadBundleTaskProgress, TContinuationResult> continuation) {
    return delegate.continueWith(continuation);
  }

  /**
   * Returns a new {@code Task} that will be completed with the result of applying the specified
   * {@code Continuation} to this Task.
   *
   * @param executor the executor to use to call the Continuation
   * @see Continuation#then(Task)
   */
  @NonNull
  @Override
  public <TContinuationResult> Task<TContinuationResult> continueWith(
      @NonNull Executor executor,
      @NonNull Continuation<LoadBundleTaskProgress, TContinuationResult> continuation) {
    return delegate.continueWith(executor, continuation);
  }

  /**
   * Returns a new {@code Task} that will be completed with the result of applying the specified
   * {@code Continuation} to this Task.
   *
   * <p>The Continuation will be called on the main application thread.
   *
   * @see Continuation#then(Task)
   */
  @NonNull
  @Override
  public <TContinuationResult> Task<TContinuationResult> continueWithTask(
      @NonNull Continuation<LoadBundleTaskProgress, Task<TContinuationResult>> continuation) {
    return delegate.continueWithTask(continuation);
  }

  /**
   * Returns a new {@code Task} that will be completed with the result of applying the specified
   * {@code Continuation} to this Task.
   *
   * @param executor the executor to use to call the {@code Continuation}
   * @see Continuation#then(Task)
   */
  @NonNull
  @Override
  public <TContinuationResult> Task<TContinuationResult> continueWithTask(
      @NonNull Executor executor,
      @NonNull Continuation<LoadBundleTaskProgress, Task<TContinuationResult>> continuation) {
    return delegate.continueWithTask(executor, continuation);
  }

  /**
   * Returns a new {@code Task} that will be completed with the result of applying the specified
   * {@code SuccessContinuation} to this {@code Task} when this {@code Task} completes successfully.
   * If the previous {@code Task} fails, the {@code onSuccessTask} completion will be skipped and
   * failure listeners will be invoked.
   *
   * <p>The {@code SuccessContinuation} will be called on the main application thread.
   *
   * <p>If the previous {@code Task} is canceled, the returned Task will also be canceled and the
   * {@code SuccessContinuation} would not execute.
   *
   * @see SuccessContinuation#then(ResultT)
   */
  @NonNull
  @Override
  public <TContinuationResult> Task<TContinuationResult> onSuccessTask(
      @NonNull
          SuccessContinuation<LoadBundleTaskProgress, TContinuationResult> successContinuation) {
    return delegate.onSuccessTask(successContinuation);
  }

  /**
   * Returns a new {@code Task} that will be completed with the result of applying the specified
   * {@code SuccessContinuation} to this {@code Task} when this {@code Task} completes successfully.
   * If the previous {@code Task} fails, the {@code }onSuccessTask completion will be skipped and
   * failure listeners will be invoked.
   *
   * <p>If the previous {@code Task} is canceled, the returned {@code Task} will also be canceled
   * and the {@code SuccessContinuation} would not execute.
   *
   * @param executor the executor to use to call the SuccessContinuation
   * @see SuccessContinuation#then(ResultT)
   */
  @NonNull
  @Override
  public <TContinuationResult> Task<TContinuationResult> onSuccessTask(
      @NonNull Executor executor,
      @NonNull
          SuccessContinuation<LoadBundleTaskProgress, TContinuationResult> successContinuation) {
    return delegate.onSuccessTask(executor, successContinuation);
  }

  /**
   * Adds a listener that is called periodically while the {@code LoadBundleTask} executes.
   *
   * <p>The listener will be called on main application thread. If multiple listeners are added,
   * they will be called in the order in which they were added.
   *
   * <p>The listener will be automatically removed during {@link Activity#onStop}.
   *
   * @return this {@code Task}
   */
  @NonNull
  public LoadBundleTask addOnProgressListener(
      @NonNull OnProgressListener<LoadBundleTaskProgress> listener) {
    ManagedListener managedListener = new ManagedListener(/* executor= */ null, listener);
    synchronized (lock) {
      progressListeners.add(managedListener);
    }
    return this;
  }

  /**
   * Adds a listener that is called periodically while the {@code LoadBundleTask} executes.
   *
   * <p>If multiple listeners are added, they will be called in the order in which they were added.
   *
   * @param executor the executor to use to call the listener
   * @return this {@code Task}
   */
  @NonNull
  public LoadBundleTask addOnProgressListener(
      @NonNull Executor executor, @NonNull OnProgressListener<LoadBundleTaskProgress> listener) {
    ManagedListener managedListener = new ManagedListener(executor, listener);
    synchronized (lock) {
      progressListeners.add(managedListener);
    }
    return this;
  }

  /**
   * Adds a listener that is called periodically while the {@code LoadBundleTask} executes.
   *
   * <p>The listener will be called on main application thread. If multiple listeners are added,
   * they will be called in the order in which they were added.
   *
   * <p>The listener will be automatically removed during {@link Activity#onStop}.
   *
   * @param activity When the supplied {@link Activity} stops, this listener will automatically be
   *     removed.
   * @return this {@code Task}
   */
  @NonNull
  public LoadBundleTask addOnProgressListener(
      @NonNull Activity activity, @NonNull OnProgressListener<LoadBundleTaskProgress> listener) {
    ManagedListener managedListener = new ManagedListener(/* executor= */ null, listener);
    synchronized (lock) {
      progressListeners.add(managedListener);
    }
    ActivityLifecycleObserver.of(activity).onStopCallOnce(() -> removeOnProgressListener(listener));
    return this;
  }

  /**
   * Removes a listener.
   *
   * <p>To match the Android Task API and its usage across Firestore, this method is private.
   */
  private void removeOnProgressListener(
      @NonNull OnProgressListener<LoadBundleTaskProgress> listener) {
    synchronized (lock) {
      progressListeners.remove(new ManagedListener(/* executor= */ null, listener));
    }
  }

  /** @hide */
  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  public void setResult(@NonNull LoadBundleTaskProgress result) {
    hardAssert(
        result.getTaskState().equals(LoadBundleTaskProgress.TaskState.SUCCESS),
        "Expected success, but was " + result.getTaskState());
    synchronized (lock) {
      snapshot = result;
      for (ManagedListener listener : progressListeners) {
        listener.invokeAsync(snapshot);
      }
      progressListeners.clear();
    }

    completionSource.setResult(result);
  }

  /** @hide */
  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  public void setException(@NonNull Exception exception) {
    LoadBundleTaskProgress snapshot;
    synchronized (lock) {
      snapshot =
          new LoadBundleTaskProgress(
              this.snapshot.getDocumentsLoaded(),
              this.snapshot.getTotalDocuments(),
              this.snapshot.getBytesLoaded(),
              this.snapshot.getTotalBytes(),
              exception,
              LoadBundleTaskProgress.TaskState.ERROR);
      this.snapshot = snapshot;
      for (ManagedListener listener : progressListeners) {
        listener.invokeAsync(snapshot);
      }
      progressListeners.clear();
    }

    completionSource.setException(exception);
  }

  /** @hide */
  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  public void updateProgress(@NonNull LoadBundleTaskProgress progressUpdate) {
    synchronized (lock) {
      snapshot = progressUpdate;
      for (ManagedListener listener : progressListeners) {
        listener.invokeAsync(progressUpdate);
      }
    }
  }

  /** Wraps a listener and its corresponding executor. */
  private static class ManagedListener {
    Executor executor;
    OnProgressListener<LoadBundleTaskProgress> listener;

    ManagedListener(
        @Nullable Executor executor, OnProgressListener<LoadBundleTaskProgress> listener) {
      this.executor = executor != null ? executor : TaskExecutors.MAIN_THREAD;
      this.listener = listener;
    }

    /**
     * If the provided snapshot is non-null, executes the listener on the provided executor. If no
     * executor was specified, uses the main thread.
     */
    public void invokeAsync(LoadBundleTaskProgress snapshot) {
      executor.execute(() -> listener.onProgress(snapshot));
    }

    /**
     * Equality implementation that only compares the listener for equality. This allows listeners
     * to detached regardless of whether they were registered with an activity or an executor.
     */
    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ManagedListener that = (ManagedListener) o;
      return listener.equals(that.listener);
    }

    @Override
    public int hashCode() {
      return listener.hashCode();
    }
  }
}
