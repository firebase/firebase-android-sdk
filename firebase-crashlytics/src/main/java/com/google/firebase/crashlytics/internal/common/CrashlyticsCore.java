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
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.crashlytics.BuildConfig;
import com.google.firebase.crashlytics.internal.CrashlyticsNativeComponent;
import com.google.firebase.crashlytics.internal.Logger;
import com.google.firebase.crashlytics.internal.analytics.AnalyticsEventLogger;
import com.google.firebase.crashlytics.internal.breadcrumbs.BreadcrumbSource;
import com.google.firebase.crashlytics.internal.network.HttpRequestFactory;
import com.google.firebase.crashlytics.internal.persistence.FileStore;
import com.google.firebase.crashlytics.internal.persistence.FileStoreImpl;
import com.google.firebase.crashlytics.internal.settings.SettingsDataProvider;
import com.google.firebase.crashlytics.internal.settings.model.Settings;
import com.google.firebase.crashlytics.internal.unity.ResourceUnityVersionProvider;
import com.google.firebase.crashlytics.internal.unity.UnityVersionProvider;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@SuppressWarnings("PMD.NullAssignment")
public class CrashlyticsCore {
  private static final String MISSING_BUILD_ID_MSG =
      "The Crashlytics build ID is missing. This "
          + "occurs when Crashlytics tooling is absent from your app's build configuration. "
          + "Please review Crashlytics onboarding instructions and ensure you have a valid "
          + "Crashlytics account.";

  private static final float CLS_DEFAULT_PROCESS_DELAY = 1.0f;

  // Build ID related constants
  static final String CRASHLYTICS_REQUIRE_BUILD_ID = "com.crashlytics.RequireBuildId";
  static final boolean CRASHLYTICS_REQUIRE_BUILD_ID_DEFAULT = true;

  static final int DEFAULT_MAIN_HANDLER_TIMEOUT_SEC = 4;

  // If this marker sticks around, the app is crashing before we finished initializing
  private static final String INITIALIZATION_MARKER_FILE_NAME = "initialization_marker";
  static final String CRASH_MARKER_FILE_NAME = "crash_marker";

  private final Context context;
  private final FirebaseApp app;
  private final DataCollectionArbiter dataCollectionArbiter;

  private final long startTime;

  private CrashlyticsFileMarker initializationMarker;
  private CrashlyticsFileMarker crashMarker;
  private boolean didCrashOnPreviousExecution;

  private CrashlyticsController controller;

  private final IdManager idManager;
  private final BreadcrumbSource breadcrumbSource;
  private final AnalyticsEventLogger analyticsEventLogger;
  private ExecutorService crashHandlerExecutor;
  private CrashlyticsBackgroundWorker backgroundWorker;

  private CrashlyticsNativeComponent nativeComponent;

  // region Constructors

  public CrashlyticsCore(
      FirebaseApp app,
      IdManager idManager,
      CrashlyticsNativeComponent nativeComponent,
      DataCollectionArbiter dataCollectionArbiter,
      BreadcrumbSource breadcrumbSource,
      AnalyticsEventLogger analyticsEventLogger,
      ExecutorService crashHandlerExecutor) {
    this.app = app;
    this.dataCollectionArbiter = dataCollectionArbiter;
    this.context = app.getApplicationContext();
    this.idManager = idManager;
    this.nativeComponent = nativeComponent;
    this.breadcrumbSource = breadcrumbSource;
    this.analyticsEventLogger = analyticsEventLogger;
    this.crashHandlerExecutor = crashHandlerExecutor;
    this.backgroundWorker = new CrashlyticsBackgroundWorker(crashHandlerExecutor);

    startTime = System.currentTimeMillis();
  }

  // endregion

  // region Initialization

  public boolean onPreExecute(SettingsDataProvider settingsProvider) {
    // before starting the crash detector make sure that this was built with our build
    // tools.
    final String mappingFileId = CommonUtils.getMappingFileId(context);
    Logger.getLogger().d("Mapping file ID is: " + mappingFileId);

    // Throw an exception and halt the app if the build ID is required and not present.
    // TODO: This flag is no longer supported and should be removed, as part of a larger refactor
    //  now that the buildId is now only used for mapping file association.
    final boolean requiresBuildId =
        CommonUtils.getBooleanResourceValue(
            context, CRASHLYTICS_REQUIRE_BUILD_ID, CRASHLYTICS_REQUIRE_BUILD_ID_DEFAULT);
    if (!isBuildIdValid(mappingFileId, requiresBuildId)) {
      throw new IllegalStateException(MISSING_BUILD_ID_MSG);
    }

    final String googleAppId = app.getOptions().getApplicationId();

    try {
      Logger.getLogger().i("Initializing Crashlytics " + getVersion());

      final FileStore fileStore = new FileStoreImpl(context);
      crashMarker = new CrashlyticsFileMarker(CRASH_MARKER_FILE_NAME, fileStore);
      initializationMarker = new CrashlyticsFileMarker(INITIALIZATION_MARKER_FILE_NAME, fileStore);

      final HttpRequestFactory httpRequestFactory = new HttpRequestFactory();

      final UnityVersionProvider unityVersionProvider = new ResourceUnityVersionProvider(context);
      final AppData appData =
          AppData.create(context, idManager, googleAppId, mappingFileId, unityVersionProvider);

      Logger.getLogger().d("Installer package name is: " + appData.installerPackageName);

      controller =
          new CrashlyticsController(
              context,
              backgroundWorker,
              httpRequestFactory,
              idManager,
              dataCollectionArbiter,
              fileStore,
              crashMarker,
              appData,
              null,
              null,
              nativeComponent,
              analyticsEventLogger,
              settingsProvider);

      // If the file is present at this point, then the previous run's initialization
      // did not complete, and we want to perform initialization synchronously this time.
      // We make this check early here because we want to guarantee that the async
      // startup thread we're about to launch doesn't affect the value.
      final boolean initializeSynchronously = didPreviousInitializationFail();

      checkForPreviousCrash();

      controller.enableExceptionHandling(
          Thread.getDefaultUncaughtExceptionHandler(), settingsProvider);

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

    Logger.getLogger().d("Exception handling initialization successful");
    return true;
  }

  /** Performs background initialization asynchronously on the background worker's thread. */
  public Task<Void> doBackgroundInitializationAsync(SettingsDataProvider settingsProvider) {
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
  private Task<Void> doBackgroundInitialization(SettingsDataProvider settingsProvider) {
    // create the marker for this run
    markInitializationStarted();

    controller.cleanInvalidTempFiles();

    try {
      breadcrumbSource.registerBreadcrumbHandler(this::log);

      final Settings settingsData = settingsProvider.getSettings();

      if (!settingsData.getFeaturesData().collectReports) {
        Logger.getLogger().d("Collection of crash reports disabled in Crashlytics settings.");
        // TODO: This isn't actually an error condition, so figure out the right way to
        // handle this case.
        return Tasks.forException(
            new RuntimeException("Collection of crash reports disabled in Crashlytics settings."));
      }

      if (!controller.finalizeSessions(settingsData.getSessionData().maxCustomExceptionEvents)) {
        Logger.getLogger().d("Could not finalize previous sessions.");
      }

      // TODO: Move this call out of this method, so that the return value merely indicates
      // initialization is complete. Callers that want to know when report sending is complete can
      // handle that as a separate call.
      return controller.submitAllReports(
          CLS_DEFAULT_PROCESS_DELAY, settingsProvider.getAppSettings());
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
  private void finishInitSynchronously(SettingsDataProvider settingsDataProvider) {

    final Runnable runnable =
        new Runnable() {
          @Override
          public void run() {
            doBackgroundInitialization(settingsDataProvider);
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
      Logger.getLogger().e("Problem encountered during Crashlytics initialization.", e);
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
    Logger.getLogger().d("Initialization marker file created.");
  }

  /** Enqueues a job to remove the Crashlytics initialization marker file */
  void markInitializationComplete() {
    backgroundWorker.submit(
        new Callable<Boolean>() {
          @Override
          public Boolean call() throws Exception {
            try {
              final boolean removed = initializationMarker.remove();
              Logger.getLogger().d("Initialization marker file removed: " + removed);
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
      Logger.getLogger().d("Configured not to require a build ID.");
      return true;
    }

    if (!CommonUtils.isNullOrEmpty(buildId)) {
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
