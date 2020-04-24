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

package com.google.firebase.crashlytics;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.analytics.connector.AnalyticsConnector;
import com.google.firebase.crashlytics.internal.CrashlyticsNativeComponent;
import com.google.firebase.crashlytics.internal.Logger;
import com.google.firebase.crashlytics.internal.MissingNativeComponent;
import com.google.firebase.crashlytics.internal.Onboarding;
import com.google.firebase.crashlytics.internal.common.CrashlyticsCore;
import com.google.firebase.crashlytics.internal.common.DataCollectionArbiter;
import com.google.firebase.crashlytics.internal.common.ExecutorUtils;
import com.google.firebase.crashlytics.internal.common.IdManager;
import com.google.firebase.crashlytics.internal.settings.SettingsController;
import com.google.firebase.iid.internal.FirebaseInstanceIdInternal;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

/**
 * The Firebase Crashlytics API provides methods to annotate and manage fatal and non-fatal reports
 * captured and reported to Firebase Crashlytics.
 *
 * <p>By default, Firebase Crashlytics is automatically initialized.
 *
 * <p>Call {@link FirebaseCrashlytics#getInstance()} to get the singleton instance of
 * FirebaseCrashlytics.
 */
public class FirebaseCrashlytics {

  static @Nullable FirebaseCrashlytics init(
      @NonNull FirebaseApp app,
      @NonNull FirebaseInstanceIdInternal instanceId,
      @Nullable CrashlyticsNativeComponent nativeComponent,
      @Nullable AnalyticsConnector analyticsConnector) {
    Context context = app.getApplicationContext();
    // Set up the IdManager.
    final String appIdentifier = context.getPackageName();
    final IdManager idManager = new IdManager(context, appIdentifier, instanceId);

    final DataCollectionArbiter arbiter = new DataCollectionArbiter(app);

    if (nativeComponent == null) {
      nativeComponent = new MissingNativeComponent();
    }

    final Onboarding onboarding = new Onboarding(app, context, idManager, arbiter);
    final CrashlyticsCore core =
        new CrashlyticsCore(app, idManager, nativeComponent, arbiter, analyticsConnector);

    if (!onboarding.onPreExecute()) {
      Logger.getLogger().e("Unable to start Crashlytics.");
      return null;
    }

    final ExecutorService threadPoolExecutor =
        ExecutorUtils.buildSingleThreadExecutorService("com.google.firebase.crashlytics.startup");
    final SettingsController settingsController =
        onboarding.retrieveSettingsData(context, app, threadPoolExecutor);

    final boolean finishCoreInBackground = core.onPreExecute(settingsController);

    Tasks.call(
        threadPoolExecutor,
        new Callable<Void>() {
          @Override
          public Void call() throws Exception {
            onboarding.doOnboarding(threadPoolExecutor, settingsController);
            if (finishCoreInBackground) {
              core.doBackgroundInitializationAsync(settingsController);
            }
            return null;
          }
        });

    return new FirebaseCrashlytics(core);
  }

  private final CrashlyticsCore core;

  private FirebaseCrashlytics(@NonNull CrashlyticsCore core) {
    this.core = core;
  }

  /**
   * Gets the singleton {@link FirebaseCrashlytics} instance.
   *
   * <p>The default {@link FirebaseApp} instance must be initialized before this function is called.
   * See <a
   * href="https://firebase.google.com/docs/reference/android/com/google/firebase/FirebaseApp">
   * FirebaseApp</a> for more information.
   */
  @NonNull
  public static FirebaseCrashlytics getInstance() {
    final FirebaseApp app = FirebaseApp.getInstance();
    final FirebaseCrashlytics instance = app.get(FirebaseCrashlytics.class);
    if (instance == null) {
      throw new NullPointerException("FirebaseCrashlytics component is not present.");
    }
    return instance;
  }

  /**
   * Records a non-fatal report to send to Crashlytics.
   *
   * @param throwable a {@link Throwable} to be recorded as a non-fatal event.
   */
  public void recordException(@NonNull Throwable throwable) {
    if (throwable == null) { // Users could call this with null despite the annotation.
      Logger.getLogger().w("Crashlytics is ignoring a request to log a null exception.");
      return;
    }
    core.logException(throwable);
  }

  /**
   * Logs a message that's included in the next fatal or non-fatal report.
   *
   * <p>Logs are visible in the session view on the Firebase Crashlytics console.
   *
   * <p>Newline characters are stripped and extremely long messages are truncated. The maximum log
   * size is 64k. If exceeded, the log rolls such that messages are removed, starting from the
   * oldest.
   *
   * @param message the message to be logged
   */
  public void log(@NonNull String message) {
    core.log(message);
  }

  /**
   * Records a user ID (identifier) that's associated with subsequent fatal and non-fatal reports.
   *
   * <p>The user ID is visible in the session view on the Firebase Crashlytics console.
   *
   * <p>Identifiers longer than 1024 characters will be truncated.
   *
   * @param identifier a unique identifier for the current user
   */
  public void setUserId(@NonNull String identifier) {
    core.setUserId(identifier);
  }

  /**
   * Sets a custom key and value that are associated with subsequent fatal and non-fatal reports.
   *
   * <p>Multiple calls to this method with the same key update the value for that key.
   *
   * <p>The value of any key at the time of a fatal or non-fatal event is associated with that
   * event.
   *
   * <p>Keys and associated values are visible in the session view on the Firebase Crashlytics
   * console.
   *
   * <p>Accepts a maximum of 64 key/value pairs. New keys beyond that limit are ignored. Keys or
   * values that exceed 1024 characters are truncated.
   *
   * @param key A unique key
   * @param value A value to be associated with the given key
   */
  public void setCustomKey(@NonNull String key, boolean value) {
    core.setCustomKey(key, Boolean.toString(value));
  }

  /**
   * Sets a custom key and value that are associated with subsequent fatal and non-fatal reports.
   *
   * <p>Multiple calls to this method with the same key update the value for that key.
   *
   * <p>The value of any key at the time of a fatal or non-fatal event is associated with that
   * event.
   *
   * <p>Keys and associated values are visible in the session view on the Firebase Crashlytics
   * console.
   *
   * <p>Accepts a maximum of 64 key/value pairs. New keys beyond that limit are ignored. Keys or
   * values that exceed 1024 characters are truncated.
   *
   * @param key A unique key
   * @param value A value to be associated with the given key
   */
  public void setCustomKey(@NonNull String key, double value) {
    core.setCustomKey(key, Double.toString(value));
  }

  /**
   * Sets a custom key and value that are associated with subsequent fatal and non-fatal reports.
   *
   * <p>Multiple calls to this method with the same key update the value for that key.
   *
   * <p>The value of any key at the time of a fatal or non-fatal event is associated with that
   * event.
   *
   * <p>Keys and associated values are visible in the session view on the Firebase Crashlytics
   * console.
   *
   * <p>Accepts a maximum of 64 key/value pairs. New keys beyond that limit are ignored. Keys or
   * values that exceed 1024 characters are truncated.
   *
   * @param key A unique key
   * @param value A value to be associated with the given key
   */
  public void setCustomKey(@NonNull String key, float value) {
    core.setCustomKey(key, Float.toString(value));
  }

  /**
   * Sets a custom key and value that are associated with subsequent fatal and non-fatal reports.
   *
   * <p>Multiple calls to this method with the same key update the value for that key.
   *
   * <p>The value of any key at the time of a fatal or non-fatal event is associated with that
   * event.
   *
   * <p>Keys and associated values are visible in the session view on the Firebase Crashlytics
   * console.
   *
   * <p>Accepts a maximum of 64 key/value pairs. New keys beyond that limit are ignored. Keys or
   * values that exceed 1024 characters are truncated.
   *
   * @param key A unique key
   * @param value A value to be associated with the given key
   */
  public void setCustomKey(@NonNull String key, int value) {
    core.setCustomKey(key, Integer.toString(value));
  }

  /**
   * Records a custom key and value to be associated with subsequent fatal and non-fatal reports.
   *
   * <p>Multiple calls to this method with the same key will update the value for that key.
   *
   * <p>The value of any key at the time of a fatal or non-fatal event will be associated with that
   * event.
   *
   * <p>Keys and associated values are visible in the session view on the Firebase Crashlytics
   * console.
   *
   * <p>A maximum of 64 key/value pairs can be written, and new keys added beyond that limit will be
   * ignored. Keys or values that exceed 1024 characters will be truncated.
   *
   * @param key A unique key
   * @param value A value to be associated with the given key
   */
  public void setCustomKey(@NonNull String key, long value) {
    core.setCustomKey(key, Long.toString(value));
  }

  /**
   * Sets a custom key and value that are associated with subsequent fatal and non-fatal reports.
   *
   * <p>Multiple calls to this method with the same key update the value for that key.
   *
   * <p>The value of any key at the time of a fatal or non-fatal event is associated with that
   * event.
   *
   * <p>Keys and associated values are visible in the session view on the Firebase Crashlytics
   * console.
   *
   * <p>Accepts a maximum of 64 key/value pairs. New keys beyond that limit are ignored. Keys or
   * values that exceed 1024 characters are truncated.
   *
   * @param key A unique key
   * @param value A value to be associated with the given key
   */
  public void setCustomKey(@NonNull String key, @NonNull String value) {
    core.setCustomKey(key, value);
  }

  // region Unsent report management.

  /**
   * Checks a device for any fatal or non-fatal crash reports that haven't yet been sent to
   * Crashlytics. If automatic data collection is enabled, then reports are uploaded automatically
   * and this always returns false. If automatic data collection is disabled, this method can be
   * used to check whether the user opts-in to send crash reports from their device.
   *
   * @returns a Task that is resolved with the result.
   */
  @NonNull
  public Task<Boolean> checkForUnsentReports() {
    return core.checkForUnsentReports();
  }

  /**
   * If automatic data collection is disabled, this method queues up all the reports on a device to
   * send to Crashlytics. Otherwise, this method is a no-op.
   */
  public void sendUnsentReports() {
    core.sendUnsentReports();
  }

  /**
   * If automatic data collection is disabled, this method queues up all the reports on a device for
   * deletion. Otherwise, this method is a no-op.
   */
  public void deleteUnsentReports() {
    core.deleteUnsentReports();
  }

  // endregion

  /**
   * Checks whether the app crashed on its previous run.
   *
   * @returns true if a crash was recorded during the previous run of the app.
   */
  public boolean didCrashOnPreviousExecution() {
    return core.didCrashOnPreviousExecution();
  }

  /**
   * Enables/disables automatic data collection by Crashlytics.
   *
   * <p>If this is set, it overrides the data collection settings provided by the Android Manifest,
   * as well as any Firebase-wide automatic data collection settings.
   *
   * <p>If automatic data collection is disabled for Crashlytics, crash reports are stored on the
   * device. To check for reports, use the {@link #checkForUnsentReports()} method. Use {@link
   * #sendUnsentReports()} to upload existing reports even when automatic data collection is
   * disabled. Use {@link #deleteUnsentReports()} to delete any reports stored on the device without
   * sending them to Crashlytics.
   *
   * @param enabled whether to enable automatic data collection. When set to `false`, the new value
   *     does not apply until the next run of the app. If you want to disable data collection by
   *     default for all app runs, add the `firebase_crashlytics_collection_enabled` flag to your
   *     app's AndroidManifest.xml.
   */
  public void setCrashlyticsCollectionEnabled(boolean enabled) {
    core.setCrashlyticsCollectionEnabled(enabled);
  }
}
