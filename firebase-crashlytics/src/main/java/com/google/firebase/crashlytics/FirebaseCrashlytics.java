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
import com.google.firebase.analytics.connector.AnalyticsConnector.AnalyticsConnectorHandle;
import com.google.firebase.crashlytics.internal.CrashlyticsNativeComponent;
import com.google.firebase.crashlytics.internal.Logger;
import com.google.firebase.crashlytics.internal.MissingNativeComponent;
import com.google.firebase.crashlytics.internal.Onboarding;
import com.google.firebase.crashlytics.internal.analytics.AnalyticsEventLogger;
import com.google.firebase.crashlytics.internal.analytics.BlockingAnalyticsEventLogger;
import com.google.firebase.crashlytics.internal.analytics.BreadcrumbAnalyticsEventReceiver;
import com.google.firebase.crashlytics.internal.analytics.CrashlyticsOriginAnalyticsEventLogger;
import com.google.firebase.crashlytics.internal.analytics.UnavailableAnalyticsEventLogger;
import com.google.firebase.crashlytics.internal.breadcrumbs.BreadcrumbSource;
import com.google.firebase.crashlytics.internal.breadcrumbs.DisabledBreadcrumbSource;
import com.google.firebase.crashlytics.internal.common.CrashlyticsCore;
import com.google.firebase.crashlytics.internal.common.DataCollectionArbiter;
import com.google.firebase.crashlytics.internal.common.ExecutorUtils;
import com.google.firebase.crashlytics.internal.common.IdManager;
import com.google.firebase.crashlytics.internal.settings.SettingsController;
import com.google.firebase.installations.FirebaseInstallationsApi;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

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

  private static final String FIREBASE_CRASHLYTICS_ANALYTICS_ORIGIN = "clx";
  private static final String LEGACY_CRASH_ANALYTICS_ORIGIN = "crash";
  private static final int APP_EXCEPTION_CALLBACK_TIMEOUT_MS = 500;

  static @Nullable FirebaseCrashlytics init(
      @NonNull FirebaseApp app,
      @NonNull FirebaseInstallationsApi firebaseInstallationsApi,
      @Nullable CrashlyticsNativeComponent nativeComponent,
      @Nullable AnalyticsConnector analyticsConnector) {
    Context context = app.getApplicationContext();
    // Set up the IdManager.
    final String appIdentifier = context.getPackageName();
    final IdManager idManager = new IdManager(context, appIdentifier, firebaseInstallationsApi);

    final DataCollectionArbiter arbiter = new DataCollectionArbiter(app);

    if (nativeComponent == null) {
      nativeComponent = new MissingNativeComponent();
    }

    final Onboarding onboarding = new Onboarding(app, context, idManager, arbiter);

    // Integration with Firebase Analytics

    // Supplies breadcrumb events
    final BreadcrumbSource breadcrumbSource;
    // Facade for logging events to FA from Crashlytics.
    final AnalyticsEventLogger analyticsEventLogger;

    if (analyticsConnector != null) {
      // If FA is available, create a logger to log events from the Crashlytics origin.
      Logger.getLogger().d("Firebase Analytics is available.");
      final CrashlyticsOriginAnalyticsEventLogger directAnalyticsEventLogger =
          new CrashlyticsOriginAnalyticsEventLogger(analyticsConnector);

      // Create a listener to register for events coming from FA, which supplies both breadcrumbs
      // as well as Crashlytics-origin events through different streams.
      final CrashlyticsAnalyticsListener crashlyticsAnalyticsListener =
          new CrashlyticsAnalyticsListener();

      // Registering our listener with FA should return a "handle", in which case we know we've
      // registered successfully. Subsequent calls to register a listener will return null.
      final AnalyticsConnectorHandle analyticsConnectorHandle =
          subscribeToAnalyticsEvents(analyticsConnector, crashlyticsAnalyticsListener);

      if (analyticsConnectorHandle != null) {
        Logger.getLogger().d("Firebase Analytics listener registered successfully.");
        // Create the event receiver which will supply breadcrumb events to Crashlytics
        final BreadcrumbAnalyticsEventReceiver breadcrumbReceiver =
            new BreadcrumbAnalyticsEventReceiver();
        // Logging events to FA is an asynchronous operation. This logger will send events to
        // FA and block until FA returns the same event back to us, from the Crashlytics origin.
        // However, in the case that data collection has been disabled on FA, we will not receive
        // the event back (it will be silently dropped), so we set up a short timeout after which
        // we will assume that FA data collection is disabled and move on.
        final BlockingAnalyticsEventLogger blockingAnalyticsEventLogger =
            new BlockingAnalyticsEventLogger(
                directAnalyticsEventLogger,
                APP_EXCEPTION_CALLBACK_TIMEOUT_MS,
                TimeUnit.MILLISECONDS);

        // Set the appropriate event receivers to receive events from the FA listener
        crashlyticsAnalyticsListener.setBreadcrumbEventReceiver(breadcrumbReceiver);
        crashlyticsAnalyticsListener.setCrashlyticsOriginEventReceiver(
            blockingAnalyticsEventLogger);

        // Set the breadcrumb event receiver as the breadcrumb source for Crashlytics.
        breadcrumbSource = breadcrumbReceiver;
        // Set the blocking analytics event logger for Crashlytics.
        analyticsEventLogger = blockingAnalyticsEventLogger;
      } else {
        Logger.getLogger().d("Firebase Analytics listener registration failed.");
        // FA is enabled, but the listener was not registered successfully.
        // We cannot listen for breadcrumbs.
        breadcrumbSource = new DisabledBreadcrumbSource();
        // We cannot listen for Crashlytics origin events, but we can still send events, so set the
        // non-blocking analytics event logger for Crashlytics.
        analyticsEventLogger = directAnalyticsEventLogger;
      }
    } else {
      // FA is entirely unavailable. We cannot listen for breadcrumbs or send events.
      Logger.getLogger().d("Firebase Analytics is unavailable.");
      breadcrumbSource = new DisabledBreadcrumbSource();
      analyticsEventLogger = new UnavailableAnalyticsEventLogger();
    }

    final ExecutorService crashHandlerExecutor =
        ExecutorUtils.buildSingleThreadExecutorService("Crashlytics Exception Handler");
    final CrashlyticsCore core =
        new CrashlyticsCore(
            app,
            idManager,
            nativeComponent,
            arbiter,
            breadcrumbSource,
            analyticsEventLogger,
            crashHandlerExecutor);

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

  private static AnalyticsConnectorHandle subscribeToAnalyticsEvents(
      @NonNull AnalyticsConnector analyticsConnector,
      @NonNull CrashlyticsAnalyticsListener listener) {
    AnalyticsConnectorHandle handle =
        analyticsConnector.registerAnalyticsConnectorListener(
            FIREBASE_CRASHLYTICS_ANALYTICS_ORIGIN, listener);

    if (handle == null) {
      Logger.getLogger()
          .d("Could not register AnalyticsConnectorListener with Crashlytics origin.");
      // Older versions of FA don't support CRASHLYTICS_ORIGIN. We can try using the old Firebase
      // Crash Reporting origin
      handle =
          analyticsConnector.registerAnalyticsConnectorListener(
              LEGACY_CRASH_ANALYTICS_ORIGIN, listener);

      // If FA allows us to connect with the legacy origin, but not the new one, nudge customers
      // to update their FA version.
      if (handle != null) {
        Logger.getLogger()
            .w(
                "A new version of the Google Analytics for Firebase SDK is now available. "
                    + "For improved performance and compatibility with Crashlytics, please "
                    + "update to the latest version.");
      }
    }

    return handle;
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
   * @return a Task that is resolved with the result.
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
   * @return true if a crash was recorded during the previous run of the app.
   */
  public boolean didCrashOnPreviousExecution() {
    return core.didCrashOnPreviousExecution();
  }

  /**
   * Enables or disables the automatic data collection configuration for Crashlytics.
   *
   * <p>If this is set, it overrides any automatic data collection settings configured in the
   * AndroidManifest.xml as well as any Firebase-wide settings.
   *
   * <p>If automatic data collection is disabled for Crashlytics, crash reports are stored on the
   * device. To check for reports, use the {@link #checkForUnsentReports()} method. Use {@link
   * #sendUnsentReports()} to upload existing reports even when automatic data collection is
   * disabled. Use {@link #deleteUnsentReports()} to delete any reports stored on the device without
   * sending them to Crashlytics.
   *
   * @param enabled whether to enable automatic data collection. When set to `false`, the new value
   *     does not apply until the next run of the app. To disable data collection by default for all
   *     app runs, add the `firebase_crashlytics_collection_enabled` flag to your app's
   *     AndroidManifest.xml.
   */
  public void setCrashlyticsCollectionEnabled(boolean enabled) {
    core.setCrashlyticsCollectionEnabled(enabled);
  }

  /**
   * Enables or disables the automatic data collection configuration for Crashlytics.
   *
   * <p>If this is set, it overrides any automatic data collection settings configured in the
   * AndroidManifest.xml as well as any Firebase-wide settings. If set to `null`, the override is
   * cleared.
   *
   * <p>If automatic data collection is disabled for Crashlytics, crash reports are stored on the
   * device. To check for reports, use the {@link #checkForUnsentReports()} method. Use {@link
   * #sendUnsentReports()} to upload existing reports even when automatic data collection is
   * disabled. Use {@link #deleteUnsentReports()} to delete any reports stored on the device without
   * sending them to Crashlytics.
   *
   * @param enabled whether to enable or disable automatic data collection. When set to `false`, the
   *     new value does not apply until the next run of the app. When set to `null`, the override is
   *     cleared and automatic data collection settings are determined by the configuration in your
   *     AndroidManifest.xml or other Firebase-wide settings.
   */
  public void setCrashlyticsCollectionEnabled(@Nullable Boolean enabled) {
    core.setCrashlyticsCollectionEnabled(enabled);
  }
}
