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

package com.google.firebase.appdistribution.internal;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCanceledListener;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.SuccessContinuation;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.appdistribution.AppDistributionRelease;
import com.google.firebase.appdistribution.FirebaseAppDistribution;
import com.google.firebase.appdistribution.FirebaseAppDistributionException;
import com.google.firebase.appdistribution.FirebaseAppDistributionException.Status;
import com.google.firebase.appdistribution.InterruptionLevel;
import com.google.firebase.appdistribution.OnProgressListener;
import com.google.firebase.appdistribution.UpdateTask;
import java.util.concurrent.Executor;

/**
 * This stuib implementation of the Firebase App Distribution API will return failed {@link Task
 * Tasks}/{@link UpdateTask UpdateTasks} with {@link
 * FirebaseAppDistributionException.Status#NOT_IMPLEMENTED}.
 */
// TODO(b/261013680): Use an explicit executor in continuations.
@SuppressLint("TaskMainThread")
public class FirebaseAppDistributionStub implements FirebaseAppDistribution {
  @NonNull
  @Override
  public UpdateTask updateIfNewReleaseAvailable() {
    return new NotImplementedUpdateTask();
  }

  @Override
  public boolean isTesterSignedIn() {
    return false;
  }

  @NonNull
  @Override
  public Task<Void> signInTester() {
    return getNotImplementedTask();
  }

  @Override
  public void signOutTester() {}

  @NonNull
  @Override
  public synchronized Task<AppDistributionRelease> checkForNewRelease() {
    return getNotImplementedTask();
  }

  @NonNull
  @Override
  public UpdateTask updateApp() {
    return new NotImplementedUpdateTask();
  }

  @Override
  public void startFeedback(@StringRes int additionalFormText) {}

  @Override
  public void startFeedback(@NonNull CharSequence additionalFormText) {}

  @Override
  public void startFeedback(@StringRes int additionalFormText, @Nullable Uri screenshotUri) {}

  @Override
  public void startFeedback(
      @NonNull CharSequence additionalFormText, @Nullable Uri screenshotUri) {}

  @Override
  public void showFeedbackNotification(
      @StringRes int additionalFormText, @NonNull InterruptionLevel interruptionLevel) {}

  @Override
  public void showFeedbackNotification(
      @NonNull CharSequence additionalFormText, @NonNull InterruptionLevel interruptionLevel) {}

  @Override
  public void cancelFeedbackNotification() {}

  private static <TResult> Task<TResult> getNotImplementedTask() {
    return Tasks.forException(
        new FirebaseAppDistributionException(
            "This API is not implemented. The build was compiled against the API only.",
            Status.NOT_IMPLEMENTED));
  }

  private static class NotImplementedUpdateTask extends UpdateTask {
    private final Task<Void> task = getNotImplementedTask();

    @NonNull
    @Override
    public <TContinuationResult> Task<TContinuationResult> continueWith(
        @NonNull Continuation<Void, TContinuationResult> continuation) {
      return task.continueWith(continuation);
    }

    @NonNull
    @Override
    public <TContinuationResult> Task<TContinuationResult> continueWith(
        @NonNull Executor executor, @NonNull Continuation<Void, TContinuationResult> continuation) {
      return task.continueWith(executor, continuation);
    }

    @NonNull
    @Override
    public <TContinuationResult> Task<TContinuationResult> continueWithTask(
        @NonNull Continuation<Void, Task<TContinuationResult>> continuation) {
      return task.continueWithTask(continuation);
    }

    @NonNull
    @Override
    public <TContinuationResult> Task<TContinuationResult> continueWithTask(
        @NonNull Executor executor,
        @NonNull Continuation<Void, Task<TContinuationResult>> continuation) {
      return task.continueWithTask(executor, continuation);
    }

    @NonNull
    @Override
    public <TContinuationResult> Task<TContinuationResult> onSuccessTask(
        @NonNull SuccessContinuation<Void, TContinuationResult> successContinuation) {
      return task.onSuccessTask(successContinuation);
    }

    @NonNull
    @Override
    public <TContinuationResult> Task<TContinuationResult> onSuccessTask(
        @NonNull Executor executor,
        @NonNull SuccessContinuation<Void, TContinuationResult> successContinuation) {
      return task.onSuccessTask(executor, successContinuation);
    }

    @NonNull
    @Override
    public UpdateTask addOnProgressListener(@NonNull OnProgressListener listener) {
      return this;
    }

    @NonNull
    @Override
    public UpdateTask addOnProgressListener(
        @Nullable Executor executor, @NonNull OnProgressListener listener) {
      return this;
    }

    @Override
    public boolean isComplete() {
      return task.isComplete();
    }

    @Override
    public boolean isSuccessful() {
      return task.isSuccessful();
    }

    @Override
    public boolean isCanceled() {
      return task.isCanceled();
    }

    @Nullable
    @Override
    public Void getResult() {
      return task.getResult();
    }

    @Nullable
    @Override
    public <X extends Throwable> Void getResult(@NonNull Class<X> aClass) throws X {
      return task.getResult(aClass);
    }

    @Nullable
    @Override
    public Exception getException() {
      return task.getException();
    }

    @NonNull
    @Override
    public Task<Void> addOnSuccessListener(
        @NonNull OnSuccessListener<? super Void> onSuccessListener) {
      task.addOnSuccessListener(onSuccessListener);
      return this;
    }

    @NonNull
    @Override
    public Task<Void> addOnSuccessListener(
        @NonNull Executor executor, @NonNull OnSuccessListener<? super Void> onSuccessListener) {
      task.addOnSuccessListener(executor, onSuccessListener);
      return this;
    }

    @NonNull
    @Override
    public Task<Void> addOnSuccessListener(
        @NonNull Activity activity, @NonNull OnSuccessListener<? super Void> onSuccessListener) {
      task.addOnSuccessListener(activity, onSuccessListener);
      return this;
    }

    @NonNull
    @Override
    public Task<Void> addOnFailureListener(@NonNull OnFailureListener onFailureListener) {
      task.addOnFailureListener(onFailureListener);
      return this;
    }

    @NonNull
    @Override
    public Task<Void> addOnFailureListener(
        @NonNull Executor executor, @NonNull OnFailureListener onFailureListener) {
      task.addOnFailureListener(executor, onFailureListener);
      return this;
    }

    @NonNull
    @Override
    public Task<Void> addOnFailureListener(
        @NonNull Activity activity, @NonNull OnFailureListener onFailureListener) {
      task.addOnFailureListener(activity, onFailureListener);
      return this;
    }

    @NonNull
    @Override
    public Task<Void> addOnCompleteListener(@NonNull OnCompleteListener<Void> onCompleteListener) {
      return task.addOnCompleteListener(onCompleteListener);
    }

    @NonNull
    @Override
    public Task<Void> addOnCompleteListener(
        @NonNull Executor executor, @NonNull OnCompleteListener<Void> onCompleteListener) {
      return task.addOnCompleteListener(executor, onCompleteListener);
    }

    @NonNull
    @Override
    public Task<Void> addOnCompleteListener(
        @NonNull Activity activity, @NonNull OnCompleteListener<Void> onCompleteListener) {
      return task.addOnCompleteListener(activity, onCompleteListener);
    }

    @NonNull
    @Override
    public Task<Void> addOnCanceledListener(@NonNull OnCanceledListener onCanceledListener) {
      return task.addOnCanceledListener(onCanceledListener);
    }

    @NonNull
    @Override
    public Task<Void> addOnCanceledListener(
        @NonNull Executor executor, @NonNull OnCanceledListener onCanceledListener) {
      return task.addOnCanceledListener(executor, onCanceledListener);
    }

    @NonNull
    @Override
    public Task<Void> addOnCanceledListener(
        @NonNull Activity activity, @NonNull OnCanceledListener onCanceledListener) {
      return task.addOnCanceledListener(activity, onCanceledListener);
    }
  }
}
