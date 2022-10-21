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

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import com.google.android.gms.tasks.Task;
import com.google.firebase.appdistribution.AppDistributionRelease;
import com.google.firebase.appdistribution.FirebaseAppDistribution;
import com.google.firebase.appdistribution.FirebaseAppDistributionException;
import com.google.firebase.appdistribution.InterruptionLevel;
import com.google.firebase.appdistribution.UpdateTask;
import com.google.firebase.inject.Provider;

/**
 * This implementation of the Firebase App Distribution API proxies to the real implementation
 * ({@code FirebaseAppDistributionImpl}) provided by the {@code
 * com.google.firebase:firebase-appdistribution} artifact. If that artifact is not included in the
 * build, then the stubs will return failed {@link Task Tasks}/{@link UpdateTask UpdateTasks} with
 * {@link FirebaseAppDistributionException.Status#NOT_IMPLEMENTED}.
 */
public class FirebaseAppDistributionProxy implements FirebaseAppDistribution {
  private final FirebaseAppDistribution delegate;

  public FirebaseAppDistributionProxy(
      Provider<FirebaseAppDistribution> firebaseAppDistributionImplProvider) {
    FirebaseAppDistribution impl = firebaseAppDistributionImplProvider.get();
    delegate = impl != null ? impl : new FirebaseAppDistributionStub();
  }

  @NonNull
  @Override
  public UpdateTask updateIfNewReleaseAvailable() {
    return delegate.updateIfNewReleaseAvailable();
  }

  @Override
  public boolean isTesterSignedIn() {
    return delegate.isTesterSignedIn();
  }

  @NonNull
  @Override
  public Task<Void> signInTester() {
    return delegate.signInTester();
  }

  @Override
  public void signOutTester() {
    delegate.signOutTester();
  }

  @NonNull
  @Override
  public synchronized Task<AppDistributionRelease> checkForNewRelease() {
    return delegate.checkForNewRelease();
  }

  @NonNull
  @Override
  public UpdateTask updateApp() {
    return delegate.updateApp();
  }

  @Override
  public void startFeedback(@StringRes int infoTextResourceId) {
    delegate.startFeedback(infoTextResourceId);
  }

  @Override
  public void startFeedback(@NonNull CharSequence infoText) {
    delegate.startFeedback(infoText);
  }

  @Override
  public void startFeedback(@StringRes int infoTextResourceId, @Nullable Uri screenshotUri) {
    delegate.startFeedback(infoTextResourceId, screenshotUri);
  }

  @Override
  public void startFeedback(@NonNull CharSequence infoText, @Nullable Uri screenshotUri) {
    delegate.startFeedback(infoText, screenshotUri);
  }

  @Override
  public void showFeedbackNotification(
      @StringRes int infoTextResourceId, @NonNull InterruptionLevel interruptionLevel) {
    delegate.showFeedbackNotification(infoTextResourceId, interruptionLevel);
  }

  @Override
  public void showFeedbackNotification(
      @NonNull CharSequence infoText, @NonNull InterruptionLevel interruptionLevel) {
    delegate.showFeedbackNotification(infoText, interruptionLevel);
  }

  @Override
  public void cancelFeedbackNotification() {
    delegate.cancelFeedbackNotification();
  }
}
