// Copyright 2021 Google LLC
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

import android.app.Activity;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCanceledListener;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.SuccessContinuation;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.TaskExecutors;
import com.google.firebase.appdistribution.OnProgressListener;
import com.google.firebase.appdistribution.UpdateProgress;
import com.google.firebase.appdistribution.UpdateTask;
import com.google.firebase.concurrent.FirebaseExecutors;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/** Implementation of UpdateTask, the return type of updateApp. */
class UpdateTaskImpl extends UpdateTask {

  @Nullable
  @GuardedBy("lock")
  private List<ManagedListener> listeners = new ArrayList<>();

  private final Object lock = new Object();
  private final Object taskCompletionLock = new Object();

  @GuardedBy("lock")
  private UpdateProgress snapshot;

  @GuardedBy("taskCompletionLock")
  private final TaskCompletionSource<Void> taskCompletionSource;

  UpdateTaskImpl() {
    synchronized (taskCompletionLock) {
      taskCompletionSource = new TaskCompletionSource<>();
    }
  }

  void updateProgress(@NonNull UpdateProgress updateProgress) {
    synchronized (lock) {
      snapshot = updateProgress;
      for (ManagedListener listener : listeners) {
        listener.invoke(updateProgress);
      }
    }
  }

  /**
   * Listen to all completion and progress events on the given {@link UpdateTask}, updating the
   * progress or completing this task with the same changes.
   */
  void shadow(UpdateTask updateTask) {
    // Using direct executor here ensures that any handlers that were themselves added using a
    // direct executor will behave as expected: they'll be executed on the thread that sets the
    // result or updates progress.
    updateTask
        .addOnProgressListener(FirebaseExecutors.directExecutor(), this::updateProgress)
        .addOnSuccessListener(FirebaseExecutors.directExecutor(), unused -> setResult())
        .addOnFailureListener(FirebaseExecutors.directExecutor(), this::setException);
  }

  private Task<Void> getTask() {
    synchronized (taskCompletionLock) {
      return taskCompletionSource.getTask();
    }
  }

  @NonNull
  @Override
  public UpdateTask addOnProgressListener(@NonNull OnProgressListener listener) {
    return addOnProgressListener(null, listener);
  }

  @NonNull
  @Override
  public UpdateTask addOnProgressListener(
      @Nullable Executor executor, @NonNull OnProgressListener listener) {
    ManagedListener managedListener = new ManagedListener(executor, listener);
    synchronized (lock) {
      listeners.add(managedListener);
      if (snapshot != null) {
        managedListener.invoke(snapshot);
      }
    }
    return this;
  }

  @Override
  public boolean isComplete() {
    return getTask().isComplete();
  }

  @Override
  public boolean isSuccessful() {
    return getTask().isSuccessful();
  }

  @Override
  public boolean isCanceled() {
    return getTask().isCanceled();
  }

  @Nullable
  @Override
  public Void getResult() {
    return getTask().getResult();
  }

  @Nullable
  @Override
  public <X extends Throwable> Void getResult(@NonNull Class<X> aClass) throws X {
    return getTask().getResult(aClass);
  }

  @Nullable
  @Override
  public Exception getException() {
    return getTask().getException();
  }

  @NonNull
  @Override
  public Task<Void> addOnSuccessListener(
      @NonNull OnSuccessListener<? super Void> onSuccessListener) {
    return getTask().addOnSuccessListener(onSuccessListener);
  }

  @NonNull
  @Override
  public Task<Void> addOnSuccessListener(
      @NonNull Executor executor, @NonNull OnSuccessListener<? super Void> onSuccessListener) {
    return getTask().addOnSuccessListener(executor, onSuccessListener);
  }

  @NonNull
  @Override
  public Task<Void> addOnSuccessListener(
      @NonNull Activity activity, @NonNull OnSuccessListener<? super Void> onSuccessListener) {
    return getTask().addOnSuccessListener(activity, onSuccessListener);
  }

  @NonNull
  @Override
  public Task<Void> addOnFailureListener(@NonNull OnFailureListener onFailureListener) {
    return getTask().addOnFailureListener(onFailureListener);
  }

  @NonNull
  @Override
  public Task<Void> addOnFailureListener(
      @NonNull Executor executor, @NonNull OnFailureListener onFailureListener) {
    return getTask().addOnFailureListener(executor, onFailureListener);
  }

  @NonNull
  @Override
  public Task<Void> addOnFailureListener(
      @NonNull Activity activity, @NonNull OnFailureListener onFailureListener) {
    return getTask().addOnFailureListener(activity, onFailureListener);
  }

  @NonNull
  @Override
  public Task<Void> addOnCompleteListener(@NonNull OnCompleteListener<Void> onCompleteListener) {
    return getTask().addOnCompleteListener(onCompleteListener);
  }

  @NonNull
  @Override
  public Task<Void> addOnCompleteListener(
      @NonNull Executor executor, @NonNull OnCompleteListener<Void> onCompleteListener) {
    return getTask().addOnCompleteListener(executor, onCompleteListener);
  }

  @NonNull
  @Override
  public Task<Void> addOnCompleteListener(
      @NonNull Activity activity, @NonNull OnCompleteListener<Void> onCompleteListener) {
    return getTask().addOnCompleteListener(activity, onCompleteListener);
  }

  @NonNull
  @Override
  public Task<Void> addOnCanceledListener(@NonNull OnCanceledListener onCanceledListener) {
    return getTask().addOnCanceledListener(onCanceledListener);
  }

  @NonNull
  @Override
  public Task<Void> addOnCanceledListener(
      @NonNull Executor executor, @NonNull OnCanceledListener onCanceledListener) {
    return getTask().addOnCanceledListener(executor, onCanceledListener);
  }

  @NonNull
  @Override
  public Task<Void> addOnCanceledListener(
      @NonNull Activity activity, @NonNull OnCanceledListener onCanceledListener) {
    return getTask().addOnCanceledListener(activity, onCanceledListener);
  }

  @NonNull
  @Override
  public <TContinuationResult> Task<TContinuationResult> continueWith(
      @NonNull Continuation<Void, TContinuationResult> continuation) {
    return getTask().continueWith(continuation);
  }

  @NonNull
  @Override
  public <TContinuationResult> Task<TContinuationResult> continueWith(
      @NonNull Executor executor, @NonNull Continuation<Void, TContinuationResult> continuation) {
    return getTask().continueWith(executor, continuation);
  }

  @NonNull
  @Override
  public <TContinuationResult> Task<TContinuationResult> continueWithTask(
      @NonNull Continuation<Void, Task<TContinuationResult>> continuation) {
    return getTask().continueWithTask(continuation);
  }

  @NonNull
  @Override
  public <TContinuationResult> Task<TContinuationResult> continueWithTask(
      @NonNull Executor executor,
      @NonNull Continuation<Void, Task<TContinuationResult>> continuation) {
    return getTask().continueWithTask(executor, continuation);
  }

  @NonNull
  @Override
  public <TContinuationResult> Task<TContinuationResult> onSuccessTask(
      @NonNull SuccessContinuation<Void, TContinuationResult> successContinuation) {
    return getTask().onSuccessTask(successContinuation);
  }

  @NonNull
  @Override
  public <TContinuationResult> Task<TContinuationResult> onSuccessTask(
      @NonNull Executor executor,
      @NonNull SuccessContinuation<Void, TContinuationResult> successContinuation) {
    return getTask().onSuccessTask(executor, successContinuation);
  }

  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  public void setResult() {

    synchronized (lock) {
      listeners.clear();
    }

    synchronized (taskCompletionLock) {
      if (!taskCompletionSource.getTask().isComplete()) {
        taskCompletionSource.setResult(null);
      }
    }
  }

  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  public void setException(@NonNull Exception exception) {

    synchronized (lock) {
      listeners.clear();
    }

    synchronized (taskCompletionLock) {
      if (!taskCompletionSource.getTask().isComplete()) {
        taskCompletionSource.setException(exception);
      }
    }
  }

  /** Wraps a listener and its corresponding executor. */
  private static class ManagedListener {
    Executor executor;
    OnProgressListener listener;

    ManagedListener(@Nullable Executor executor, OnProgressListener listener) {
      this.executor = executor != null ? executor : TaskExecutors.MAIN_THREAD;
      this.listener = listener;
    }

    /**
     * If the provided snapshot is non-null, executes the listener on the provided executor. If no
     * executor was specified, uses the main thread.
     */
    public void invoke(UpdateProgress snapshot) {
      executor.execute(() -> listener.onProgressUpdate(snapshot));
    }
  }
}
