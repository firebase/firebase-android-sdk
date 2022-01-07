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

package com.google.firebase.appdistribution;

import android.app.Activity;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.tasks.OnCanceledListener;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.appdistribution.FirebaseAppDistributionException.Status;
import java.util.concurrent.Executor;

/** Stubbed version of the Firebase App Distribution API */
public class FirebaseAppDistribution {

  FirebaseAppDistribution() {}

  /** @return a FirebaseAppDistribution instance */
  @NonNull
  public static FirebaseAppDistribution getInstance() {
    return new FirebaseAppDistribution();
  }

  /**
   * Stubbed version of {@code updateIfNewReleaseAvailable()}.
   *
   * @return an {@link UpdateTask} that will fail with a {@link FirebaseAppDistributionException}
   *     with status {@link Status#APP_RUNNING_IN_PRODUCTION}.
   */
  @NonNull
  public UpdateTask updateIfNewReleaseAvailable() {
    return new AppInProductionUpdateTask();
  }

  /**
   * Stubbed version of {@code signInTester()}.
   *
   * @return a {@link Task} that will fail with a {@link FirebaseAppDistributionException} with
   *     status {@link Status#APP_RUNNING_IN_PRODUCTION}.
   */
  @NonNull
  public Task<Void> signInTester() {
    return getAppInProductionTask();
  }

  /**
   * Stubbed version of {@code checkForNewRelease()}.
   *
   * @return a {@link Task} that will fail with a {@link FirebaseAppDistributionException} with
   *     status {@link Status#APP_RUNNING_IN_PRODUCTION}.
   */
  @NonNull
  public synchronized Task<AppDistributionRelease> checkForNewRelease() {
    return getAppInProductionTask();
  }

  /**
   * Stubbed version of {@code updateApp()}.
   *
   * @return an {@link UpdateTask} that will fail with a {@link FirebaseAppDistributionException}
   *     with status {@link Status#APP_RUNNING_IN_PRODUCTION}.
   */
  @NonNull
  public UpdateTask updateApp() {
    return new AppInProductionUpdateTask();
  }

  /**
   * Stubbed version of {@code isTesterSignedIn()}.
   *
   * @return false
   */
  public boolean isTesterSignedIn() {
    return false;
  }

  /** Stubbed version of {@code signOutTester()}. */
  public void signOutTester() {}

  private static <TResult> Task<TResult> getAppInProductionTask() {
    return Tasks.forException(
        new FirebaseAppDistributionException(
            "App is running in production. App Distribution is disabled.",
            Status.APP_RUNNING_IN_PRODUCTION));
  }

  private static class AppInProductionUpdateTask extends UpdateTask {
    private final Task<Void> task = getAppInProductionTask();

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
