// Copyright 2019 Google LLC
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

package com.google.firebase.crashlytics.internal.common;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.FirebaseApp;
import com.google.firebase.crashlytics.internal.Logger;

// Determines whether automatic data collection is enabled.
public class DataCollectionArbiter {
  private static final String FIREBASE_CRASHLYTICS_COLLECTION_ENABLED =
      "firebase_crashlytics_collection_enabled";

  // State for waitForDataCollectionEnabled().
  private Object taskLock = new Object();
  TaskCompletionSource<Void> dataCollectionEnabledTask = new TaskCompletionSource<>();
  boolean taskResolved = false;

  private final SharedPreferences sharedPreferences;
  private volatile boolean crashlyticsDataCollectionExplicitlySet;
  private volatile boolean crashlyticsDataCollectionEnabled;
  private final FirebaseApp firebaseApp;

  /**
   * A Task that will be resolved when explicit data collection permission is granted by calling
   * grantDataCollectionPermission.
   */
  private TaskCompletionSource<Void> dataCollectionExplicitlyApproved =
      new TaskCompletionSource<>();

  public DataCollectionArbiter(FirebaseApp app) {
    this.firebaseApp = app;
    Context applicationContext = app.getApplicationContext();
    if (applicationContext == null) {
      throw new RuntimeException("null context");
    }

    sharedPreferences = CommonUtils.getSharedPrefs(applicationContext);

    boolean enabled = true;
    boolean explicitlySet = false;

    if (sharedPreferences.contains(FIREBASE_CRASHLYTICS_COLLECTION_ENABLED)) {
      enabled = sharedPreferences.getBoolean(FIREBASE_CRASHLYTICS_COLLECTION_ENABLED, true);
      explicitlySet = true;
    } else {
      try {
        final PackageManager packageManager = applicationContext.getPackageManager();
        if (packageManager != null) {
          final ApplicationInfo applicationInfo =
              packageManager.getApplicationInfo(
                  applicationContext.getPackageName(), PackageManager.GET_META_DATA);
          if (applicationInfo != null
              && applicationInfo.metaData != null
              && applicationInfo.metaData.containsKey(FIREBASE_CRASHLYTICS_COLLECTION_ENABLED)) {
            enabled = applicationInfo.metaData.getBoolean(FIREBASE_CRASHLYTICS_COLLECTION_ENABLED);
            explicitlySet = true;
          }
        }
      } catch (PackageManager.NameNotFoundException e) {
        // This shouldn't happen since it's this app's package, but fall through to default
        // if so.
        Logger.getLogger().d("Unable to get PackageManager. Falling through", e);
      }
    }

    crashlyticsDataCollectionEnabled = enabled;
    crashlyticsDataCollectionExplicitlySet = explicitlySet;

    synchronized (taskLock) {
      if (isAutomaticDataCollectionEnabled()) {
        dataCollectionEnabledTask.trySetResult(null);
        taskResolved = true;
      }
    }
  }

  public boolean isAutomaticDataCollectionEnabled() {
    if (crashlyticsDataCollectionExplicitlySet) {
      return crashlyticsDataCollectionEnabled;
    }
    return firebaseApp.isDataCollectionDefaultEnabled();
  }

  public Task<Void> waitForAutomaticDataCollectionEnabled() {
    synchronized (taskLock) {
      return dataCollectionEnabledTask.getTask();
    }
  }

  @SuppressLint({"CommitPrefEdits", "ApplySharedPref"})
  public void setCrashlyticsDataCollectionEnabled(boolean enabled) {
    crashlyticsDataCollectionEnabled = enabled;
    crashlyticsDataCollectionExplicitlySet = true;
    sharedPreferences.edit().putBoolean(FIREBASE_CRASHLYTICS_COLLECTION_ENABLED, enabled).commit();

    synchronized (taskLock) {
      if (enabled) {
        if (!taskResolved) {
          dataCollectionEnabledTask.trySetResult(null);
          taskResolved = true;
        }
      } else {
        if (taskResolved) {
          dataCollectionEnabledTask = new TaskCompletionSource<>();
          taskResolved = false;
        }
      }
    }
  }

  /**
   * Returns a task which will be resolved when either: 1) automatic data collection has been
   * enabled, or 2) grantDataCollectionPermission has been called.
   */
  public Task<Void> waitForDataCollectionPermission() {
    return Utils.race(
        dataCollectionExplicitlyApproved.getTask(), waitForAutomaticDataCollectionEnabled());
  }

  /**
   * Signals that explicit permission to collection data has been granted by the user, which will
   * allow fetching settings and doing onboarding even if automatic data collection is disabled.
   * This method should only be called for operations that are necessary to collect data in order to
   * ensure crash reports get uploaded properly.
   *
   * @param dataCollectionToken a valid data collection
   */
  public void grantDataCollectionPermission(boolean dataCollectionToken) {
    if (!dataCollectionToken) {
      throw new IllegalStateException("An invalid data collection token was used.");
    }
    dataCollectionExplicitlyApproved.trySetResult(null);
  }
}
