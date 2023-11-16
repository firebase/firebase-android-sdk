// Copyright 2020 Google LLC
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

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.firebase.FirebaseApp;
import com.google.firebase.crashlytics.BuildConfig;
import com.google.firebase.crashlytics.internal.CrashlyticsNativeComponent;
import com.google.firebase.crashlytics.internal.Logger;
import com.google.firebase.crashlytics.internal.RemoteConfigDeferredProxy;
import com.google.firebase.crashlytics.internal.analytics.AnalyticsEventLogger;
import com.google.firebase.crashlytics.internal.breadcrumbs.BreadcrumbSource;
import com.google.firebase.crashlytics.internal.metadata.LogFileManager;
import com.google.firebase.crashlytics.internal.metadata.UserMetadata;
import com.google.firebase.crashlytics.internal.persistence.FileStore;
import com.google.firebase.crashlytics.internal.settings.Settings;
import com.google.firebase.crashlytics.internal.settings.SettingsProvider;
import com.google.firebase.crashlytics.internal.stacktrace.MiddleOutFallbackStrategy;
import com.google.firebase.crashlytics.internal.stacktrace.RemoveRepeatsStrategy;
import com.google.firebase.crashlytics.internal.stacktrace.StackTraceTrimmingStrategy;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@SuppressWarnings("PMD.NullAssignment")
public class CrashlyticsCore {
  private static final String MISSING_BUILD_ID_MSG =
      "The Crashlytics build ID is missing. This occurs when the Crashlytics Gradle plugin is "
          + "missing from your app's build configuration. Please review the Firebase Crashlytics "
          + "onboarding instructions at "
          + "https://firebase.google.com/docs/crashlytics/get-started?platform=android#add-plugin";

  static final int MAX_STACK_SIZE = 1024;
  static final int NUM_STACK_REPETITIONS_ALLOWED = 10;

  // Build ID related constants
  static final String CRASHLYTICS_REQUIRE_BUILD_ID = "com.crashlytics.RequireBuildId";
  static final boolean CRASHLYTICS_REQUIRE_BUILD_ID_DEFAULT = true;

  static final int DEFAULT_MAIN_HANDLER_TIMEOUT_SEC = 3;

  private static final String ON_DEMAND_RECORDED_KEY =
      "com.crashlytics.on-demand.recorded-exceptions";
  private static final String ON_DEMAND_DROPPED_KEY =
      "com.crashlytics.on-demand.dropped-exceptions";

  // If this marker sticks around, the app is crashing before we finished initializing
  private static final String INITIALIZATION_MARKER_FILE_NAME = "initialization_marker";
  static final String CRASH_MARKER_FILE_NAME = "crash_marker";

  private final Context context;
  private final FirebaseApp app;
  private final DataCollectionArbiter dataCollectionArbiter;
  private final OnDemandCounter onDemandCounter;

  private final long startTime;

  private CrashlyticsFileMarker initializationMarker;
  private CrashlyticsFileMarker crashMarker;
  private boolean didCrashOnPreviousExecution;

  private CrashlyticsController controller;
  private final IdManager idManager;
  private final FileStore fileStore;

  @VisibleForTesting public final BreadcrumbSource breadcrumbSource;
  private final AnalyticsEventLogger analyticsEventLogger;

  private final ExecutorService crashHandlerExecutor;
  private final CrashlyticsBackgroundWorker backgroundWorker;
  private final CrashlyticsAppQualitySessionsSubscriber sessionsSubscriber;

  private final CrashlyticsNativeComponent nativeComponent;

  private final RemoteConfigDeferredProxy remoteConfigDeferredProxy;

  // region Constructors

  public CrashlyticsCore(
      FirebaseApp app,
      IdManager idManager,
      CrashlyticsNativeComponent nativeComponent,
      DataCollectionArbiter dataCollectionArbiter,
      BreadcrumbSource breadcrumbSource,
      AnalyticsEventLogger analyticsEventLogger,
      FileStore fileStore,
      ExecutorService crashHandlerExecutor,
      CrashlyticsAppQualitySessionsSubscriber sessionsSubscriber,
      RemoteConfigDeferredProxy remoteConfigDeferredProxy) {
    this.app = app;
    this.dataCollectionArbiter = dataCollectionArbiter;
    this.context = app.getApplicationContext();
    this.idManager = idManager;
    this.nativeComponent = nativeComponent;
    this.breadcrumbSource = breadcrumbSource;
    this.analyticsEventLogger = analyticsEventLogger;
    this.crashHandlerExecutor = crashHandlerExecutor;
    this.fileStore = fileStore;
    this.backgroundWorker = new CrashlyticsBackgroundWorker(crashHandlerExecutor);
    this.sessionsSubscriber = sessionsSubscriber;
    this.remoteConfigDeferredProxy = remoteConfigDeferredProxy;

    startTime = System.currentTimeMillis();
    onDemandCounter = new OnDemandCounter();
  }

  // endregion

  // region Initialization

  public boolean onPreExecute(AppData appData, SettingsProvider settingsProvider) {
    // before starting the crash detector make sure that this was built with our build
    // tools.
    // Throw an exception and halt the app if the build ID is required and not present.
    // TODO: This flag is no longer supported and should be removed, as part of a larger refactor
    //  now that the buildId is now only used for mapping file association.
    final boolean requiresBuildId =
        CommonUtils.getBooleanResourceValue(
            context, CRASHLYTICS_REQUIRE_BUILD_ID, CRASHLYTICS_REQUIRE_BUILD_ID_DEFAULT);
    if (!isBuildIdValid(appData.buildId, requiresBuildId)) {
      throw new IllegalStateException(MISSING_BUILD_ID_MSG);
    }

    final String sessionIdentifier = new CLSUUID(idManager).toString();
    try {
      crashMarker = new CrashlyticsFileMarker(CRASH_MARKER_FILE_NAME, fileStore);
      initializationMarker = new CrashlyticsFileMarker(INITIALIZATION_MARKER_FILE_NAME, fileStore);

      final UserMetadata userMetadata =
          new UserMetadata(sessionIdentifier, fileStore, backgroundWorker);
      final LogFileManager logFileManager = new LogFileManager(fileStore);
      final StackTraceTrimmingStrategy stackTraceTrimmingStrategy =
          new MiddleOutFallbackStrategy(
              MAX_STACK_SIZE, new RemoveRepeatsStrategy(NUM_STACK_REPETITIONS_ALLOWED));

      remoteConfigDeferredProxy.setupListener(userMetadata);

      final SessionReportingCoordinator sessionReportingCoordinator =
          SessionReportingCoordinator.create(
              context,
              idManager,
              fileStore,
              appData,
              logFileManager,
              userMetadata,
              stackTraceTrimmingStrategy,
              settingsProvider,
              onDemandCounter,
              sessionsSubscriber);

      controller =
          new CrashlyticsController(
              context,
              backgroundWorker,
              idManager,
              dataCollectionArbiter,
              fileStore,
              crashMarker,
              appData,
              userMetadata,
              logFileManager,
              sessionReportingCoordinator,
              nativeComponent,
              analyticsEventLogger,
              sessionsSubscriber);

      // If the file is present at this point, then the previous run's initialization
      // did not complete, and we want to perform initialization synchronously this time.
      // We make this check early here because we want to guarantee that the async
      // startup thread we're about to launch doesn't affect the value.
      final boolean initializeSynchronously = didPreviousInitializationFail();

      checkForPreviousCrash();

      controller.enableExceptionHandling(
          sessionIdentifier, Thread.getDefaultUncaughtExceptionHandler(), settingsProvider);

      if (initializeSynchronously && CommonUtils.canTryConnection(context)) {
        Logger.getLogger()
            .d(
                "Crashlytics did not finish previous background "
                    + "initialization. Initializing synchronously.");
        // finishInitSynchronously blocks the UI thread while it finishes background init.
        finishInitSynchronously(settingsProvider);
        // Returning false here to stop the rest of init from being run in the background thread.
        return false;
      }
    } catch (Exception e) {
      Logger.getLogger()
          .e("Crashlytics was not started due to an exception during initialization", e);
      controller = null;
      return false;
    }

    Logger.getLogger().d("Successfully configured exception handler.");
    return true;
  }

  /** Performs background initialization asynchronously on the background worker's thread. */
  @CanIgnoreReturnValue
  public Task<Void> doBackgroundInitializationAsync(SettingsProvider settingsProvider) {
    return Utils.callTask(
        crashHandlerExecutor,
        new Callable<Task<Void>>() {
          @Override
          public Task<Void> call() throws Exception {
            return doBackgroundInitialization(settingsProvider);
          }
        });
  }

  /** Performs background initialization synchronously on the calling thread. */
  @CanIgnoreReturnValue
  private Task<Void> doBackgroundInitialization(SettingsProvider settingsProvider) {
    // create the marker for this run
    markInitializationStarted();

    try {
      breadcrumbSource.registerBreadcrumbHandler(this::log);

      controller.saveVersionControlInfo();

      final Settings settingsData = settingsProvider.getSettingsSync();

      if (!settingsData.featureFlagData.collectReports) {
        Logger.getLogger().d("Collection of crash reports disabled in Crashlytics settings.");
        // TODO: This isn't actually an error condition, so figure out the right way to
        // handle this case.
        return Tasks.forException(
            new RuntimeException("Collection of crash reports disabled in Crashlytics settings."));
      }

      if (!controller.finalizeSessions(settingsProvider)) {
        Logger.getLogger().w("Previous sessions could not be finalized.");
      }

      // TODO: Move this call out of this method, so that the return value merely indicates
      // initialization is complete. Callers that want to know when report sending is complete can
      // handle that as a separate call.
      return controller.submitAllReports(settingsProvider.getSettingsAsync());
    } catch (Exception e) {
      Logger.getLogger()
          .e("Crashlytics encountered a problem during asynchronous initialization.", e);
      return Tasks.forException(e);
    } finally {
      // The only thing that compels us to leave the marker and start synchronously next time
      // is not executing all the way through this method. That would indicate that we perhaps
      // didn't get our settings and have a chance to send reports. This situation is usually
      // caused by the Main thread crashing shortly after the synchronous portion of our
      // start-up completes.
      //
      // Internal exceptions on start-up or other problems aren't likely to be fixed by
      // starting synchronously next time, so don't bother slowing down the host app for that.
      markInitializationComplete();
    }
  }

  // endregion

  public void setCrashlyticsCollectionEnabled(@Nullable Boolean enabled) {
    dataCollectionArbiter.setCrashlyticsDataCollectionEnabled(enabled);
  }

  // region Unsent report management.

  @NonNull
  public Task<Boolean> checkForUnsentReports() {
    return controller.checkForUnsentReports();
  }

  public Task<Void> sendUnsentReports() {
    return controller.sendUnsentReports();
  }

  public Task<Void> deleteUnsentReports() {
    return controller.deleteUnsentReports();
  }

  // endregion

  // region Crashlytics version

  public static String getVersion() {
    return BuildConfig.VERSION_NAME;
  }

  // endregion

  // region Public API

  /**
   * Logs a non-fatal Throwable on the Crashlytics servers. Crashlytics will analyze the Throwable
   * and create a new issue or add it to an existing issue, as appropriate.
   *
   * <p>To ensure accurate reporting, this method must be invoked from the thread on which the
   * Throwable was thrown. The Throwable will always be processed on a background thread, so it is
   * safe to invoke this method from the main thread.
   */
  public void logException(@NonNull Throwable throwable) {
    controller.writeNonFatalException(Thread.currentThread(), throwable);
  }

  /**
   * Add text logging that will be sent with your next report. This logging will only be visible in
   * your Crashlytics dashboard, and will be associated with the next crash or logged exception for
   * this app execution. Newline characters ('\n') will be stripped from msg.
   *
   * <p>The log is rolling with a maximum size of 64k, such that messages are removed (oldest first)
   * if the max size is exceeded.
   *
   * @see #logException(Throwable)
   */
  public void log(final String msg) {
    final long timestamp = System.currentTimeMillis() - startTime;
    controller.writeToLog(timestamp, msg);
  }

  public void setUserId(String identifier) {
    controller.setUserId(identifier);
  }

  /**
   * Set a value to be associated with a given key for your crash data. The key/value pairs will be
   * reported with any crash that occurs in this session. A maximum of 64 key/value pairs can be
   * stored for any type. New keys added over that limit will be ignored. Keys and values are
   * trimmed ({@link String#trim()}), and keys or values that exceed 1024 characters will be
   * truncated.
   *
   * @throws NullPointerException if key is null.
   */
  public void setCustomKey(String key, String value) {
    controller.setCustomKey(key, value);
  }

  /**
   * Sets multiple values to be associated with given keys for your crash data. This method should
   * be used instead of setCustomKey when many different key/value pairs are to be set at the same
   * time in order to optimize the process of writing out the data. The key/value pairs will be
   * reported with any crash that occurs in this session. A maximum of 64 key/value pairs can be
   * stored for any type. New keys added over that limit will be ignored. If calling this method
   * would exceed the maximum number of keys, some keys will not be added; as there is no intrinsic
   * sorting of keys it is unpredictable which will be logged versus dropped. Keys and values are
   * trimmed ({@link String#trim()}), and keys or values that exceed 1024 characters will be
   * truncated.
   *
   * @throws NullPointerException if any key in keysAndValues is null.
   */
  public void setCustomKeys(Map<String, String> keysAndValues) {
    controller.setCustomKeys(keysAndValues);
  }

  // endregion

  // region Internal API

  /**
   * Set a value to be associated with a given key for your crash data. The key/value pairs will be
   * reported with any crash that occurs in this session. A maximum of 64 key/value pairs can be
   * stored for any type. New keys added over that limit will be ignored. Keys and values are
   * trimmed ({@link String#trim()}), and keys or values that exceed 1024 characters will be
   * truncated.
   *
   * <p>IMPORTANT: This method is accessed via reflection and JNI. Do not change the type without
   * updating the SDKs that depend on it.
   *
   * @throws NullPointerException if key is null.
   */
  public void setInternalKey(String key, String value) {
    controller.setInternalKey(key, value);
  }

  /** Logs a fatal Throwable on the Crashlytics servers on-demand. */
  public void logFatalException(Throwable throwable) {
    Logger.getLogger()
        .d("Recorded on-demand fatal events: " + onDemandCounter.getRecordedOnDemandExceptions());
    Logger.getLogger()
        .d("Dropped on-demand fatal events: " + onDemandCounter.getDroppedOnDemandExceptions());
    controller.setInternalKey(
        ON_DEMAND_RECORDED_KEY, Integer.toString(onDemandCounter.getRecordedOnDemandExceptions()));
    controller.setInternalKey(
        ON_DEMAND_DROPPED_KEY, Integer.toString(onDemandCounter.getDroppedOnDemandExceptions()));
    controller.logFatalException(Thread.currentThread(), throwable);
  }

  // endregion

  // region Package-protected getters

  CrashlyticsController getController() {
    return controller;
  }

  // endregion

  // region Instance utilities

  /**
   * When a startup crash occurs, Crashlytics must lock on the main thread and complete
   * initializaiton to upload crash result. 4 seconds is chosen for the lock to prevent ANR
   */
  private void finishInitSynchronously(SettingsProvider settingsProvider) {

    final Runnable runnable =
        new Runnable() {
          @Override
          public void run() {
            doBackgroundInitialization(settingsProvider);
          }
        };

    final Future<?> future = crashHandlerExecutor.submit(runnable);

    Logger.getLogger()
        .d(
            "Crashlytics detected incomplete initialization on previous app launch."
                + " Will initialize synchronously.");

    try {
      future.get(DEFAULT_MAIN_HANDLER_TIMEOUT_SEC, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Logger.getLogger().e("Crashlytics was interrupted during initialization.", e);
    } catch (ExecutionException e) {
      Logger.getLogger().e("Crashlytics encountered a problem during initialization.", e);
    } catch (TimeoutException e) {
      Logger.getLogger().e("Crashlytics timed out during initialization.", e);
    }
  }

  /** Synchronous call to mark start of initialization */
  void markInitializationStarted() {
    backgroundWorker.checkRunningOnThread();

    // Create the Crashlytics initialization marker file, which is used to determine
    // whether the app crashed before initialization could complete.
    initializationMarker.create();
    Logger.getLogger().v("Initialization marker file was created.");
  }

  /** Enqueues a job to remove the Crashlytics initialization marker file */
  void markInitializationComplete() {
    backgroundWorker.submit(
        new Callable<Boolean>() {
          @Override
          public Boolean call() throws Exception {
            try {
              final boolean removed = initializationMarker.remove();
              if (!removed) {
                Logger.getLogger().w("Initialization marker file was not properly removed.");
              }
              return removed;
            } catch (Exception e) {
              Logger.getLogger()
                  .e("Problem encountered deleting Crashlytics initialization marker.", e);
              return false;
            }
          }
        });
  }

  boolean didPreviousInitializationFail() {
    return initializationMarker.isPresent();
  }

  // region Previous crash handling

  private void checkForPreviousCrash() {
    Task<Boolean> task =
        backgroundWorker.submit(
            new Callable<Boolean>() {
              @Override
              public Boolean call() throws Exception {
                return controller.didCrashOnPreviousExecution();
              }
            });

    Boolean result;
    try {
      result = Utils.awaitEvenIfOnMainThread(task);
    } catch (Exception e) {
      didCrashOnPreviousExecution = false;
      return;
    }

    // It shouldn't be possible for result to be null, but this guards against it anyway.
    didCrashOnPreviousExecution = Boolean.TRUE.equals(result);
  }

  public boolean didCrashOnPreviousExecution() {
    return didCrashOnPreviousExecution;
  }

  // endregion

  // region Static utilities

  static boolean isBuildIdValid(String buildId, boolean requiresBuildId) {
    if (!requiresBuildId) {
      Logger.getLogger().v("Configured not to require a build ID.");
      return true;
    }

    if (!TextUtils.isEmpty(buildId)) {
      return true;
    }

    Log.e(Logger.TAG, ".");
    Log.e(Logger.TAG, ".     |  | ");
    Log.e(Logger.TAG, ".     |  |");
    Log.e(Logger.TAG, ".     |  |");
    Log.e(Logger.TAG, ".   \\ |  | /");
    Log.e(Logger.TAG, ".    \\    /");
    Log.e(Logger.TAG, ".     \\  /");
    Log.e(Logger.TAG, ".      \\/");
    Log.e(Logger.TAG, ".");
    Log.e(Logger.TAG, MISSING_BUILD_ID_MSG);
    Log.e(Logger.TAG, ".");
    Log.e(Logger.TAG, ".      /\\");
    Log.e(Logger.TAG, ".     /  \\");
    Log.e(Logger.TAG, ".    /    \\");
    Log.e(Logger.TAG, ".   / |  | \\");
    Log.e(Logger.TAG, ".     |  |");
    Log.e(Logger.TAG, ".     |  |");
    Log.e(Logger.TAG, ".     |  |");
    Log.e(Logger.TAG, ".");

    return false;
  }

  // endregion
}
