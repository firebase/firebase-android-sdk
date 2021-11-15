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

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.tasks.SuccessContinuation;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.crashlytics.internal.CrashlyticsNativeComponent;
import com.google.firebase.crashlytics.internal.Logger;
import com.google.firebase.crashlytics.internal.NativeSessionFileProvider;
import com.google.firebase.crashlytics.internal.analytics.AnalyticsEventLogger;
import com.google.firebase.crashlytics.internal.log.LogFileManager;
import com.google.firebase.crashlytics.internal.model.StaticSessionData;
import com.google.firebase.crashlytics.internal.persistence.FileStore;
import com.google.firebase.crashlytics.internal.settings.SettingsDataProvider;
import com.google.firebase.crashlytics.internal.settings.model.AppSettingsData;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedSet;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

class CrashlyticsController {

  static final String FIREBASE_CRASH_TYPE = "fatal";
  static final String FIREBASE_TIMESTAMP = "timestamp";
  static final String FIREBASE_APPLICATION_EXCEPTION = "_ae";
  static final String APP_EXCEPTION_MARKER_PREFIX = ".ae";

  static final FilenameFilter APP_EXCEPTION_MARKER_FILTER =
      (directory, filename) -> filename.startsWith(APP_EXCEPTION_MARKER_PREFIX);

  static final String NATIVE_SESSION_DIR = "native-sessions";

  static final int FIREBASE_CRASH_TYPE_FATAL = 1;

  private static final String GENERATOR_FORMAT = "Crashlytics Android SDK/%s";

  private final Context context;
  private final DataCollectionArbiter dataCollectionArbiter;
  private final CrashlyticsFileMarker crashMarker;
  private final UserMetadata userMetadata;

  private final CrashlyticsBackgroundWorker backgroundWorker;

  private final IdManager idManager;
  private final FileStore fileStore;

  private final AppData appData;

  private final LogFileManager logFileManager;
  private final CrashlyticsNativeComponent nativeComponent;
  private final AnalyticsEventLogger analyticsEventLogger;
  private final SessionReportingCoordinator reportingCoordinator;

  private CrashlyticsUncaughtExceptionHandler crashHandler;

  // A promise that will be resolved when unsent reports are found on the device, and
  // send/deleteUnsentReports can be called to decide how to deal with them.
  final TaskCompletionSource<Boolean> unsentReportsAvailable = new TaskCompletionSource<>();

  // A promise that will be resolved when the user has provided an action that they want to perform
  // for all the unsent reports.
  final TaskCompletionSource<Boolean> reportActionProvided = new TaskCompletionSource<>();

  // A promise that will be resolved when all unsent reports have been "handled". They won't
  // necessarily have been uploaded, but we will know whether they should be sent or deleted, and
  // the initial work to make that happen will have been processed on the work queue.
  final TaskCompletionSource<Void> unsentReportsHandled = new TaskCompletionSource<>();

  // A token to make sure that checkForUnsentReports only gets called once.
  final AtomicBoolean checkForUnsentReportsCalled = new AtomicBoolean(false);

  CrashlyticsController(
      Context context,
      CrashlyticsBackgroundWorker backgroundWorker,
      IdManager idManager,
      DataCollectionArbiter dataCollectionArbiter,
      FileStore fileStore,
      CrashlyticsFileMarker crashMarker,
      AppData appData,
      UserMetadata userMetadata,
      LogFileManager logFileManager,
      SessionReportingCoordinator sessionReportingCoordinator,
      CrashlyticsNativeComponent nativeComponent,
      AnalyticsEventLogger analyticsEventLogger) {
    this.context = context;
    this.backgroundWorker = backgroundWorker;
    this.idManager = idManager;
    this.dataCollectionArbiter = dataCollectionArbiter;
    this.fileStore = fileStore;
    this.crashMarker = crashMarker;
    this.appData = appData;
    this.userMetadata = userMetadata;
    this.logFileManager = logFileManager;
    this.nativeComponent = nativeComponent;
    this.analyticsEventLogger = analyticsEventLogger;

    this.reportingCoordinator = sessionReportingCoordinator;
  }

  private Context getContext() {
    return context;
  }

  // region Exception handling

  void enableExceptionHandling(
      Thread.UncaughtExceptionHandler defaultHandler, SettingsDataProvider settingsProvider) {
    // This must be called before installing the controller with
    // Thread.setDefaultUncaughtExceptionHandler to ensure that we are ready to handle
    // any crashes we catch.
    openSession();
    final CrashlyticsUncaughtExceptionHandler.CrashListener crashListener =
        new CrashlyticsUncaughtExceptionHandler.CrashListener() {
          @Override
          public void onUncaughtException(
              @NonNull SettingsDataProvider settingsDataProvider,
              @NonNull Thread thread,
              @NonNull Throwable ex) {
            handleUncaughtException(settingsDataProvider, thread, ex);
          }
        };
    crashHandler =
        new CrashlyticsUncaughtExceptionHandler(
            crashListener, settingsProvider, defaultHandler, nativeComponent);
    Thread.setDefaultUncaughtExceptionHandler(crashHandler);
  }

  synchronized void handleUncaughtException(
      @NonNull SettingsDataProvider settingsDataProvider,
      @NonNull final Thread thread,
      @NonNull final Throwable ex) {

    Logger.getLogger()
        .d("Handling uncaught " + "exception \"" + ex + "\" from thread " + thread.getName());

    // Capture the time that the crash occurs and close over it so that the time doesn't
    // reflect when we get around to executing the task later.
    final long timestampMillis = System.currentTimeMillis();

    final Task<Void> handleUncaughtExceptionTask =
        backgroundWorker.submitTask(
            new Callable<Task<Void>>() {
              @Override
              public Task<Void> call() throws Exception {
                final long timestampSeconds = getTimestampSeconds(timestampMillis);

                final String currentSessionId = getCurrentSessionId();
                if (currentSessionId == null) {
                  Logger.getLogger()
                      .e("Tried to write a fatal exception while no session was open.");
                  return Tasks.forResult(null);
                }

                // We've fatally crashed, so write the marker file that indicates a crash occurred.
                crashMarker.create();

                reportingCoordinator.persistFatalEvent(
                    ex, thread, currentSessionId, timestampSeconds);

                doWriteAppExceptionMarker(timestampMillis);
                doCloseSessions(settingsDataProvider);
                doOpenSession();

                // If automatic data collection is disabled, we'll need to wait until the next run
                // of the app.
                if (!dataCollectionArbiter.isAutomaticDataCollectionEnabled()) {
                  return Tasks.forResult(null);
                }

                Executor executor = backgroundWorker.getExecutor();

                return settingsDataProvider
                    .getAppSettings()
                    .onSuccessTask(
                        executor,
                        new SuccessContinuation<AppSettingsData, Void>() {
                          @NonNull
                          @Override
                          public Task<Void> then(@Nullable AppSettingsData appSettingsData)
                              throws Exception {
                            if (appSettingsData == null) {
                              Logger.getLogger()
                                  .w(
                                      "Received null app settings, cannot send reports at crash time.");
                              return Tasks.forResult(null);
                            }
                            // Data collection is enabled, so it's safe to send the report.
                            return Tasks.whenAll(
                                logAnalyticsAppExceptionEvents(),
                                reportingCoordinator.sendReports(executor));
                          }
                        });
              }
            });

    try {
      Utils.awaitEvenIfOnMainThread(handleUncaughtExceptionTask);
    } catch (Exception e) {
      Logger.getLogger().e("Error handling uncaught exception", e);
      // Nothing to do in this case.
    }
  }

  // endregion

  // This method returns a promise that is resolved with a wrapped action once the user has
  // indicated whether they want to upload currently cached reports.
  // 1. If data collection is enabled, this method immediately calls the block with Send.
  // 2. Otherwise, this method waits until either:
  //    a. Data collection becomes enabled, in which case, the promise will be resolved with Send.
  //    b. The developer uses the send/deleteUnsentReports API to indicate whether the report
  //       should be sent or deleted, at which point the promise will be resolved with the action.
  private Task<Boolean> waitForReportAction() {
    if (dataCollectionArbiter.isAutomaticDataCollectionEnabled()) {
      Logger.getLogger().d("Automatic data collection is enabled. Allowing upload.");
      unsentReportsAvailable.trySetResult(false);
      return Tasks.forResult(true);
    }

    Logger.getLogger().d("Automatic data collection is disabled.");
    Logger.getLogger().v("Notifying that unsent reports are available.");
    unsentReportsAvailable.trySetResult(true);

    // If data collection gets enabled while we are waiting for an action, go ahead and send the
    // reports, and any subsequent explicit response will be ignored.
    final Task<Boolean> collectionEnabled =
        dataCollectionArbiter
            .waitForAutomaticDataCollectionEnabled()
            .onSuccessTask(
                new SuccessContinuation<Void, Boolean>() {
                  @NonNull
                  @Override
                  public Task<Boolean> then(@Nullable Void aVoid) throws Exception {
                    return Tasks.forResult(true);
                  }
                });

    Logger.getLogger().d("Waiting for send/deleteUnsentReports to be called.");
    // Wait for either the processReports callback to be called, or data collection to be enabled.
    return Utils.race(collectionEnabled, reportActionProvided.getTask());
  }

  /** This function must be called before opening the first session * */
  boolean didCrashOnPreviousExecution() {
    if (!crashMarker.isPresent()) {
      // Before the first session of this execution is opened, the current session ID still refers
      // to the previous execution's last session, which is what we want.
      final String sessionId = getCurrentSessionId();
      return sessionId != null && nativeComponent.hasCrashDataForSession(sessionId);
    }

    Logger.getLogger().v("Found previous crash marker.");
    crashMarker.remove();

    return Boolean.TRUE;
  }

  @NonNull
  Task<Boolean> checkForUnsentReports() {
    // Make sure checkForUnsentReports only gets called once. It really doesn't make sense to call
    // it multiple times, since reports only get packaged up at two possible times: 1) at start-up,
    // and 2) when there's a fatal crash. So no new reports will become available while the app is
    // running.
    if (!checkForUnsentReportsCalled.compareAndSet(false, true)) {
      Logger.getLogger().w("checkForUnsentReports should only be called once per execution.");
      return Tasks.forResult(false);
    }
    return unsentReportsAvailable.getTask();
  }

  Task<Void> sendUnsentReports() {
    reportActionProvided.trySetResult(true);
    return unsentReportsHandled.getTask();
  }

  Task<Void> deleteUnsentReports() {
    reportActionProvided.trySetResult(false);
    return unsentReportsHandled.getTask();
  }

  Task<Void> submitAllReports(Task<AppSettingsData> appSettingsDataTask) {
    if (!reportingCoordinator.hasReportsToSend()) {
      // Just notify the user that there are no reports and stop.
      Logger.getLogger().v("No crash reports are available to be sent.");
      unsentReportsAvailable.trySetResult(false);
      return Tasks.forResult(null);
    }
    Logger.getLogger().v("Crash reports are available to be sent.");

    return waitForReportAction()
        .onSuccessTask(
            new SuccessContinuation<Boolean, Void>() {
              @NonNull
              @Override
              public Task<Void> then(@Nullable Boolean send) throws Exception {

                return backgroundWorker.submitTask(
                    new Callable<Task<Void>>() {
                      @Override
                      public Task<Void> call() throws Exception {
                        if (!send) {
                          Logger.getLogger().v("Deleting cached crash reports...");
                          deleteFiles(listAppExceptionMarkerFiles());
                          reportingCoordinator.removeAllReports();
                          unsentReportsHandled.trySetResult(null);
                          return Tasks.forResult(null);
                        }

                        Logger.getLogger().d("Sending cached crash reports...");

                        // waitForReportAction guarantees we got user permission.
                        boolean dataCollectionToken = send;

                        // Signal to the settings fetch and onboarding that we have explicit
                        // permission.
                        dataCollectionArbiter.grantDataCollectionPermission(dataCollectionToken);

                        Executor executor = backgroundWorker.getExecutor();

                        return appSettingsDataTask.onSuccessTask(
                            executor,
                            new SuccessContinuation<AppSettingsData, Void>() {
                              @NonNull
                              @Override
                              public Task<Void> then(@Nullable AppSettingsData appSettingsData)
                                  throws Exception {
                                if (appSettingsData == null) {
                                  Logger.getLogger()
                                      .w(
                                          "Received null app settings at app startup. Cannot send cached reports");
                                  return Tasks.forResult(null);
                                }
                                logAnalyticsAppExceptionEvents();
                                reportingCoordinator.sendReports(executor);
                                unsentReportsHandled.trySetResult(null);

                                return Tasks.forResult(null);
                              }
                            });
                      }
                    });
              }
            });
  }

  // region Internal "public" API for data capture

  /** Log a timestamped string to the log file. */
  void writeToLog(final long timestamp, final String msg) {
    backgroundWorker.submit(
        new Callable<Void>() {
          @Override
          public Void call() throws Exception {
            if (!isHandlingException()) {
              logFileManager.writeToLog(timestamp, msg);
            }
            return null;
          }
        });
  }

  /** Log a caught exception - write out Throwable as event section of protobuf */
  void writeNonFatalException(@NonNull final Thread thread, @NonNull final Throwable ex) {
    // Capture and close over the current time, so that we get the exact call time,
    // rather than the time at which the task executes.
    final long timestampMillis = System.currentTimeMillis();

    backgroundWorker.submit(
        new Runnable() {
          @Override
          public void run() {
            if (!isHandlingException()) {
              long timestampSeconds = getTimestampSeconds(timestampMillis);
              final String currentSessionId = getCurrentSessionId();
              if (currentSessionId == null) {
                Logger.getLogger()
                    .w("Tried to write a non-fatal exception while no session was open.");
                return;
              }
              reportingCoordinator.persistNonFatalEvent(
                  ex, thread, currentSessionId, timestampSeconds);
            }
          }
        });
  }

  void setUserId(String identifier) {
    userMetadata.setUserId(identifier);
    cacheUserData(userMetadata);
  }

  void setCustomKey(String key, String value) {
    try {
      if (userMetadata.setCustomKey(key, value)) {
        cacheKeyData(userMetadata.getCustomKeys(), false);
      }
    } catch (IllegalArgumentException ex) {
      if (context != null && CommonUtils.isAppDebuggable(context)) {
        throw ex;
      } else {
        Logger.getLogger().e("Attempting to set custom attribute with null key, ignoring.");
        return;
      }
    }
  }

  void setCustomKeys(Map<String, String> keysAndValues) {
    // Write all the key/value pairs before doing anything computationally expensive.
    userMetadata.setCustomKeys(keysAndValues);
    // Once all the key/value pairs are added, update the cache.
    cacheKeyData(userMetadata.getCustomKeys(), false);
  }

  void setInternalKey(String key, String value) {
    try {
      if (userMetadata.setInternalKey(key, value)) {
        cacheKeyData(userMetadata.getInternalKeys(), true);
      }
    } catch (IllegalArgumentException ex) {
      if (context != null && CommonUtils.isAppDebuggable(context)) {
        throw ex;
      } else {
        Logger.getLogger().e("Attempting to set custom attribute with null key, ignoring.");
        return;
      }
    }
  }

  /**
   * Cache user metadata asynchronously in case of a non-graceful process exit. Can be reloaded and
   * sent with the previous crash data on app restart. NOTE: Because this is asynchronous, it is
   * performant in critical code paths, but susceptible to losing data if a crash happens
   * immediately after setting a value. If this becomes a problem, we can investigate writing
   * synchronously, or potentially add an explicit user-facing API for synchronous writes.
   */
  private void cacheUserData(final UserMetadata userMetaData) {
    backgroundWorker.submit(
        new Callable<Void>() {
          @Override
          public Void call() throws Exception {
            final String currentSessionId = getCurrentSessionId();
            if (currentSessionId == null) {
              Logger.getLogger().d("Tried to cache user data while no session was open.");
              return null;
            }
            reportingCoordinator.persistUserId(currentSessionId);
            new MetaDataStore(fileStore).writeUserData(currentSessionId, userMetaData);
            return null;
          }
        });
  }

  /**
   * Cache custom key metadata asynchronously in case of a non-graceful process exit. Can be
   * reloaded and sent with the previous crash data on app restart. NOTE: Because this is
   * asynchronous, it is performant in critical code paths, but susceptible to losing data if a
   * crash happens immediately after setting a value. If this becomes a problem, we can investigate
   * writing synchronously, or potentially add an explicit user-facing API for synchronous writes.
   */
  private void cacheKeyData(final Map<String, String> keyData, boolean isInternal) {
    backgroundWorker.submit(
        new Callable<Void>() {
          @Override
          public Void call() throws Exception {
            final String currentSessionId = getCurrentSessionId();
            new MetaDataStore(fileStore).writeKeyData(currentSessionId, keyData, isInternal);
            return null;
          }
        });
  }

  // endregion

  // region Session Management

  /** Open a new session on the single-threaded executor. */
  void openSession() {
    backgroundWorker.submit(
        new Callable<Void>() {
          @Override
          public Void call() throws Exception {
            doOpenSession();
            return null;
          }
        });
  }

  /**
   * Not synchronized/locked. Must be executed from the single thread executor service used by this
   * class.
   *
   * <p>May return <code>null</code> if there is no open session.
   */
  @Nullable
  private String getCurrentSessionId() {
    final SortedSet<String> sortedOpenSessions = reportingCoordinator.listSortedOpenSessionIds();
    return (!sortedOpenSessions.isEmpty()) ? sortedOpenSessions.first() : null;
  }

  /**
   * Closes any previously open sessions
   *
   * <p>return false if sessions could not be closed due to current exception handling
   *
   * <p>This method can not be called while the {@link CrashlyticsCore} settings lock is held. It
   * will result in a deadlock!
   *
   * @param settingsDataProvider
   */
  boolean finalizeSessions(SettingsDataProvider settingsDataProvider) {
    backgroundWorker.checkRunningOnThread();

    if (isHandlingException()) {
      Logger.getLogger().w("Skipping session finalization because a crash has already occurred.");
      return Boolean.FALSE;
    }

    Logger.getLogger().v("Finalizing previously open sessions.");
    try {
      doCloseSessions(true, settingsDataProvider);
    } catch (Exception e) {
      Logger.getLogger().e("Unable to finalize previously open sessions.", e);
      return false;
    }
    Logger.getLogger().v("Closed all previously open sessions.");

    return true;
  }

  /**
   * Not synchronized/locked. Must be executed from the single thread executor service used by this
   * class.
   */
  private void doOpenSession() {
    final long startedAtSeconds = getCurrentTimestampSeconds();
    final String sessionIdentifier = new CLSUUID(idManager).toString();

    Logger.getLogger().d("Opening a new session with ID " + sessionIdentifier);

    final String generator =
        String.format(Locale.US, GENERATOR_FORMAT, CrashlyticsCore.getVersion());

    StaticSessionData.AppData appData = createAppData(idManager, this.appData);
    StaticSessionData.OsData osData = createOsData(getContext());
    StaticSessionData.DeviceData deviceData = createDeviceData(getContext());

    nativeComponent.prepareNativeSession(
        sessionIdentifier,
        generator,
        startedAtSeconds,
        StaticSessionData.create(appData, osData, deviceData));

    logFileManager.setCurrentSession(sessionIdentifier);
    reportingCoordinator.onBeginSession(sessionIdentifier, startedAtSeconds);
  }

  void doCloseSessions(SettingsDataProvider settingsDataProvider) {
    doCloseSessions(false, settingsDataProvider);
  }

  /**
   * Not synchronized/locked. Must be executed from the single thread executor service used by this
   * class.
   */
  private void doCloseSessions(
      boolean skipCurrentSession, SettingsDataProvider settingsDataProvider) {
    final int offset = skipCurrentSession ? 1 : 0;

    // :TODO HW2021 this implementation can be cleaned up.
    List<String> sortedOpenSessions =
        new ArrayList<>(reportingCoordinator.listSortedOpenSessionIds());

    if (sortedOpenSessions.size() <= offset) {
      Logger.getLogger().v("No open sessions to be closed.");
      return;
    }

    final String mostRecentSessionIdToClose = sortedOpenSessions.get(offset);

    if (settingsDataProvider.getSettings().getFeaturesData().collectAnrs) {
      writeApplicationExitInfoEventIfRelevant(mostRecentSessionIdToClose);
    } else {
      Logger.getLogger().v("ANR feature disabled.");
    }

    if (nativeComponent.hasCrashDataForSession(mostRecentSessionIdToClose)) {
      // We only finalize the current session if it's a Java crash, so only finalize native crash
      // data when we aren't including current.
      finalizePreviousNativeSession(mostRecentSessionIdToClose);
    }

    String currentSessionId = null;
    if (skipCurrentSession) {
      currentSessionId = sortedOpenSessions.get(0);
    }

    reportingCoordinator.finalizeSessions(getCurrentTimestampSeconds(), currentSessionId);
  }

  // endregion

  List<File> listAppExceptionMarkerFiles() {
    return fileStore.getCommonFiles(APP_EXCEPTION_MARKER_FILTER);
  }

  private void finalizePreviousNativeSession(String previousSessionId) {
    Logger.getLogger().v("Finalizing native report for session " + previousSessionId);
    NativeSessionFileProvider nativeSessionFileProvider =
        nativeComponent.getSessionFileProvider(previousSessionId);
    File minidumpFile = nativeSessionFileProvider.getMinidumpFile();
    if (minidumpFile == null || !minidumpFile.exists()) {
      Logger.getLogger().w("No minidump data found for session " + previousSessionId);
      return;
    }
    // Because we don't want to read the minidump to get its timestamp, just use file creation time.
    final long eventTime = minidumpFile.lastModified();

    final LogFileManager previousSessionLogManager =
        new LogFileManager(fileStore, previousSessionId);
    final File nativeSessionDirectory = fileStore.getNativeSessionDir(previousSessionId);

    if (!nativeSessionDirectory.isDirectory()) {
      Logger.getLogger().w("Couldn't create directory to store native session files, aborting.");
      return;
    }

    doWriteAppExceptionMarker(eventTime);
    List<NativeSessionFile> nativeSessionFiles =
        getNativeSessionFiles(
            nativeSessionFileProvider,
            previousSessionId,
            fileStore,
            previousSessionLogManager.getBytesForLog());
    NativeSessionFileGzipper.processNativeSessions(nativeSessionDirectory, nativeSessionFiles);

    Logger.getLogger().d("CrashlyticsController#finalizePreviousNativeSession");

    reportingCoordinator.finalizeSessionWithNativeEvent(previousSessionId, nativeSessionFiles);
    previousSessionLogManager.clearLog();
  }

  private static long getCurrentTimestampSeconds() {
    return getTimestampSeconds(System.currentTimeMillis());
  }

  private static long getTimestampSeconds(long timestampMillis) {
    return timestampMillis / 1000;
  }

  // region Serialization to protobuf

  private void doWriteAppExceptionMarker(long eventTime) {
    try {
      if (!fileStore.getCommonFile(APP_EXCEPTION_MARKER_PREFIX + eventTime).createNewFile()) {
        throw new IOException("Create new file failed.");
      }
    } catch (IOException e) {
      Logger.getLogger().w("Could not create app exception marker file.", e);
    }
  }

  private static StaticSessionData.AppData createAppData(IdManager idManager, AppData appData) {
    return StaticSessionData.AppData.create(
        idManager.getAppIdentifier(),
        appData.versionCode,
        appData.versionName,
        idManager.getCrashlyticsInstallId(),
        DeliveryMechanism.determineFrom(appData.installerPackageName).getId(),
        appData.developmentPlatform,
        appData.developmentPlatformVersion);
  }

  private static StaticSessionData.OsData createOsData(Context context) {
    return StaticSessionData.OsData.create(
        VERSION.RELEASE, VERSION.CODENAME, CommonUtils.isRooted(context));
  }

  private static StaticSessionData.DeviceData createDeviceData(Context context) {
    final StatFs statFs = new StatFs(Environment.getDataDirectory().getPath());
    final long diskSpace = (long) statFs.getBlockCount() * (long) statFs.getBlockSize();

    return StaticSessionData.DeviceData.create(
        CommonUtils.getCpuArchitectureInt(),
        Build.MODEL,
        Runtime.getRuntime().availableProcessors(),
        CommonUtils.getTotalRamInBytes(),
        diskSpace,
        CommonUtils.isEmulator(context),
        CommonUtils.getDeviceState(context),
        Build.MANUFACTURER,
        Build.PRODUCT);
  }

  // endregion

  // region Utilities

  UserMetadata getUserMetadata() {
    return userMetadata;
  }

  boolean isHandlingException() {
    return crashHandler != null && crashHandler.isHandlingException();
  }

  FileStore getFileStore() {
    return fileStore;
  }

  /**
   * Send App Exception events to Firebase Analytics. FA records the event asynchronously, so this
   * method returns a Task in case the caller wants to verify that the event was recorded by FA and
   * will not be lost.
   */
  private Task<Void> logAnalyticsAppExceptionEvents() {
    List<Task<Void>> events = new ArrayList<>();

    List<File> appExceptionMarkers = listAppExceptionMarkerFiles();
    for (File markerFile : appExceptionMarkers) {
      try {
        final long timestamp =
            Long.parseLong(markerFile.getName().substring(APP_EXCEPTION_MARKER_PREFIX.length()));
        events.add(logAnalyticsAppExceptionEvent(timestamp));
      } catch (NumberFormatException nfe) {
        Logger.getLogger()
            .w("Could not parse app exception timestamp from file " + markerFile.getName());
      }
      markerFile.delete();
    }

    return Tasks.whenAll(events);
  }

  private Task<Void> logAnalyticsAppExceptionEvent(long timestamp) {
    if (firebaseCrashExists()) {
      Logger.getLogger().w("Skipping logging Crashlytics event to Firebase, FirebaseCrash exists");
      return Tasks.forResult(null);
    }
    Logger.getLogger().d("Logging app exception event to Firebase Analytics");
    final ThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
    return Tasks.call(
        executor,
        new Callable<Void>() {
          @Override
          public Void call() throws Exception {
            final Bundle params = new Bundle();
            params.putInt(FIREBASE_CRASH_TYPE, FIREBASE_CRASH_TYPE_FATAL);
            params.putLong(FIREBASE_TIMESTAMP, timestamp);

            analyticsEventLogger.logEvent(FIREBASE_APPLICATION_EXCEPTION, params);

            return null;
          }
        });
  }

  private static void deleteFiles(List<File> files) {
    for (File file : files) {
      file.delete();
    }
  }

  private static boolean firebaseCrashExists() {
    try {
      final Class clazz = Class.forName("com.google.firebase.crash.FirebaseCrash");
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  @NonNull
  static List<NativeSessionFile> getNativeSessionFiles(
      NativeSessionFileProvider fileProvider,
      String previousSessionId,
      FileStore fileStore,
      byte[] logBytes) {

    final MetaDataStore metaDataStore = new MetaDataStore(fileStore);
    final File userFile = metaDataStore.getUserDataFileForSession(previousSessionId);
    final File keysFile = metaDataStore.getKeysFileForSession(previousSessionId);

    List<NativeSessionFile> nativeSessionFiles = new ArrayList<>();
    nativeSessionFiles.add(new BytesBackedNativeSessionFile("logs_file", "logs", logBytes));
    nativeSessionFiles.add(
        new FileBackedNativeSessionFile(
            "crash_meta_file", "metadata", fileProvider.getMetadataFile()));
    nativeSessionFiles.add(
        new FileBackedNativeSessionFile(
            "session_meta_file", "session", fileProvider.getSessionFile()));
    nativeSessionFiles.add(
        new FileBackedNativeSessionFile("app_meta_file", "app", fileProvider.getAppFile()));
    nativeSessionFiles.add(
        new FileBackedNativeSessionFile(
            "device_meta_file", "device", fileProvider.getDeviceFile()));
    nativeSessionFiles.add(
        new FileBackedNativeSessionFile("os_meta_file", "os", fileProvider.getOsFile()));
    nativeSessionFiles.add(
        new FileBackedNativeSessionFile(
            "minidump_file", "minidump", fileProvider.getMinidumpFile()));
    nativeSessionFiles.add(new FileBackedNativeSessionFile("user_meta_file", "user", userFile));
    nativeSessionFiles.add(new FileBackedNativeSessionFile("keys_file", "keys", keysFile));
    return nativeSessionFiles;
  }

  // endregion

  // region ApplicationExitInfo

  /** If an ApplicationExitInfo exists relevant to the session, writes that event. */
  private void writeApplicationExitInfoEventIfRelevant(String sessionId) {
    if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      ActivityManager activityManager =
          (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
      // Gets all the available app exit infos.
      List<ApplicationExitInfo> applicationExitInfoList =
          activityManager.getHistoricalProcessExitReasons(null, 0, 0);

      // Passes the latest applicationExitInfo to ReportCoordinator, which persists it if it
      // happened during the session.
      if (applicationExitInfoList.size() != 0) {
        final LogFileManager relevantSessionLogManager = new LogFileManager(fileStore, sessionId);
        final UserMetadata relevantUserMetadata = new UserMetadata();
        relevantUserMetadata.setCustomKeys(new MetaDataStore(fileStore).readKeyData(sessionId));
        reportingCoordinator.persistRelevantAppExitInfoEvent(
            sessionId, applicationExitInfoList, relevantSessionLogManager, relevantUserMetadata);
      } else {
        Logger.getLogger().v("No ApplicationExitInfo available. Session: " + sessionId);
      }
    } else {
      Logger.getLogger()
          .v("ANR feature enabled, but device is API " + android.os.Build.VERSION.SDK_INT);
    }
  }
  // endregion
}
