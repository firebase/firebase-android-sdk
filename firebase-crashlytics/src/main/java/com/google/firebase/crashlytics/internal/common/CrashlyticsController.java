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

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.util.Base64;
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
import com.google.firebase.crashlytics.internal.metadata.LogFileManager;
import com.google.firebase.crashlytics.internal.metadata.UserMetadata;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport;
import com.google.firebase.crashlytics.internal.model.StaticSessionData;
import com.google.firebase.crashlytics.internal.persistence.FileStore;
import com.google.firebase.crashlytics.internal.settings.Settings;
import com.google.firebase.crashlytics.internal.settings.SettingsProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedSet;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeoutException;
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

  private static final String VERSION_CONTROL_INFO_KEY = "com.crashlytics.version-control-info";
  private static final String VERSION_CONTROL_INFO_FILE = "version-control-info.textproto";
  private static final String META_INF_FOLDER = "META-INF/";

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
  private final CrashlyticsAppQualitySessionsSubscriber sessionsSubscriber;
  private final SessionReportingCoordinator reportingCoordinator;

  private CrashlyticsUncaughtExceptionHandler crashHandler;
  private SettingsProvider settingsProvider = null;

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
      AnalyticsEventLogger analyticsEventLogger,
      CrashlyticsAppQualitySessionsSubscriber sessionsSubscriber) {
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
    this.sessionsSubscriber = sessionsSubscriber;
    this.reportingCoordinator = sessionReportingCoordinator;
  }

  private Context getContext() {
    return context;
  }

  // region Exception handling

  void enableExceptionHandling(
      String sessionIdentifier,
      Thread.UncaughtExceptionHandler defaultHandler,
      SettingsProvider settingsProvider) {
    this.settingsProvider = settingsProvider;
    // This must be called before installing the controller with
    // Thread.setDefaultUncaughtExceptionHandler to ensure that we are ready to handle
    // any crashes we catch.
    openSession(sessionIdentifier);
    final CrashlyticsUncaughtExceptionHandler.CrashListener crashListener =
        new CrashlyticsUncaughtExceptionHandler.CrashListener() {
          @Override
          public void onUncaughtException(
              @NonNull SettingsProvider settingsDataProvider,
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

  void handleUncaughtException(
      @NonNull SettingsProvider settingsProvider,
      @NonNull final Thread thread,
      @NonNull final Throwable ex) {
    handleUncaughtException(settingsProvider, thread, ex, /* isOnDemand= */ false);
  }

  synchronized void handleUncaughtException(
      @NonNull SettingsProvider settingsProvider,
      @NonNull final Thread thread,
      @NonNull final Throwable ex,
      boolean isOnDemand) {

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
                doCloseSessions(settingsProvider);
                doOpenSession(new CLSUUID(idManager).toString(), isOnDemand);

                // If automatic data collection is disabled, we'll need to wait until the next run
                // of the app.
                if (!dataCollectionArbiter.isAutomaticDataCollectionEnabled()) {
                  return Tasks.forResult(null);
                }

                Executor executor = backgroundWorker.getExecutor();

                return settingsProvider
                    .getSettingsAsync()
                    .onSuccessTask(
                        executor,
                        new SuccessContinuation<Settings, Void>() {
                          @NonNull
                          @Override
                          public Task<Void> then(@Nullable Settings settings) throws Exception {
                            if (settings == null) {
                              Logger.getLogger()
                                  .w(
                                      "Received null app settings, cannot send reports at crash time.");
                              return Tasks.forResult(null);
                            }
                            // Data collection is enabled, so it's safe to send the report.
                            return Tasks.whenAll(
                                logAnalyticsAppExceptionEvents(),
                                reportingCoordinator.sendReports(
                                    executor, isOnDemand ? currentSessionId : null));
                          }
                        });
              }
            });

    try {
      // TODO(mrober): Don't block the main thread ever for on-demand fatals.
      Utils.awaitEvenIfOnMainThread(handleUncaughtExceptionTask);
    } catch (TimeoutException e) {
      Logger.getLogger().e("Cannot send reports. Timed out while fetching settings.");
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
    // TODO(b/261014167): Use an explicit executor in continuations.
    @SuppressLint("TaskMainThread")
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

  // TODO(b/261014167): Use an explicit executor in continuations.
  @SuppressLint("TaskMainThread")
  Task<Void> submitAllReports(Task<Settings> settingsDataTask) {
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

                        return settingsDataTask.onSuccessTask(
                            executor,
                            new SuccessContinuation<Settings, Void>() {
                              @NonNull
                              @Override
                              public Task<Void> then(@Nullable Settings appSettingsData)
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

  void logFatalException(Thread thread, Throwable ex) {
    if (settingsProvider == null) {
      Logger.getLogger().w("settingsProvider not set");
      return;
    }
    handleUncaughtException(settingsProvider, thread, ex, /* isOnDemand= */ true);
  }

  void setUserId(String identifier) {
    userMetadata.setUserId(identifier);
  }

  void setCustomKey(String key, String value) {
    try {
      userMetadata.setCustomKey(key, value);
    } catch (IllegalArgumentException ex) {
      if (context != null && CommonUtils.isAppDebuggable(context)) {
        throw ex;
      } else {
        Logger.getLogger().e("Attempting to set custom attribute with null key, ignoring.");
      }
    }
  }

  void setCustomKeys(Map<String, String> keysAndValues) {
    userMetadata.setCustomKeys(keysAndValues);
  }

  void setInternalKey(String key, String value) {
    try {
      userMetadata.setInternalKey(key, value);
    } catch (IllegalArgumentException ex) {
      if (context != null && CommonUtils.isAppDebuggable(context)) {
        throw ex;
      } else {
        Logger.getLogger().e("Attempting to set custom attribute with null key, ignoring.");
      }
    }
  }

  // endregion

  // region Session Management

  /** Open a new session on the single-threaded executor. */
  void openSession(String sessionIdentifier) {
    backgroundWorker.submit(
        new Callable<Void>() {
          @Override
          public Void call() throws Exception {
            doOpenSession(sessionIdentifier, /*isOnDemand=*/ false);
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
   * @param settingsProvider
   */
  boolean finalizeSessions(SettingsProvider settingsProvider) {
    backgroundWorker.checkRunningOnThread();

    if (isHandlingException()) {
      Logger.getLogger().w("Skipping session finalization because a crash has already occurred.");
      return Boolean.FALSE;
    }

    Logger.getLogger().v("Finalizing previously open sessions.");
    try {
      doCloseSessions(true, settingsProvider);
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
  private void doOpenSession(String sessionIdentifier, Boolean isOnDemand) {
    final long startedAtSeconds = getCurrentTimestampSeconds();

    Logger.getLogger().d("Opening a new session with ID " + sessionIdentifier);

    final String generator =
        String.format(Locale.US, GENERATOR_FORMAT, CrashlyticsCore.getVersion());

    StaticSessionData.AppData appData = createAppData(idManager, this.appData);
    StaticSessionData.OsData osData = createOsData();
    StaticSessionData.DeviceData deviceData = createDeviceData(context);

    nativeComponent.prepareNativeSession(
        sessionIdentifier,
        generator,
        startedAtSeconds,
        StaticSessionData.create(appData, osData, deviceData));

    // If is on-demand fatal, we need to update the session id for userMetadata
    // as well(since we don't really change the object to a new one for a new session).
    // all the information in the previous session is still in memory, but we do need to
    // manually writing them into persistence for the new session.
    if (isOnDemand && sessionIdentifier != null) {
      userMetadata.setNewSession(sessionIdentifier);
    }

    logFileManager.setCurrentSession(sessionIdentifier);
    sessionsSubscriber.setSessionId(sessionIdentifier);
    reportingCoordinator.onBeginSession(sessionIdentifier, startedAtSeconds);
  }

  void doCloseSessions(SettingsProvider settingsProvider) {
    doCloseSessions(false, settingsProvider);
  }

  /**
   * Not synchronized/locked. Must be executed from the single thread executor service used by this
   * class.
   */
  private void doCloseSessions(boolean skipCurrentSession, SettingsProvider settingsProvider) {
    final int offset = skipCurrentSession ? 1 : 0;

    // :TODO HW2021 this implementation can be cleaned up.
    List<String> sortedOpenSessions =
        new ArrayList<>(reportingCoordinator.listSortedOpenSessionIds());

    if (sortedOpenSessions.size() <= offset) {
      Logger.getLogger().v("No open sessions to be closed.");
      return;
    }

    final String mostRecentSessionIdToClose = sortedOpenSessions.get(offset);

    if (settingsProvider.getSettingsSync().featureFlagData.collectAnrs) {
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
    } else {
      // The Sessions SDK can rotate the AQS session id independently of the Crashlytics session.
      // There is a window of time between Crashlytics closing the current session and opening a
      // new session when the Sessions SDK is able to rotate the AQS session id. For such cases it
      // is necessary to clear the current Crashlytics session id from the Sessions subscriber in
      // order to prevent the newly rotated AQS session id from being associated with the closed
      // Crashlytics session. On-demand fatals is an example of where this can happen.
      sessionsSubscriber.setSessionId(/* sessionId= */ null);
    }

    reportingCoordinator.finalizeSessions(getCurrentTimestampSeconds(), currentSessionId);
  }

  // endregion

  List<File> listAppExceptionMarkerFiles() {
    return fileStore.getCommonFiles(APP_EXCEPTION_MARKER_FILTER);
  }

  void saveVersionControlInfo() {
    try {
      String versionControlInfo = getVersionControlInfo();
      if (versionControlInfo != null) {
        setInternalKey(VERSION_CONTROL_INFO_KEY, versionControlInfo);
        Logger.getLogger().i("Saved version control info");
      }
    } catch (IOException e) {
      Logger.getLogger().w("Unable to save version control info", e);
    }
  }

  String getVersionControlInfo() throws IOException {
    InputStream is = getResourceAsStream(META_INF_FOLDER + VERSION_CONTROL_INFO_FILE);
    if (is == null) {
      return null;
    }

    Logger.getLogger().d("Read version control info");
    return Base64.encodeToString(readResource(is), 0);
  }

  private InputStream getResourceAsStream(String resource) {
    ClassLoader classLoader = this.getClass().getClassLoader();
    if (classLoader == null) {
      Logger.getLogger().w("Couldn't get Class Loader");
      return null;
    }

    InputStream is = classLoader.getResourceAsStream(resource);
    if (is == null) {
      Logger.getLogger().i("No version control information found");
      return null;
    }

    return is;
  }

  private static byte[] readResource(InputStream is) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[1024];
    int length;

    while ((length = is.read(buffer)) != -1) {
      out.write(buffer, 0, length);
    }

    return out.toByteArray();
  }

  private void finalizePreviousNativeSession(String previousSessionId) {
    Logger.getLogger().v("Finalizing native report for session " + previousSessionId);
    NativeSessionFileProvider nativeSessionFileProvider =
        nativeComponent.getSessionFileProvider(previousSessionId);
    File minidumpFile = nativeSessionFileProvider.getMinidumpFile();
    CrashlyticsReport.ApplicationExitInfo applicationExitInfo =
        nativeSessionFileProvider.getApplicationExitInto();

    if (nativeCoreAbsent(previousSessionId, minidumpFile, applicationExitInfo)) {
      Logger.getLogger().w("No native core present");
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

    reportingCoordinator.finalizeSessionWithNativeEvent(
        previousSessionId, nativeSessionFiles, applicationExitInfo);
    previousSessionLogManager.clearLog();
  }

  private static boolean nativeCoreAbsent(
      String previousSessionId,
      File minidumpFile,
      CrashlyticsReport.ApplicationExitInfo applicationExitInfo) {
    if (minidumpFile == null || !minidumpFile.exists()) {
      Logger.getLogger().w("No minidump data found for session " + previousSessionId);
    }

    if (applicationExitInfo == null) {
      Logger.getLogger().i("No Tombstones data found for session " + previousSessionId);
    }

    return (minidumpFile == null || !minidumpFile.exists()) && applicationExitInfo == null;
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
        idManager.getInstallIds().getCrashlyticsInstallId(),
        DeliveryMechanism.determineFrom(appData.installerPackageName).getId(),
        appData.developmentPlatformProvider);
  }

  private static StaticSessionData.OsData createOsData() {
    return StaticSessionData.OsData.create(
        VERSION.RELEASE, VERSION.CODENAME, CommonUtils.isRooted());
  }

  private static StaticSessionData.DeviceData createDeviceData(Context context) {
    final StatFs statFs = new StatFs(Environment.getDataDirectory().getPath());
    final long diskSpace = (long) statFs.getBlockCount() * (long) statFs.getBlockSize();

    return StaticSessionData.DeviceData.create(
        CommonUtils.getCpuArchitectureInt(),
        Build.MODEL,
        Runtime.getRuntime().availableProcessors(),
        CommonUtils.calculateTotalRamInBytes(context),
        diskSpace,
        CommonUtils.isEmulator(),
        CommonUtils.getDeviceState(),
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

    // TODO(b/258263226): Migrate to go/firebase-android-executors
    @SuppressLint("ThreadPoolCreation")
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

    final File userFile =
        fileStore.getSessionFile(previousSessionId, UserMetadata.USERDATA_FILENAME);
    final File keysFile =
        fileStore.getSessionFile(previousSessionId, UserMetadata.KEYDATA_FILENAME);
    final File rolloutsFile =
        fileStore.getSessionFile(previousSessionId, UserMetadata.ROLLOUTS_STATE_FILENAME);

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
    nativeSessionFiles.add(nativeCoreFile(fileProvider));
    nativeSessionFiles.add(new FileBackedNativeSessionFile("user_meta_file", "user", userFile));
    nativeSessionFiles.add(new FileBackedNativeSessionFile("keys_file", "keys", keysFile));
    nativeSessionFiles.add(
        new FileBackedNativeSessionFile("rollouts_file", "rollouts", rolloutsFile));
    return nativeSessionFiles;
  }

  private static NativeSessionFile nativeCoreFile(NativeSessionFileProvider fileProvider) {
    File minidump = fileProvider.getMinidumpFile();

    return minidump == null || !minidump.exists()
        ? new BytesBackedNativeSessionFile("minidump_file", "minidump", new byte[] {0})
        : new FileBackedNativeSessionFile("minidump_file", "minidump", minidump);
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
        final UserMetadata relevantUserMetadata =
            UserMetadata.loadFromExistingSession(sessionId, fileStore, backgroundWorker);
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
