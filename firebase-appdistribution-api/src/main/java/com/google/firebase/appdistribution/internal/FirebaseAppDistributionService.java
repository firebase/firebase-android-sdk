package com.google.firebase.appdistribution.internal;

import androidx.annotation.NonNull;
import com.google.android.gms.tasks.Task;
import com.google.firebase.appdistribution.AppDistributionRelease;
import com.google.firebase.appdistribution.UpdateTask;

public interface FirebaseAppDistributionService {
  @NonNull
  Task<AppDistributionRelease> checkForNewRelease();

  boolean isTesterSignedIn();

  @NonNull
  Task<Void> signInTester();

  void signOutTester();

  @NonNull
  UpdateTask updateApp();

  @NonNull
  UpdateTask updateIfNewReleaseAvailable();
}
