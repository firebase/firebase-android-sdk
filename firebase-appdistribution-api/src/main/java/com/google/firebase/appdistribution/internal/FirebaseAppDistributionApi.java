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

package com.google.firebase.appdistribution.internal;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.OnCanceledListener;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.appdistribution.AppDistributionRelease;
import com.google.firebase.appdistribution.FirebaseAppDistributionException;
import com.google.firebase.appdistribution.FirebaseAppDistributionException.Status;
import com.google.firebase.appdistribution.FirebaseAppDistribution;
import com.google.firebase.appdistribution.OnProgressListener;
import com.google.firebase.appdistribution.UpdateTask;
import com.google.firebase.inject.Provider;

import java.util.concurrent.Executor;

/**
 * The Firebase App Distribution API provides methods to update the app to the most recent
 * pre-release build.
 *
 * <p>By default, Firebase App Distribution is automatically initialized.
 *
 * <p>Call {@link FirebaseAppDistributionApi#getInstance()} to get the singleton instance of
 * FirebaseAppDistribution.
 */
public class FirebaseAppDistributionApi implements FirebaseAppDistribution {

  private final Provider<FirebaseAppDistribution> firebaseAppDistributionImplProvider;

  /** Constructor for FirebaseAppDistribution. */
  public FirebaseAppDistributionApi(
      Provider<FirebaseAppDistribution> firebaseAppDistributionImplProvider) {
    this.firebaseAppDistributionImplProvider = firebaseAppDistributionImplProvider;
  }

  /**
   * Updates the app to the newest release, if one is available.
   *
   * <p>Returns the release information or {@code null} if no update is found. Performs the
   * following actions:
   *
   * <ol>
   *   <li>If tester is not signed in, presents the tester with a Google Sign-in UI.
   *   <li>Checks if a newer release is available. If so, presents the tester with a confirmation
   *       dialog to begin the download.
   *   <li>If the newest release is an APK, downloads the binary and starts an installation. If the
   *       newest release is an AAB, directs the tester to the Play app to complete the download and
   *       installation.
   * </ol>
   */
  @NonNull
  public UpdateTask updateIfNewReleaseAvailable() {
    if (firebaseAppDistributionImplProvider.get() == null) {
      return new AppInProductionUpdateTask();
    }
    return firebaseAppDistributionImplProvider.get().updateIfNewReleaseAvailable();
  }

  /** Signs in the App Distribution tester. Presents the tester with a Google sign in UI. */
  @NonNull
  public Task<Void> signInTester() {
    if (firebaseAppDistributionImplProvider.get() == null) {
      return getNotImplementedTask();
    }
    return firebaseAppDistributionImplProvider.get().signInTester();
  }

  /**
   * Returns an {@link AppDistributionRelease} if an update is available for the current signed in
   * tester, or {@code null} otherwise.
   */
  @NonNull
  public synchronized Task<AppDistributionRelease> checkForNewRelease() {
    if (firebaseAppDistributionImplProvider.get() == null) {
      return getNotImplementedTask();
    }
    return firebaseAppDistributionImplProvider.get().checkForNewRelease();
  }

  /**
   * Updates app to the {@link AppDistributionRelease} returned by {@link #checkForNewRelease}.
   *
   * <p>If the newest release is an APK, downloads the binary and starts an installation. If the
   * newest release is an AAB, directs the tester to the Play app to complete the download and
   * installation.
   *
   * <p>Cancels task with {@link Status#UPDATE_NOT_AVAILABLE} if no new release is cached from
   * {@link #checkForNewRelease}.
   */
  @NonNull
  public UpdateTask updateApp() {
    if (firebaseAppDistributionImplProvider.get() == null) {
      return new AppInProductionUpdateTask();
    }
    return firebaseAppDistributionImplProvider.get().updateApp();
  }

  /** Returns {@code true} if the App Distribution tester is signed in. */
  public boolean isTesterSignedIn() {
    if (firebaseAppDistributionImplProvider.get() == null) {
      return false;
    }
    return firebaseAppDistributionImplProvider.get().isTesterSignedIn();
  }

  /** Signs out the App Distribution tester. */
  public void signOutTester() {
    if (firebaseAppDistributionImplProvider.get() == null) {
      return;
    }
    firebaseAppDistributionImplProvider.get().signOutTester();
  }

  private static <TResult> Task<TResult> getNotImplementedTask() {
    return Tasks.forException(
        new FirebaseAppDistributionException(
            "This API is not implemented. The build was compiled against the API only.",
            Status.NOT_IMPLEMENTED));
  }

  private static class AppInProductionUpdateTask extends UpdateTask {
    private final Task<Void> task = getNotImplementedTask();

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
