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

import androidx.annotation.NonNull;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import org.jetbrains.annotations.Nullable;

public class FirebaseAppDistribution {

  private final FirebaseApp firebaseApp;

  public FirebaseAppDistribution(FirebaseApp firebaseApp) {
    this.firebaseApp = firebaseApp;
  }

  /** @return a FirebaseInstallationsApi instance */
  @NonNull
  public static FirebaseAppDistribution getInstance() {
    return new FirebaseAppDistribution(FirebaseApp.getInstance());
  }

  /**
   * Updates the app to the latest release, if one is available. Returns the release information or
   * null if no update is found. Performs the following actions: 1. If tester is not signed in,
   * presents the tester with a Google sign in UI 2. Checks if a newer release is available. If so,
   * presents the tester with a confirmation dialog to begin the download. 3. For APKs, downloads
   * the binary and starts an installation intent. 4. For AABs, directs the tester to the Play app
   * to complete the download and installation.
   */
  @NonNull
  public Task<AppDistributionRelease> updateToLatestRelease() {
    return Tasks.forResult(null);
  }

  /**
   * Returns an AppDistributionRelease if one is available for the current signed in tester. If no
   * update is found, returns null. If tester is not signed in, presents the tester with a Google
   * sign in UI
   */
  @NonNull
  public Task<AppDistributionRelease> checkForUpdate() {
    return Tasks.forResult(null);
  }

  /**
   * Updates app to the latest release. If the latest release is an APK, downloads the binary and
   * starts an installation If the latest release is an AAB, directs the tester to the Play app to
   * complete the download and installation.
   *
   * @throws an UPDATE_NOT_AVAIALBLE exception if no new release is cached from checkForUpdate
   * @param updateProgressListener a callback function invoked as the update progresses
   */
  @NonNull
  public Task<Void> updateApp(@Nullable UpdateProgressListener updateProgressListener) {
    return Tasks.forResult(null);
  }

  /** Signs in the App Distribution tester. Presents the tester with a Google sign in UI */
  @NonNull
  public Task<Void> signInTester() {
    return Tasks.forResult(null);
  }

  /** Returns true if the App Distribution tester is signed in */
  @NonNull
  public boolean isTesterSignedIn() {
    return false;
  }

  /** Signs out the App Distribution tester */
  public void signOutTester() {}
}
