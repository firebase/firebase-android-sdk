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
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import java.util.concurrent.Executor;

public class FirebaseAppDistribution {

  /** @return a FirebaseAppDistribution instance */
  @NonNull
  public static FirebaseAppDistribution getInstance() {
    return new FirebaseAppDistribution();
  }

  /**
   * Updates the app to the newest release, if one is available. Returns the release information or
   * null if no update is found. Performs the following actions: 1. If tester is not signed in,
   * presents the tester with a Google sign in UI 2. Checks if a newer release is available. If so,
   * presents the tester with a confirmation dialog to begin the download. 3. For APKs, downloads
   * the binary and starts an installation intent. 4. For AABs, directs the tester to the Play app
   * to complete the download and installation.
   */
  @NonNull
  public UpdateTask updateIfNewReleaseAvailable() {
    return new CompletedUpdateTask();
  }

  /** Signs in the App Distribution tester. Presents the tester with a Google sign in UI */
  @NonNull
  public Task<Void> signInTester() {
    return Tasks.forResult(null);
  }

  /**
   * Returns an AppDistributionRelease if one is available for the current signed in tester. If no
   * update is found, returns null. If tester is not signed in, presents the tester with a Google
   * sign in UI
   */
  @NonNull
  public synchronized Task<AppDistributionRelease> checkForNewRelease() {
    return Tasks.forResult(null);
  }

  /**
   * Updates app to the newest release. If the newest release is an APK, downloads the binary and
   * starts an installation If the newest release is an AAB, directs the tester to the Play app to
   * complete the download and installation.
   *
   * <p>cancels task with FirebaseAppDistributionException with UPDATE_NOT_AVAILABLE exception if no
   * new release is cached from checkForNewRelease
   */
  @NonNull
  public UpdateTask updateApp() {
    return new CompletedUpdateTask();
  }

  /** Returns true if the App Distribution tester is signed in */
  public boolean isTesterSignedIn() {
    return false;
  }

  /** Signs out the App Distribution tester */
  public void signOutTester() {}

  private static class CompletedUpdateTask extends UpdateTask {
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
      return true;
    }

    @Override
    public boolean isSuccessful() {
      return true;
    }

    @Override
    public boolean isCanceled() {
      return false;
    }

    @Nullable
    @Override
    public Void getResult() {
      return null;
    }

    @Nullable
    @Override
    public <X extends Throwable> Void getResult(@NonNull Class<X> aClass) throws X {
      return null;
    }

    @Nullable
    @Override
    public Exception getException() {
      return null;
    }

    @NonNull
    @Override
    public Task<Void> addOnSuccessListener(
        @NonNull OnSuccessListener<? super Void> onSuccessListener) {
      return this;
    }

    @NonNull
    @Override
    public Task<Void> addOnSuccessListener(
        @NonNull Executor executor, @NonNull OnSuccessListener<? super Void> onSuccessListener) {
      return this;
    }

    @NonNull
    @Override
    public Task<Void> addOnSuccessListener(
        @NonNull Activity activity, @NonNull OnSuccessListener<? super Void> onSuccessListener) {
      return this;
    }

    @NonNull
    @Override
    public Task<Void> addOnFailureListener(@NonNull OnFailureListener onFailureListener) {
      return this;
    }

    @NonNull
    @Override
    public Task<Void> addOnFailureListener(
        @NonNull Executor executor, @NonNull OnFailureListener onFailureListener) {
      return this;
    }

    @NonNull
    @Override
    public Task<Void> addOnFailureListener(
        @NonNull Activity activity, @NonNull OnFailureListener onFailureListener) {
      return this;
    }
  }
}
