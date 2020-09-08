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
import androidx.annotation.Nullable;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.FirebaseApp;
import com.google.firebase.crashlytics.internal.Logger;

// Determines whether automatic data collection is enabled.
public class DataCollectionArbiter {
  private static final String FIREBASE_CRASHLYTICS_COLLECTION_ENABLED =
      "firebase_crashlytics_collection_enabled";

  private final SharedPreferences sharedPreferences;
  private final FirebaseApp firebaseApp;

  // State for waitForDataCollectionEnabled().
  private final Object taskLock = new Object();
  TaskCompletionSource<Void> dataCollectionEnabledTask = new TaskCompletionSource<>();
  boolean taskResolved = false;

  private boolean setInManifest = false;

  @Nullable private Boolean crashlyticsDataCollectionEnabled;

  /**
   * A Task that will be resolved when explicit data collection permission is granted by calling
   * grantDataCollectionPermission.
   */
  private TaskCompletionSource<Void> dataCollectionExplicitlyApproved =
      new TaskCompletionSource<>();

  public DataCollectionArbiter(FirebaseApp app) {
    final Context applicationContext = app.getApplicationContext();

    firebaseApp = app;
    sharedPreferences = CommonUtils.getSharedPrefs(applicationContext);

    Boolean dataCollectionEnabled = getDataCollectionValueFromSharedPreferences();
    if (dataCollectionEnabled == null) {
      dataCollectionEnabled = getDataCollectionValueFromManifest(applicationContext);
    }

    crashlyticsDataCollectionEnabled = dataCollectionEnabled;

    synchronized (taskLock) {
      if (isAutomaticDataCollectionEnabled()) {
        dataCollectionEnabledTask.trySetResult(null);
        taskResolved = true;
      }
    }
  }

  public synchronized boolean isAutomaticDataCollectionEnabled() {
    final boolean dataCollectionEnabled =
        crashlyticsDataCollectionEnabled != null
            ? crashlyticsDataCollectionEnabled
            : firebaseApp.isDataCollectionDefaultEnabled();
    logDataCollectionState(dataCollectionEnabled);
    return dataCollectionEnabled;
  }

  public synchronized void setCrashlyticsDataCollectionEnabled(@Nullable Boolean enabled) {
    if (enabled != null) {
      setInManifest = false;
    }

    crashlyticsDataCollectionEnabled =
        (enabled != null)
            ? enabled
            : getDataCollectionValueFromManifest(firebaseApp.getApplicationContext());
    storeDataCollectionValueInSharedPreferences(sharedPreferences, enabled);

    synchronized (taskLock) {
      if (isAutomaticDataCollectionEnabled()) {
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

  public Task<Void> waitForAutomaticDataCollectionEnabled() {
    synchronized (taskLock) {
      return dataCollectionEnabledTask.getTask();
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

  private void logDataCollectionState(boolean dataCollectionEnabled) {
    final String stateString = dataCollectionEnabled ? "ENABLED" : "DISABLED";
    final String fromString =
        crashlyticsDataCollectionEnabled == null
            ? "global Firebase setting"
            : setInManifest ? FIREBASE_CRASHLYTICS_COLLECTION_ENABLED + " manifest flag" : "API";
    Logger.getLogger()
        .d(
            String.format(
                "Crashlytics automatic data collection %s by %s.", stateString, fromString));
  }

  @Nullable
  private Boolean getDataCollectionValueFromSharedPreferences() {
    if (sharedPreferences.contains(FIREBASE_CRASHLYTICS_COLLECTION_ENABLED)) {
      setInManifest = false;
      return sharedPreferences.getBoolean(FIREBASE_CRASHLYTICS_COLLECTION_ENABLED, true);
    }
    return null;
  }

  @Nullable
  private Boolean getDataCollectionValueFromManifest(Context applicationContext) {
    final Boolean manifestSetting =
        readCrashlyticsDataCollectionEnabledFromManifest(applicationContext);
    if (manifestSetting == null) {
      setInManifest = false;
      return null;
    }
    setInManifest = true;
    return Boolean.TRUE.equals(manifestSetting);
  }

  @Nullable
  private static Boolean readCrashlyticsDataCollectionEnabledFromManifest(
      Context applicationContext) {
    try {
      final PackageManager packageManager = applicationContext.getPackageManager();
      if (packageManager != null) {
        final ApplicationInfo applicationInfo =
            packageManager.getApplicationInfo(
                applicationContext.getPackageName(), PackageManager.GET_META_DATA);
        if (applicationInfo != null
            && applicationInfo.metaData != null
            && applicationInfo.metaData.containsKey(FIREBASE_CRASHLYTICS_COLLECTION_ENABLED)) {
          return applicationInfo.metaData.getBoolean(FIREBASE_CRASHLYTICS_COLLECTION_ENABLED);
        }
      }
    } catch (PackageManager.NameNotFoundException e) {
      // This shouldn't happen since it's this app's package, but fall through to default
      // if so.
      Logger.getLogger().d("Unable to get PackageManager. Falling through", e);
    }
    return null;
  }

  @SuppressLint({"ApplySharedPref"})
  private static void storeDataCollectionValueInSharedPreferences(
      SharedPreferences sharedPreferences, Boolean enabled) {
    final SharedPreferences.Editor prefsEditor = sharedPreferences.edit();
    if (enabled != null) {
      prefsEditor.putBoolean(FIREBASE_CRASHLYTICS_COLLECTION_ENABLED, enabled);
    } else {
      prefsEditor.remove(FIREBASE_CRASHLYTICS_COLLECTION_ENABLED);
    }
    prefsEditor.commit();
  }
}
