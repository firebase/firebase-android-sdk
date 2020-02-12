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
package com.google.firebase.crashlytics.core;

import static com.google.firebase.crashlytics.internal.proto.ClsFileOutputStream.IN_PROGRESS_SESSION_FILE_EXTENSION;
import static com.google.firebase.crashlytics.internal.proto.ClsFileOutputStream.SESSION_FILE_EXTENSION;

import android.app.ActivityManager.RunningAppProcessInfo;
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
import com.google.firebase.analytics.connector.AnalyticsConnector;
import com.google.firebase.crashlytics.internal.CrashlyticsNativeComponent;
import com.google.firebase.crashlytics.internal.Logger;
import com.google.firebase.crashlytics.internal.NativeSessionFileProvider;
import com.google.firebase.crashlytics.internal.breadcrumbs.BreadcrumbsReceiver;
import com.google.firebase.crashlytics.internal.common.AppData;
import com.google.firebase.crashlytics.internal.common.BatteryState;
import com.google.firebase.crashlytics.internal.common.CommonUtils;
import com.google.firebase.crashlytics.internal.common.DataCollectionArbiter;
import com.google.firebase.crashlytics.internal.common.DeliveryMechanism;
import com.google.firebase.crashlytics.internal.common.IdManager;
import com.google.firebase.crashlytics.internal.log.LogFileManager;
import com.google.firebase.crashlytics.internal.ndk.NativeFileUtils;
import com.google.firebase.crashlytics.internal.network.HttpRequestFactory;
import com.google.firebase.crashlytics.internal.persistence.FileStore;
import com.google.firebase.crashlytics.internal.proto.ClsFileOutputStream;
import com.google.firebase.crashlytics.internal.proto.CodedOutputStream;
import com.google.firebase.crashlytics.internal.proto.SessionProtobufHelper;
import com.google.firebase.crashlytics.internal.report.ReportManager;
import com.google.firebase.crashlytics.internal.report.ReportUploader;
import com.google.firebase.crashlytics.internal.report.model.Report;
import com.google.firebase.crashlytics.internal.report.model.SessionReport;
import com.google.firebase.crashlytics.internal.report.network.CompositeCreateReportSpiCall;
import com.google.firebase.crashlytics.internal.report.network.CreateReportSpiCall;
import com.google.firebase.crashlytics.internal.report.network.DefaultCreateReportSpiCall;
import com.google.firebase.crashlytics.internal.report.network.NativeCreateReportSpiCall;
import com.google.firebase.crashlytics.internal.settings.SettingsDataProvider;
import com.google.firebase.crashlytics.internal.settings.model.AppSettingsData;
import com.google.firebase.crashlytics.internal.settings.model.Settings;
import com.google.firebase.crashlytics.internal.stacktrace.MiddleOutFallbackStrategy;
import com.google.firebase.crashlytics.internal.stacktrace.RemoveRepeatsStrategy;
import com.google.firebase.crashlytics.internal.stacktrace.StackTraceTrimmingStrategy;
import com.google.firebase.crashlytics.internal.stacktrace.TrimmedThrowableData;
import com.google.firebase.crashlytics.internal.unity.UnityVersionProvider;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

@SuppressWarnings("PMD")
class CrashlyticsController {

  static final String SESSION_USER_TAG = "SessionUser";
  static final String SESSION_NON_FATAL_TAG = "SessionEvent";
  static final String SESSION_FATAL_TAG = "SessionCrash";
  static final String SESSION_APP_TAG = "SessionApp";
  static final String SESSION_OS_TAG = "SessionOS";
  static final String SESSION_DEVICE_TAG = "SessionDevice";
  static final String SESSION_BEGIN_TAG = "BeginSession";
  static final String SESSION_EVENT_MISSING_BINARY_IMGS_TAG = "SessionMissingBinaryImages";

  static final String FIREBASE_CRASH_TYPE = "fatal";
  static final String FIREBASE_TIMESTAMP = "timestamp";
  static final String FIREBASE_APPLICATION_EXCEPTION = "_ae";
  static final String FIREBASE_ANALYTICS_ORIGIN_CRASHLYTICS = "clx";

  // region CLS File filters for retrieving specific sets of files.

  /** File filter that matches if a specified string is contained in the file name. */
  static class FileNameContainsFilter implements FilenameFilter {
    private final String string;

    public FileNameContainsFilter(String s) {
      string = s;
    }

    @Override
    public boolean accept(File dir, String filename) {
      return filename.contains(string) && !filename.endsWith(IN_PROGRESS_SESSION_FILE_EXTENSION);
    }
  }

  /**
   * File filter that returns files whose names contain the provided session ID, but which aren't
   * the completed session file itself.
   */
  static class SessionPartFileFilter implements FilenameFilter {
    private final String sessionId;

    public SessionPartFileFilter(String sessionId) {
      this.sessionId = sessionId;
    }

    @Override
    public boolean accept(File file, String fileName) {
      if (fileName.equals(sessionId + SESSION_FILE_EXTENSION)) {
        return false;
      } else {
        return fileName.contains(sessionId)
            && !fileName.endsWith(IN_PROGRESS_SESSION_FILE_EXTENSION);
      }
    }
  }

  /**
   * File filter that matches any file which is a session part file for any session, but is not a
   * complete session file. Includes temp/in-progress files!
   */
  private static class AnySessionPartFileFilter implements FilenameFilter {
    @Override
    public boolean accept(File file, String fileName) {
      return !SESSION_FILE_FILTER.accept(file, fileName)
          && SESSION_FILE_PATTERN.matcher(fileName).matches();
    }
  }

  static class InvalidPartFileFilter implements FilenameFilter {
    @Override
    public boolean accept(File file, String fileName) {
      return ClsFileOutputStream.TEMP_FILENAME_FILTER.accept(file, fileName)
          || fileName.contains(SESSION_EVENT_MISSING_BINARY_IMGS_TAG);
    }
  }

  static final FilenameFilter SESSION_BEGIN_FILE_FILTER =
      new FileNameContainsFilter(SESSION_BEGIN_TAG) {
        @Override
        public boolean accept(File dir, String filename) {
          return super.accept(dir, filename) && filename.endsWith(SESSION_FILE_EXTENSION);
        }
      };

  /**
   * Matches *.cls filenames with exactly 39 character names (32 UUID + 3 dashes + dot + extension).
   */
  static final FilenameFilter SESSION_FILE_FILTER =
      new FilenameFilter() {
        @Override
        public boolean accept(File dir, String filename) {
          return (filename.length() == (35 + SESSION_FILE_EXTENSION.length()))
              && filename.endsWith(SESSION_FILE_EXTENSION);
        }
      };

  static final Comparator<File> LARGEST_FILE_NAME_FIRST =
      new Comparator<File>() {
        @Override
        public int compare(File file1, File file2) {
          return file2.getName().compareTo(file1.getName());
        }
      };

  static final Comparator<File> SMALLEST_FILE_NAME_FIRST =
      new Comparator<File>() {
        @Override
        public int compare(File file1, File file2) {
          return file1.getName().compareTo(file2.getName());
        }
      };

  private static final Pattern SESSION_FILE_PATTERN =
      Pattern.compile(
          "([\\d|A-Z|a-z]{12}\\-[\\d|A-Z|a-z]{4}\\-[\\d|A-Z|a-z]{4}\\-[\\d|A-Z|a-z]{12}).+");

  // endregion

  private static final String CRASHLYTICS_API_ENDPOINT = "com.crashlytics.ApiEndpoint";

  // Indicates that a crash report is being sent at the time of the crash and not on next launch.
  private static final Map<String, String> SEND_AT_CRASHTIME_HEADER =
      Collections.singletonMap("X-CRASHLYTICS-SEND-FLAGS", "1");

  private static final int MAX_LOCAL_LOGGED_EXCEPTIONS = 64;
  static final int MAX_OPEN_SESSIONS = 8;

  private static final int MAX_CHAINED_EXCEPTION_DEPTH = 8;

  static final int MAX_STACK_SIZE = 1024;
  static final int NUM_STACK_REPETITIONS_ALLOWED = 10;

  static final String NONFATAL_SESSION_DIR = "nonfatal-sessions";
  static final String FATAL_SESSION_DIR = "fatal-sessions";
  static final String NATIVE_SESSION_DIR = "native-sessions";

  static final int FIREBASE_CRASH_TYPE_FATAL = 1;

  private static final String GENERATOR_FORMAT = "Crashlytics Android SDK/%s";

  private static final String EVENT_TYPE_CRASH = "crash";
  private static final String EVENT_TYPE_LOGGED = "error";

  private static final int SESSION_ID_LENGTH = 35;

  private static final int ANALYZER_VERSION = 1;

  private static final String COLLECT_CUSTOM_KEYS = "com.crashlytics.CollectCustomKeys";

  private static final String[] INITIAL_SESSION_PART_TAGS = {
    SESSION_USER_TAG, SESSION_APP_TAG, SESSION_OS_TAG, SESSION_DEVICE_TAG
  };

  private final AtomicInteger eventCounter = new AtomicInteger(0);

  private final Context context;
  private final DataCollectionArbiter dataCollectionArbiter;
  private final CrashlyticsFileMarker crashMarker;
  private final UserMetadata userMetadata;

  private final CrashlyticsBackgroundWorker backgroundWorker;

  private final HttpRequestFactory httpRequestFactory;
  private final IdManager idManager;
  private final FileStore fileStore;

  private final AppData appData;

  private final ReportUploader.Provider reportUploaderProvider;
  private final LogFileDirectoryProvider logFileDirectoryProvider;
  private final LogFileManager logFileManager;
  private final ReportManager reportManager;
  private final ReportUploader.HandlingExceptionCheck handlingExceptionCheck;
  private final CrashlyticsNativeComponent nativeComponent;
  private final StackTraceTrimmingStrategy stackTraceTrimmingStrategy;
  private final String unityVersion;
  private final BreadcrumbsReceiver breadcrumbsReceiver;
  private final AnalyticsConnector analyticsConnector;

  private CrashlyticsUncaughtExceptionHandler crashHandler;

  // A promise that will be resolved when unsent reports are found on the device, and
  // send/deleteUnsentReports can be called to decide how to deal with them.
  TaskCompletionSource<Boolean> unsentReportsAvailable = new TaskCompletionSource<>();

  // A promise that will be resolved when the user has provided an action that they want to perform
  // for all the unsent reports.
  TaskCompletionSource<Boolean> reportActionProvided = new TaskCompletionSource<>();

  // A promise that will be resolved when all unsent reports have been "handled". They won't
  // necessarily have been uploaded, but we will know whether they should be sent or deleted, and
  // the initial work to make that happen will have been processed on the work queue.
  TaskCompletionSource<Void> unsentReportsHandled = new TaskCompletionSource<>();

  // A token to make sure that checkForUnsentReports only gets called once.
  AtomicBoolean checkForUnsentReportsCalled = new AtomicBoolean(false);

  CrashlyticsController(
      final Context context,
      CrashlyticsBackgroundWorker backgroundWorker,
      HttpRequestFactory httpRequestFactory,
      IdManager idManager,
      DataCollectionArbiter dataCollectionArbiter,
      FileStore fileStore,
      CrashlyticsFileMarker crashMarker,
      AppData appData,
      ReportManager reportManager,
      ReportUploader.Provider reportUploaderProvider,
      CrashlyticsNativeComponent nativeComponent,
      UnityVersionProvider unityVersionProvider,
      BreadcrumbsReceiver breadcrumbsReceiver,
      AnalyticsConnector analyticsConnector) {
    this.context = context;
    this.backgroundWorker = backgroundWorker;
    this.httpRequestFactory = httpRequestFactory;
    this.idManager = idManager;
    this.dataCollectionArbiter = dataCollectionArbiter;
    this.fileStore = fileStore;
    this.crashMarker = crashMarker;
    this.appData = appData;

    if (reportUploaderProvider != null) {
      this.reportUploaderProvider = reportUploaderProvider;
    } else {
      this.reportUploaderProvider = defaultReportUploader();
    }
    this.nativeComponent = nativeComponent;
    this.unityVersion = unityVersionProvider.getUnityVersion();
    this.breadcrumbsReceiver = breadcrumbsReceiver;
    this.analyticsConnector = analyticsConnector;

    this.userMetadata = new UserMetadata();

    logFileDirectoryProvider = new LogFileDirectoryProvider(fileStore);
    logFileManager = new LogFileManager(context, logFileDirectoryProvider);
    if (reportManager == null) {
      reportManager = new ReportManager(new ReportUploaderFilesProvider());
    }
    this.reportManager = reportManager;
    handlingExceptionCheck = new ReportUploaderHandlingExceptionCheck();
    stackTraceTrimmingStrategy =
        new MiddleOutFallbackStrategy(
            MAX_STACK_SIZE, new RemoveRepeatsStrategy(NUM_STACK_REPETITIONS_ALLOWED));
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
              SettingsDataProvider settingsDataProvider, Thread thread, Throwable ex) {
            handleUncaughtException(settingsDataProvider, thread, ex);
          }
        };
    crashHandler =
        new CrashlyticsUncaughtExceptionHandler(crashListener, settingsProvider, defaultHandler);
    Thread.setDefaultUncaughtExceptionHandler(crashHandler);
  }

  synchronized void handleUncaughtException(
      SettingsDataProvider settingsDataProvider, final Thread thread, final Throwable ex) {

    Logger.getLogger()
        .d(
            Logger.TAG,
            "Crashlytics is handling uncaught "
                + "exception \""
                + ex
                + "\" from thread "
                + thread.getName());

    // Capture the time that the crash occurs and close over it so that the time doesn't
    // reflect when we get around to executing the task later.
    final Date time = new Date();

    Task<Void> task =
        backgroundWorker.submitTask(
            new Callable<Task<Void>>() {
              @Override
              public Task<Void> call() throws Exception {
                // We've fatally crashed, so write the marker file that indicates a crash occurred.
                crashMarker.create();

                writeFatal(time, thread, ex);

                Settings settings = settingsDataProvider.getSettings();
                int maxCustomExceptionEvents = settings.getSessionData().maxCustomExceptionEvents;
                int maxCompleteSessionsCount = settings.getSessionData().maxCompleteSessionsCount;

                recordFatalFirebaseEvent(time.getTime());

                doCloseSessions(maxCustomExceptionEvents);
                doOpenSession();

                trimSessionFiles(maxCompleteSessionsCount);

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
                            // Data collection is enabled, so it's safe to send the report.
                            boolean dataCollectionToken = true;
                            sendSessionReports(appSettingsData, dataCollectionToken);
                            return null;
                          }
                        });
              }
            });
    try {
      Utils.awaitEvenIfOnMainThread(task);
    } catch (Exception e) {
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
      Logger.getLogger().d(Logger.TAG, "Automatic data collection is enabled. Allowing upload.");
      unsentReportsAvailable.trySetResult(false);
      return Tasks.forResult(true);
    }

    Logger.getLogger().d(Logger.TAG, "Automatic data collection is disabled.");
    Logger.getLogger().d(Logger.TAG, "Notifying that unsent reports are available.");
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

    Logger.getLogger().d(Logger.TAG, "Waiting for send/deleteUnsentReports to be called.");
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

    Logger.getLogger().d(Logger.TAG, "Found previous crash marker.");
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
      Logger.getLogger()
          .d(Logger.TAG, "checkForUnsentReports should only be called once per execution.");
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

  Task<Void> submitAllReports(float delay, Task<AppSettingsData> appSettingsDataTask) {
    if (!reportManager.areReportsAvailable()) {
      // Just notify the user that there are no reports and stop.
      Logger.getLogger().d(Logger.TAG, "No reports are available.");
      unsentReportsAvailable.trySetResult(false);
      return Tasks.forResult(null);
    }
    Logger.getLogger().d(Logger.TAG, "Unsent reports are available.");

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
                        List<Report> reports = reportManager.findReports();

                        if (!send) {
                          Logger.getLogger().d(Logger.TAG, "Reports are being deleted.");
                          reportManager.deleteReports(reports);
                          unsentReportsHandled.trySetResult(null);
                          return Tasks.forResult(null);
                        }

                        Logger.getLogger().d(Logger.TAG, "Reports are being sent.");

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
                                // Append the most recent org ID to each report file, even if it
                                // was already appended during the crash time upload. This way
                                // we'll always have the most recent value available attached.
                                for (Report report : reports) {
                                  if (report.getType() == Report.Type.JAVA) {
                                    appendOrganizationIdToSessionFile(
                                        appSettingsData.organizationId, report.getFile());
                                  }
                                }
                                ReportUploader uploader =
                                    reportUploaderProvider.createReportUploader(appSettingsData);
                                uploader.uploadReportsAsync(reports, dataCollectionToken, delay);
                                unsentReportsHandled.trySetResult(null);

                                return Tasks.forResult(null);
                              }
                            });
                      }
                    });
              }
            });
  }

  private ReportUploader.Provider defaultReportUploader() {
    return new ReportUploader.Provider() {
      @Override
      public ReportUploader createReportUploader(AppSettingsData appSettingsData) {
        final String reportsUrl = appSettingsData.reportsUrl;
        final String ndkReportsUrl = appSettingsData.ndkReportsUrl;
        final String organizationId = appSettingsData.organizationId;
        final CreateReportSpiCall call = getCreateReportSpiCall(reportsUrl, ndkReportsUrl);
        return new ReportUploader(
            organizationId, appData.googleAppId, reportManager, call, handlingExceptionCheck);
      }
    };
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
  void writeNonFatalException(final Thread thread, final Throwable ex) {
    // Capture and close over the current time, so that we get the exact call time,
    // rather than the time at which the task executes.
    final Date now = new Date();

    backgroundWorker.submit(
        new Runnable() {
          @Override
          public void run() {
            if (!isHandlingException()) {
              doWriteNonFatal(now, thread, ex);
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
      userMetadata.setCustomKey(key, value);
    } catch (IllegalArgumentException ex) {
      if (context != null && CommonUtils.isAppDebuggable(context)) {
        throw ex;
      } else {
        Logger.getLogger()
            .e(Logger.TAG, "Attempting to set custom attribute with null key, ignoring.", null);
        return;
      }
    }
    cacheKeyData(userMetadata.getCustomKeys());
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
            new MetaDataStore(getFilesDir()).writeUserData(currentSessionId, userMetaData);
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
  private void cacheKeyData(final Map<String, String> keyData) {
    backgroundWorker.submit(
        new Callable<Void>() {
          @Override
          public Void call() throws Exception {
            final String currentSessionId = getCurrentSessionId();
            new MetaDataStore(getFilesDir()).writeKeyData(currentSessionId, keyData);
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
   * <p>May return <code>null</code> if no session begin file is present.
   */
  private String getCurrentSessionId() {
    final File[] sessionBeginFiles = listSortedSessionBeginFiles();
    return (sessionBeginFiles.length > 0)
        ? getSessionIdFromSessionFile(sessionBeginFiles[0])
        : null;
  }

  /** @return */
  private String getPreviousSessionId() {
    final File[] sessionBeginFiles = listSortedSessionBeginFiles();
    return (sessionBeginFiles.length > 1)
        ? getSessionIdFromSessionFile(sessionBeginFiles[1])
        : null;
  }

  /**
   * Returns the session ID that forms the beginning part of the name of the provided file
   *
   * @param sessionFile The filename must match the expected format for a session file or session
   *     part file. That is, it must begin with a 35 character CLSUUID, followed by an optional part
   *     tag, ending with either .cls or .cls_temp
   * @return the session ID portion of the file name
   * @see CLSUUID
   */
  static String getSessionIdFromSessionFile(File sessionFile) {
    return sessionFile.getName().substring(0, SESSION_ID_LENGTH);
  }

  boolean hasOpenSession() {
    // listSessionBeginFiles is guaranteed not to return null
    return listSessionBeginFiles().length > 0;
  }

  /**
   * Closes any previously open sessions
   *
   * <p>return false if sessions could not be closed due to current exception handling
   *
   * <p>This method can not be called while the {@link CrashlyticsCore} settings lock is held. It
   * will result in a deadlock!
   */
  boolean finalizeSessions(int maxCustomExceptionEvents) {
    backgroundWorker.checkRunningOnThread();

    if (isHandlingException()) {
      Logger.getLogger()
          .d(Logger.TAG, "Skipping session finalization because a crash has already occurred.");
      return Boolean.FALSE;
    }

    Logger.getLogger().d(Logger.TAG, "Finalizing previously open sessions.");
    try {
      doCloseSessions(maxCustomExceptionEvents, true);
    } catch (Exception e) {
      Logger.getLogger().e(Logger.TAG, "Unable to finalize previously open sessions.", e);
      return false;
    }
    Logger.getLogger().d(Logger.TAG, "Closed all previously open sessions");

    return true;
  }

  /**
   * Not synchronized/locked. Must be executed from the single thread executor service used by this
   * class.
   */
  private void doOpenSession() throws Exception {
    final Date startedAt = new Date();
    final String sessionIdentifier = new CLSUUID(idManager).toString();

    Logger.getLogger().d(Logger.TAG, "Opening a new session with ID " + sessionIdentifier);

    nativeComponent.openSession(sessionIdentifier);

    writeBeginSession(sessionIdentifier, startedAt);
    writeSessionApp(sessionIdentifier);
    writeSessionOS(sessionIdentifier);
    writeSessionDevice(sessionIdentifier);
    logFileManager.setCurrentSession(sessionIdentifier);
  }

  void doCloseSessions(int maxCustomExceptionEvents) throws Exception {
    doCloseSessions(maxCustomExceptionEvents, false);
  }

  /**
   * Not synchronized/locked. Must be executed from the single thread executor service used by this
   * class.
   */
  private void doCloseSessions(int maxCustomExceptionEvents, boolean excludeCurrent)
      throws Exception {
    final int offset = excludeCurrent ? 1 : 0;

    trimOpenSessions(MAX_OPEN_SESSIONS + offset);

    final File[] sessionBeginFiles = listSortedSessionBeginFiles();

    if (sessionBeginFiles.length <= offset) {
      Logger.getLogger().d(Logger.TAG, "No open sessions to be closed.");
      return;
    }

    final String mostRecentSessionIdToClose =
        getSessionIdFromSessionFile(sessionBeginFiles[offset]);

    // We delay writing the user information until session close time so that there's the
    // maximum chance that the user code that sets this information has been run.
    writeSessionUser(mostRecentSessionIdToClose);

    closeOpenSessions(sessionBeginFiles, offset, maxCustomExceptionEvents);
  }

  /**
   * Closes the open sessions for each given session begin file, starting at the given index. Allows
   * for the index to be passed in in the case we want to exclude the latest open session.
   */
  private void closeOpenSessions(
      File[] sessionBeginFiles, int beginIndex, int maxLoggedExceptionsCount) {
    Logger.getLogger().d(Logger.TAG, "Closing open sessions.");

    for (int i = beginIndex; i < sessionBeginFiles.length; ++i) {
      final File sessionBeginFile = sessionBeginFiles[i];
      final String sessionIdentifier = getSessionIdFromSessionFile(sessionBeginFile);

      Logger.getLogger().d(Logger.TAG, "Closing session: " + sessionIdentifier);
      writeSessionPartsToSessionFile(sessionBeginFile, sessionIdentifier, maxLoggedExceptionsCount);
    }
  }

  // endregion

  // region File management

  /**
   * Calls {@link ClsFileOutputStream#closeInProgressStream()} on the provided {@link
   * ClsFileOutputStream}, logging any exception that is thrown.
   *
   * @param fos May be <code>null</code>, in which case nothing happens.
   */
  private void closeWithoutRenamingOrLog(ClsFileOutputStream fos) {
    if (fos == null) {
      return;
    }

    try {
      fos.closeInProgressStream();
    } catch (IOException ex) {
      Logger.getLogger()
          .e(Logger.TAG, "Error closing session file stream in the presence of an exception", ex);
    }
  }

  /**
   * Not synchronized/locked. Must be executed from the single thread executor service used by this
   * class.
   */
  private void deleteSessionPartFilesFor(String sessionId) {
    for (File file : listSessionPartFilesFor(sessionId)) {
      file.delete();
    }
  }

  /** Lists any files that contain the session ID that aren't the completed session file itself. */
  private File[] listSessionPartFilesFor(String sessionId) {
    return listFilesMatching(new SessionPartFileFilter(sessionId));
  }

  File[] listCompleteSessionFiles() {
    final List<File> completeSessionFiles = new LinkedList<>();
    Collections.addAll(
        completeSessionFiles, listFilesMatching(getFatalSessionFilesDir(), SESSION_FILE_FILTER));
    Collections.addAll(
        completeSessionFiles, listFilesMatching(getNonFatalSessionFilesDir(), SESSION_FILE_FILTER));
    Collections.addAll(completeSessionFiles, listFilesMatching(getFilesDir(), SESSION_FILE_FILTER));
    return completeSessionFiles.toArray(new File[completeSessionFiles.size()]);
  }

  File[] listNativeSessionFileDirectories() {
    return ensureFileArrayNotNull(getNativeSessionFilesDir().listFiles());
  }

  File[] listSessionBeginFiles() {
    return listFilesMatching(SESSION_BEGIN_FILE_FILTER);
  }

  private File[] listSortedSessionBeginFiles() {
    final File[] sessionBeginFiles = listSessionBeginFiles();
    Arrays.sort(sessionBeginFiles, LARGEST_FILE_NAME_FIRST);
    return sessionBeginFiles;
  }

  private File[] listFilesMatching(FilenameFilter filter) {
    return listFilesMatching(getFilesDir(), filter);
  }

  private File[] listFilesMatching(File directory, FilenameFilter filter) {
    return ensureFileArrayNotNull(directory.listFiles(filter));
  }

  private File[] listFiles(File directory) {
    return ensureFileArrayNotNull(directory.listFiles());
  }

  private File[] ensureFileArrayNotNull(File[] files) {
    return (files == null) ? new File[] {} : files;
  }

  /**
   * Not synchronized/locked. Must be executed from the single thread executor service used by this
   * class.
   *
   * <p>Trims the number of session events to the configured max.
   */
  private void trimSessionEventFiles(String sessionId, int limit) {
    Utils.capFileCount(
        getFilesDir(),
        new FileNameContainsFilter(sessionId + SESSION_NON_FATAL_TAG),
        limit,
        SMALLEST_FILE_NAME_FIRST);
  }

  /**
   * Not synchronized/locked. Must be executed from the single thread executor service used by this
   * class.
   *
   * <p>If there are more than maxCompleteSessionsCount session files, delete files (oldest-first)
   * until we are at the max.
   */
  void trimSessionFiles(int maxCompleteSessionsCount) {
    int remaining = maxCompleteSessionsCount;
    remaining =
        remaining
            - Utils.capSessionCount(
                getNativeSessionFilesDir(),
                getFatalSessionFilesDir(),
                remaining,
                SMALLEST_FILE_NAME_FIRST);
    remaining =
        remaining
            - Utils.capFileCount(getNonFatalSessionFilesDir(), remaining, SMALLEST_FILE_NAME_FIRST);
    Utils.capFileCount(getFilesDir(), SESSION_FILE_FILTER, remaining, SMALLEST_FILE_NAME_FIRST);
  }

  private void trimOpenSessions(int maxOpenSessionCount) {
    final Set<String> sessionIdsToKeep = new HashSet<String>();

    final File[] beginSessionFiles = listSortedSessionBeginFiles();
    final int count = Math.min(maxOpenSessionCount, beginSessionFiles.length);

    for (int i = 0; i < count; i++) {
      final String sessionId = getSessionIdFromSessionFile(beginSessionFiles[i]);
      sessionIdsToKeep.add(sessionId);
    }

    logFileManager.discardOldLogFiles(sessionIdsToKeep);

    retainSessions(listFilesMatching(new AnySessionPartFileFilter()), sessionIdsToKeep);
  }

  private void retainSessions(File[] files, Set<String> sessionIdsToKeep) {
    for (File sessionPartFile : files) {
      final String fileName = sessionPartFile.getName();
      final Matcher matcher = SESSION_FILE_PATTERN.matcher(fileName);

      if (!matcher.matches()) {
        Logger.getLogger().d(Logger.TAG, "Deleting unknown file: " + fileName);
        sessionPartFile.delete();
        continue;
      }

      final String sessionId = matcher.group(1);
      if (!sessionIdsToKeep.contains(sessionId)) {
        Logger.getLogger().d(Logger.TAG, "Trimming session file: " + fileName);
        sessionPartFile.delete();
      }
    }
  }

  /**
   * Trims the given set of non-fatal files down to the given maximum length, deleting files that
   * don't fit the maximum length.
   *
   * @return the appropriate set of non-fatal files after trimming is complete.
   */
  private File[] getTrimmedNonFatalFiles(
      String sessionId, File[] nonFatalFiles, int maxLoggedExceptionsCount) {
    if (nonFatalFiles.length > maxLoggedExceptionsCount) {
      Logger.getLogger()
          .d(
              Logger.TAG,
              String.format(
                  Locale.US, "Trimming down to %d logged exceptions.", maxLoggedExceptionsCount));
      trimSessionEventFiles(sessionId, maxLoggedExceptionsCount);
      nonFatalFiles =
          listFilesMatching(new FileNameContainsFilter(sessionId + SESSION_NON_FATAL_TAG));
    }
    return nonFatalFiles;
  }

  /**
   * Asynchronously finds all invalid temp files and deletes all session part files in the related
   * sessions.
   */
  void cleanInvalidTempFiles() {
    backgroundWorker.submit(
        new Runnable() {
          @Override
          public void run() {
            doCleanInvalidTempFiles(listFilesMatching(new InvalidPartFileFilter()));
          }
        });
  }

  /**
   * Not synchronized/locked. Must be executed from the single thread executor service used by this
   * class.
   *
   * <p>Package protected so that tests can use it directly - other users must call
   * cleanInvalidTempFiles(File[])
   */
  void doCleanInvalidTempFiles(File[] invalidFiles) {
    final Set<String> invalidSessionIds = new HashSet<>();

    // If a temp file name exists as part of a group of session part files, that session failed
    // to be opened properly and is now invalid. Clean it up by moving it to a quarantine
    // directory where it can be dealt with separately.
    for (File invalidFile : invalidFiles) {
      Logger.getLogger().d(Logger.TAG, "Found invalid session part file: " + invalidFile);
      invalidSessionIds.add(getSessionIdFromSessionFile(invalidFile));
    }

    if (invalidSessionIds.isEmpty()) {
      return;
    }

    final FilenameFilter invalidSessionFilter =
        new FilenameFilter() {
          @Override
          public boolean accept(File dir, String filename) {
            if (filename.length() < SESSION_ID_LENGTH) {
              return false;
            }
            return invalidSessionIds.contains(filename.substring(0, SESSION_ID_LENGTH));
          }
        };

    for (File sessionFile : listFilesMatching(invalidSessionFilter)) {
      Logger.getLogger().d(Logger.TAG, "Deleting invalid session file: " + sessionFile);
      sessionFile.delete();
    }
  }

  // endregion

  // TODO: Finalize native sessions for *all* older open sessions, not just previous.
  boolean finalizeNativeSessions() {
    backgroundWorker.checkRunningOnThread();
    final String previousSessionId = getPreviousSessionId();
    if (previousSessionId == null) {
      // No previous session to finalize
      return true;
    }
    try {
      finalizePreviousNativeSession(previousSessionId);
      return nativeComponent.finalizeSession(previousSessionId);
    } catch (Exception e) {
      Logger.getLogger().e(Logger.TAG, "Unable to finalize native crash " + previousSessionId, e);
      return false;
    }
  }

  private void finalizePreviousNativeSession(String previousSessionId) throws IOException {
    NativeSessionFileProvider nativeSessionFileProvider =
        nativeComponent.getSessionFileProvider(previousSessionId);

    final File minidump = nativeSessionFileProvider.getMinidumpFile();
    final File binaryImages = nativeSessionFileProvider.getBinaryImagesFile();
    final File metadata = nativeSessionFileProvider.getMetadataFile();
    final File sessionFile = nativeSessionFileProvider.getSessionFile();
    final File sessionApp = nativeSessionFileProvider.getAppFile();
    final File sessionDevice = nativeSessionFileProvider.getDeviceFile();
    final File sessionOs = nativeSessionFileProvider.getOsFile();

    if (minidump == null || !minidump.exists()) {
      Logger.getLogger().w(Logger.TAG, "No minidump data found for session " + previousSessionId);
      return;
    }

    final File filesDir = getFilesDir();
    final MetaDataStore metaDataStore = new MetaDataStore(filesDir);
    final File sessionUser = metaDataStore.getUserDataFileForSession(previousSessionId);
    final File sessionKeys = metaDataStore.getKeysFileForSession(previousSessionId);

    final LogFileManager previousSessionLogManager =
        new LogFileManager(getContext(), logFileDirectoryProvider, previousSessionId);
    final byte[] logs = previousSessionLogManager.getBytesForLog();

    final File nativeSessionDirectory = new File(getNativeSessionFilesDir(), previousSessionId);

    if (!nativeSessionDirectory.mkdirs()) {
      Logger.getLogger().d(Logger.TAG, "Couldn't create native sessions directory");
      return;
    }

    gzipFile(minidump, new File(nativeSessionDirectory, "minidump"));
    gzipIfNotEmpty(
        NativeFileUtils.binaryImagesJsonFromMapsFile(binaryImages, context),
        new File(nativeSessionDirectory, "binaryImages"));
    gzipFile(metadata, new File(nativeSessionDirectory, "metadata"));
    gzipFile(sessionFile, new File(nativeSessionDirectory, "session"));
    gzipFile(sessionApp, new File(nativeSessionDirectory, "app"));
    gzipFile(sessionDevice, new File(nativeSessionDirectory, "device"));
    gzipFile(sessionOs, new File(nativeSessionDirectory, "os"));
    gzipFile(sessionUser, new File(nativeSessionDirectory, "user"));
    gzipFile(sessionKeys, new File(nativeSessionDirectory, "keys"));
    gzipIfNotEmpty(logs, new File(nativeSessionDirectory, "logs"));

    previousSessionLogManager.clearLog();
  }

  // TODO: Maybe make this a separate collaborator/serializer
  private static void gzipFile(@NonNull File input, @NonNull File output) throws IOException {
    if (!input.exists() || !input.isFile()) {
      return;
    }
    byte[] buffer = new byte[1024];
    FileInputStream fis = null;
    GZIPOutputStream gos = null;
    try {
      fis = new FileInputStream(input);
      gos = new GZIPOutputStream(new FileOutputStream(output));

      int read;

      while ((read = fis.read(buffer)) > 0) {
        gos.write(buffer, 0, read);
      }

      gos.finish();
    } finally {
      CommonUtils.closeQuietly(fis);
      CommonUtils.closeQuietly(gos);
    }
  }

  private static void gzipIfNotEmpty(@Nullable byte[] content, @NonNull File path)
      throws IOException {
    if (content != null && content.length > 0) {
      gzip(content, path);
    }
  }

  private static void gzip(@NonNull byte[] bytes, @NonNull File path) throws IOException {
    GZIPOutputStream gos = null;
    try {
      gos = new GZIPOutputStream(new FileOutputStream(path));
      gos.write(bytes, 0, bytes.length);
      gos.finish();
    } finally {
      CommonUtils.closeQuietly(gos);
    }
  }

  // region Serialization to protobuf

  /**
   * Not synchronized/locked. Must be executed from the single thread executor service used by this
   * class.
   */
  private void writeFatal(Date time, Thread thread, Throwable ex) {
    ClsFileOutputStream fos = null;
    CodedOutputStream cos = null;
    try {
      final String currentSessionId = getCurrentSessionId();

      if (currentSessionId == null) {
        Logger.getLogger()
            .e(Logger.TAG, "Tried to write a fatal exception while no session was open.", null);
        return;
      }

      fos = new ClsFileOutputStream(getFilesDir(), currentSessionId + SESSION_FATAL_TAG);
      cos = CodedOutputStream.newInstance(fos);
      writeSessionEvent(cos, time, thread, ex, EVENT_TYPE_CRASH, true);
    } catch (Exception e) {
      Logger.getLogger().e(Logger.TAG, "An error occurred in the fatal exception logger", e);
    } finally {
      CommonUtils.flushOrLog(cos, "Failed to flush to session begin file.");
      CommonUtils.closeOrLog(fos, "Failed to close fatal exception file output stream.");
    }
  }

  /**
   * Not synchronized/locked. Must be executed from the single thread executor service used by this
   * class.
   */
  private void doWriteNonFatal(Date time, Thread thread, Throwable ex) {
    final String currentSessionId = getCurrentSessionId();

    if (currentSessionId == null) {
      Logger.getLogger()
          .e(Logger.TAG, "Tried to write a non-fatal exception while no session was open.", null);
      return;
    }

    ClsFileOutputStream fos = null;
    CodedOutputStream cos = null;
    try {
      Logger.getLogger()
          .d(
              Logger.TAG,
              "Crashlytics is logging non-fatal exception \""
                  + ex
                  + "\" from thread "
                  + thread.getName());

      final String counterString =
          CommonUtils.padWithZerosToMaxIntWidth(eventCounter.getAndIncrement());
      final String nonFatalFileName = currentSessionId + SESSION_NON_FATAL_TAG + counterString;
      fos = new ClsFileOutputStream(getFilesDir(), nonFatalFileName);

      cos = CodedOutputStream.newInstance(fos);
      writeSessionEvent(cos, time, thread, ex, EVENT_TYPE_LOGGED, false);
    } catch (Exception e) {
      Logger.getLogger().e(Logger.TAG, "An error occurred in the non-fatal exception logger", e);
    } finally {
      CommonUtils.flushOrLog(cos, "Failed to flush to non-fatal file.");
      CommonUtils.closeOrLog(fos, "Failed to close non-fatal file output stream.");
    }

    try {
      // Moved into its own block to ensure that the current ClsFileOutputStream has been
      // closed before we attempt to trim.
      trimSessionEventFiles(currentSessionId, MAX_LOCAL_LOGGED_EXCEPTIONS);
    } catch (Exception e) {
      Logger.getLogger().e(Logger.TAG, "An error occurred when trimming non-fatal files.", e);
    }
  }

  private interface CodedOutputStreamWriteAction {
    void writeTo(CodedOutputStream cos) throws Exception;
  }

  private void writeSessionPartFile(
      String sessionId, String tag, CodedOutputStreamWriteAction writeAction) throws Exception {
    FileOutputStream fos = null;
    CodedOutputStream cos = null;
    try {
      fos = new ClsFileOutputStream(getFilesDir(), sessionId + tag);
      cos = CodedOutputStream.newInstance(fos);
      writeAction.writeTo(cos);
    } finally {
      CommonUtils.flushOrLog(cos, "Failed to flush to session " + tag + " file.");
      CommonUtils.closeOrLog(fos, "Failed to close session " + tag + " file.");
    }
  }

  private static void appendToProtoFile(File file, CodedOutputStreamWriteAction writeAction)
      throws Exception {
    FileOutputStream fos = null;
    CodedOutputStream cos = null;
    try {
      fos = new FileOutputStream(file, true);
      cos = CodedOutputStream.newInstance(fos);
      writeAction.writeTo(cos);
    } finally {
      CommonUtils.flushOrLog(cos, "Failed to flush to append to " + file.getPath());
      CommonUtils.closeOrLog(fos, "Failed to close " + file.getPath());
    }
  }

  private void writeBeginSession(final String sessionId, final Date startedAt) throws Exception {
    final String generator =
        String.format(Locale.US, GENERATOR_FORMAT, CrashlyticsCore.getVersion());
    final long startedAtSeconds = startedAt.getTime() / 1000;

    writeSessionPartFile(
        sessionId,
        SESSION_BEGIN_TAG,
        new CodedOutputStreamWriteAction() {
          @Override
          public void writeTo(CodedOutputStream arg) throws Exception {
            SessionProtobufHelper.writeBeginSession(arg, sessionId, generator, startedAtSeconds);
          }
        });

    nativeComponent.writeBeginSession(sessionId, generator, startedAtSeconds);
  }

  private void writeSessionApp(String sessionId) throws Exception {
    final String appIdentifier = idManager.getAppIdentifier();
    final String versionCode = appData.versionCode;
    final String versionName = appData.versionName;
    final String installUuid = idManager.getCrashlyticsInstallId();
    final int deliveryMechanism =
        DeliveryMechanism.determineFrom(appData.installerPackageName).getId();

    writeSessionPartFile(
        sessionId,
        SESSION_APP_TAG,
        new CodedOutputStreamWriteAction() {
          @Override
          public void writeTo(CodedOutputStream arg) throws Exception {
            SessionProtobufHelper.writeSessionApp(
                arg,
                appIdentifier,
                versionCode,
                versionName,
                installUuid,
                deliveryMechanism,
                unityVersion);
          }
        });

    nativeComponent.writeSessionApp(
        sessionId,
        appIdentifier,
        versionCode,
        versionName,
        installUuid,
        deliveryMechanism,
        unityVersion);
  }

  private void writeSessionOS(String sessionId) throws Exception {
    final String osRelease = VERSION.RELEASE;
    final String osCodeName = VERSION.CODENAME;
    final boolean isRooted = CommonUtils.isRooted(getContext());

    writeSessionPartFile(
        sessionId,
        SESSION_OS_TAG,
        new CodedOutputStreamWriteAction() {
          @Override
          public void writeTo(CodedOutputStream arg) throws Exception {
            SessionProtobufHelper.writeSessionOS(arg, osRelease, osCodeName, isRooted);
          }
        });

    nativeComponent.writeSessionOs(sessionId, osRelease, osCodeName, isRooted);
  }

  @SuppressWarnings("deprecation")
  private void writeSessionDevice(String sessionId) throws Exception {
    final Context context = getContext();
    final StatFs statFs = new StatFs(Environment.getDataDirectory().getPath());

    final int arch = CommonUtils.getCpuArchitectureInt();
    final String model = Build.MODEL;
    final int availableProcessors = Runtime.getRuntime().availableProcessors();
    final long totalRam = CommonUtils.getTotalRamInBytes();
    final long diskSpace = (long) statFs.getBlockCount() * (long) statFs.getBlockSize();
    final boolean isEmulator = CommonUtils.isEmulator(context);
    final int state = CommonUtils.getDeviceState(context);
    final String manufacturer = Build.MANUFACTURER;
    final String modelClass = Build.PRODUCT;

    writeSessionPartFile(
        sessionId,
        SESSION_DEVICE_TAG,
        new CodedOutputStreamWriteAction() {
          @Override
          public void writeTo(CodedOutputStream arg) throws Exception {
            SessionProtobufHelper.writeSessionDevice(
                arg,
                arch,
                model,
                availableProcessors,
                totalRam,
                diskSpace,
                isEmulator,
                state,
                manufacturer,
                modelClass);
          }
        });

    nativeComponent.writeSessionDevice(
        sessionId,
        arch,
        model,
        availableProcessors,
        totalRam,
        diskSpace,
        isEmulator,
        state,
        manufacturer,
        modelClass);
  }

  private void writeSessionUser(String sessionId) throws Exception {
    final UserMetadata metadata = getUserMetadata(sessionId);

    writeSessionPartFile(
        sessionId,
        SESSION_USER_TAG,
        new CodedOutputStreamWriteAction() {
          @Override
          public void writeTo(CodedOutputStream arg) throws Exception {
            SessionProtobufHelper.writeSessionUser(arg, metadata.getUserId(), null, null);
          }
        });
  }

  private void writeSessionEvent(
      CodedOutputStream cos,
      Date time,
      Thread thread,
      Throwable ex,
      String eventType,
      boolean includeAllThreads)
      throws Exception {

    final TrimmedThrowableData trimmedEx = new TrimmedThrowableData(ex, stackTraceTrimmingStrategy);

    final Context context = getContext();
    final long eventTime = time.getTime() / 1000;

    final BatteryState battery = BatteryState.get(context);
    final Float batteryLevel = battery.getBatteryLevel();
    final int batteryVelocity = battery.getBatteryVelocity();

    final boolean proximityEnabled = CommonUtils.getProximitySensorEnabled(context);
    final int orientation = context.getResources().getConfiguration().orientation;
    final long usedRamBytes =
        CommonUtils.getTotalRamInBytes() - CommonUtils.calculateFreeRamInBytes(context);
    final long diskUsedBytes =
        CommonUtils.calculateUsedDiskSpaceInBytes(Environment.getDataDirectory().getPath());

    final RunningAppProcessInfo runningAppProcessInfo =
        CommonUtils.getAppProcessInfo(context.getPackageName(), context);
    final List<StackTraceElement[]> stacks = new LinkedList<>();
    final StackTraceElement[] exceptionStack = trimmedEx.stacktrace;
    final String buildId = appData.buildId;
    final String appIdentifier = idManager.getAppIdentifier();

    Thread[] threads;
    if (includeAllThreads) {
      final Map<Thread, StackTraceElement[]> allStackTraces = Thread.getAllStackTraces();
      threads = new Thread[allStackTraces.size()];
      int i = 0;
      for (Map.Entry<Thread, StackTraceElement[]> entry : allStackTraces.entrySet()) {
        threads[i] = entry.getKey();
        stacks.add(stackTraceTrimmingStrategy.getTrimmedStackTrace(entry.getValue()));
        i++;
      }
    } else {
      // Represents all the threads except the current crashing one, which is handled directly
      // through the thread parameter, so this should be empty.
      threads = new Thread[] {};
    }

    Map<String, String> attributes;
    if (!CommonUtils.getBooleanResourceValue(context, COLLECT_CUSTOM_KEYS, true)) {
      attributes = new TreeMap<String, String>();
    } else {
      attributes = userMetadata.getCustomKeys();
      if (attributes != null && attributes.size() > 1) {
        // alpha sort by keys. The copy constructor uses Map#entrySet(), which is weakly
        // consistent (i.e., provides good enough thread safety) for the ConcurrentHashMap
        // implementation we are using.
        attributes = new TreeMap<String, String>(attributes);
      }
    }

    SessionProtobufHelper.writeSessionEvent(
        cos,
        eventTime,
        eventType,
        trimmedEx,
        thread,
        exceptionStack,
        threads,
        stacks,
        MAX_CHAINED_EXCEPTION_DEPTH,
        attributes,
        logFileManager.getBytesForLog(),
        runningAppProcessInfo,
        orientation,
        appIdentifier,
        buildId,
        batteryLevel,
        batteryVelocity,
        proximityEnabled,
        usedRamBytes,
        diskUsedBytes);

    // clear the log now that we've read it.
    logFileManager.clearLog();
  }

  /**
   * Not synchronized/locked. Must be executed from the single thread executor service used by this
   * class.
   *
   * @param maxLoggedExceptionsCount
   */
  private void writeSessionPartsToSessionFile(
      File sessionBeginFile, String sessionId, int maxLoggedExceptionsCount) {
    Logger.getLogger().d(Logger.TAG, "Collecting session parts for ID " + sessionId);

    final File[] fatalFiles =
        listFilesMatching(new FileNameContainsFilter(sessionId + SESSION_FATAL_TAG));
    final boolean hasFatal = fatalFiles != null && fatalFiles.length > 0;
    Logger.getLogger()
        .d(
            Logger.TAG,
            String.format(Locale.US, "Session %s has fatal exception: %s", sessionId, hasFatal));

    final File[] nonFatalFiles =
        listFilesMatching(new FileNameContainsFilter(sessionId + SESSION_NON_FATAL_TAG));
    final boolean hasNonFatal = nonFatalFiles != null && nonFatalFiles.length > 0;
    Logger.getLogger()
        .d(
            Logger.TAG,
            String.format(
                Locale.US, "Session %s has non-fatal exceptions: %s", sessionId, hasNonFatal));

    if (hasFatal || hasNonFatal) {
      final File[] trimmedNonFatalFiles =
          getTrimmedNonFatalFiles(sessionId, nonFatalFiles, maxLoggedExceptionsCount);
      final File fatalFile = hasFatal ? fatalFiles[0] : null;
      synthesizeSessionFile(sessionBeginFile, sessionId, trimmedNonFatalFiles, fatalFile);
    } else {
      Logger.getLogger().d(Logger.TAG, "No events present for session ID " + sessionId);
    }

    Logger.getLogger().d(Logger.TAG, "Removing session part files for ID " + sessionId);
    deleteSessionPartFilesFor(sessionId);
  }

  /** Synthesize all session data into the final session file for submission. */
  private void synthesizeSessionFile(
      File sessionBeginFile, String sessionId, File[] nonFatalFiles, File fatalFile) {
    final boolean hasFatal = fatalFile != null;
    boolean exceptionDuringWrite = false;

    final File outputDir = hasFatal ? getFatalSessionFilesDir() : getNonFatalSessionFilesDir();
    if (!outputDir.exists()) {
      outputDir.mkdirs();
    }
    ClsFileOutputStream fos = null;
    CodedOutputStream cos = null;
    try {
      fos = new ClsFileOutputStream(outputDir, sessionId);
      cos = CodedOutputStream.newInstance(fos);

      Logger.getLogger().d(Logger.TAG, "Collecting SessionStart data for session ID " + sessionId);
      writeToCosFromFile(cos, sessionBeginFile);

      cos.writeUInt64(4, new Date().getTime() / 1000);
      cos.writeBool(5, hasFatal);

      cos.writeUInt32(11, ANALYZER_VERSION);
      // GeneratorType ANDROID_SDK = 3;
      cos.writeEnum(12, 3);

      writeInitialPartsTo(cos, sessionId);

      writeNonFatalEventsTo(cos, nonFatalFiles, sessionId);

      if (hasFatal) {
        writeToCosFromFile(cos, fatalFile);
      }
    } catch (Exception e) {
      Logger.getLogger()
          .e(Logger.TAG, "Failed to write session file for session ID: " + sessionId, e);
      // Need to set this ugly flag because we can't close the CFOS before we flush the
      // COS.
      exceptionDuringWrite = true;
    } finally {
      // Make sure the COS is flushed so that we get partial data at the very minimum.
      CommonUtils.flushOrLog(cos, "Error flushing session file stream");

      if (exceptionDuringWrite) {
        // If we have an error, we're going to close the stream without copying it to
        // its finalized file.
        closeWithoutRenamingOrLog(fos);
      } else {
        // No exception - allow the normal close to copy the file to its intended
        // location
        CommonUtils.closeOrLog(fos, "Failed to close CLS file");
      }
    }
  }

  /**
   * Copies the set of non-fatal files into the given CodedOutputStream. This is a no-op if <code>
   * nonFatalFiles</code> is empty.
   */
  private static void writeNonFatalEventsTo(
      CodedOutputStream cos, File[] nonFatalFiles, String sessionId) {
    Arrays.sort(nonFatalFiles, CommonUtils.FILE_MODIFIED_COMPARATOR);

    for (File nonFatalFile : nonFatalFiles) {
      try {
        Logger.getLogger()
            .d(
                Logger.TAG,
                String.format(
                    Locale.US,
                    "Found Non Fatal for session ID %s in %s ",
                    sessionId,
                    nonFatalFile.getName()));
        writeToCosFromFile(cos, nonFatalFile);
      } catch (Exception e) {
        Logger.getLogger().e(Logger.TAG, "Error writting non-fatal to session.", e);
      }
    }
  }

  private void writeInitialPartsTo(CodedOutputStream cos, String sessionId) throws IOException {
    for (String tag : INITIAL_SESSION_PART_TAGS) {
      final File[] sessionPartFiles =
          listFilesMatching(new FileNameContainsFilter(sessionId + tag + SESSION_FILE_EXTENSION));

      if (sessionPartFiles.length == 0) {
        Logger.getLogger()
            .e(Logger.TAG, "Can't find " + tag + " data for session ID " + sessionId, null);
      } else {
        Logger.getLogger().d(Logger.TAG, "Collecting " + tag + " data for session ID " + sessionId);
        writeToCosFromFile(cos, sessionPartFiles[0]);
      }
    }
  }

  private static void appendOrganizationIdToSessionFile(String organizationId, File file)
      throws Exception {
    appendToProtoFile(
        file,
        new CodedOutputStreamWriteAction() {
          @Override
          public void writeTo(CodedOutputStream cos) throws Exception {
            SessionProtobufHelper.writeSessionAppClsId(cos, organizationId);
          }
        });
  }

  /**
   * Not synchronized/locked. Must be executed from the single thread executor service used by this
   * class.
   */
  private static void writeToCosFromFile(CodedOutputStream cos, File file) throws IOException {
    if (!file.exists()) {
      Logger.getLogger()
          .e(Logger.TAG, "Tried to include a file that doesn't exist: " + file.getName(), null);
      return;
    }

    FileInputStream fis = null;
    try {
      fis = new FileInputStream(file);
      // TODO: MW 2015-10-28 Copy the file in chunks instead of all at once.
      copyToCodedOutputStream(fis, cos, (int) file.length());
    } finally {
      CommonUtils.closeOrLog(fis, "Failed to close file input stream.");
    }
  }

  private static void copyToCodedOutputStream(
      InputStream inStream, CodedOutputStream cos, int bufferLength) throws IOException {
    final byte[] buffer = new byte[bufferLength];
    int offset = 0;
    int numRead;

    while (offset < buffer.length
        && (numRead = inStream.read(buffer, offset, buffer.length - offset)) >= 0) {
      offset += numRead;
    }

    cos.writeRawBytes(buffer);
  }

  // endregion

  // region Utilities

  UserMetadata getUserMetadata() {
    return userMetadata;
  }

  /**
   * Get the appropriate user metadata for inclusion with the given session report. In the case that
   * we're safely handling a JVM crash, use the latest values in memory. Otherwise, restore the
   * metadata from disk.
   */
  private UserMetadata getUserMetadata(String sessionId) {
    return isHandlingException()
        ? userMetadata
        : new MetaDataStore(getFilesDir()).readUserData(sessionId);
  }

  boolean isHandlingException() {
    return crashHandler != null && crashHandler.isHandlingException();
  }

  File getFilesDir() {
    return fileStore.getFilesDir();
  }

  File getNativeSessionFilesDir() {
    return new File(getFilesDir(), NATIVE_SESSION_DIR);
  }

  File getFatalSessionFilesDir() {
    return new File(getFilesDir(), FATAL_SESSION_DIR);
  }

  File getNonFatalSessionFilesDir() {
    return new File(getFilesDir(), NONFATAL_SESSION_DIR);
  }

  void registerBreadcrumbsReceiver() {
    final boolean breadcrumbsRegistered = breadcrumbsReceiver.register();
    Logger.getLogger()
        .d(
            Logger.TAG,
            "Registered Firebase Analytics event listener for breadcrumbs: "
                + breadcrumbsRegistered);
  }

  private CreateReportSpiCall getCreateReportSpiCall(String reportsUrl, String ndkReportsUrl) {
    final Context context = getContext();
    final String overriddenHost =
        CommonUtils.getStringsFileValue(context, CRASHLYTICS_API_ENDPOINT);

    final DefaultCreateReportSpiCall defaultCreateReportSpiCall =
        new DefaultCreateReportSpiCall(
            overriddenHost, reportsUrl, httpRequestFactory, CrashlyticsCore.getVersion());

    final NativeCreateReportSpiCall nativeCreateReportSpiCall =
        new NativeCreateReportSpiCall(
            overriddenHost, ndkReportsUrl, httpRequestFactory, CrashlyticsCore.getVersion());

    return new CompositeCreateReportSpiCall(defaultCreateReportSpiCall, nativeCreateReportSpiCall);
  }

  private void sendSessionReports(AppSettingsData appSettings, boolean dataCollectionToken)
      throws Exception {
    final Context context = getContext();
    final ReportUploader reportUploader = reportUploaderProvider.createReportUploader(appSettings);
    for (File finishedSessionFile : listCompleteSessionFiles()) {
      appendOrganizationIdToSessionFile(appSettings.organizationId, finishedSessionFile);
      final Report report = new SessionReport(finishedSessionFile, SEND_AT_CRASHTIME_HEADER);
      backgroundWorker.submit(
          new SendReportRunnable(context, report, reportUploader, dataCollectionToken));
    }
  }

  private void recordFatalFirebaseEvent(long timestamp) {
    if (firebaseCrashExists()) {
      Logger.getLogger()
          .d(Logger.TAG, "Skipping logging Crashlytics event to Firebase, FirebaseCrash exists");
      return;
    }

    if (analyticsConnector != null) {
      Logger.getLogger().d(Logger.TAG, "Logging Crashlytics event to Firebase");
      final Bundle params = new Bundle();
      params.putInt(FIREBASE_CRASH_TYPE, FIREBASE_CRASH_TYPE_FATAL);
      params.putLong(FIREBASE_TIMESTAMP, timestamp);
      analyticsConnector.logEvent(
          FIREBASE_ANALYTICS_ORIGIN_CRASHLYTICS, FIREBASE_APPLICATION_EXCEPTION, params);
    } else {
      Logger.getLogger()
          .d(Logger.TAG, "Skipping logging Crashlytics event to Firebase, no Firebase Analytics");
    }
  }

  private boolean firebaseCrashExists() {
    try {
      final Class clazz = Class.forName("com.google.firebase.crash.FirebaseCrash");
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  private final class ReportUploaderHandlingExceptionCheck
      implements ReportUploader.HandlingExceptionCheck {
    @Override
    public boolean isHandlingException() {
      return CrashlyticsController.this.isHandlingException();
    }
  }

  private final class ReportUploaderFilesProvider implements ReportUploader.ReportFilesProvider {
    @Override
    public File[] getCompleteSessionFiles() {
      return listCompleteSessionFiles();
    }

    @Override
    public File[] getNativeReportFiles() {
      return listNativeSessionFileDirectories();
    }
  }

  // TODO: Remove this class and use uploadReportsAsync instead.
  private static final class SendReportRunnable implements Runnable {

    private final Context context;
    private final Report report;
    private final ReportUploader reportUploader;
    private final boolean dataCollectionToken;

    public SendReportRunnable(
        Context context,
        Report report,
        ReportUploader reportUploader,
        boolean dataCollectionToken) {
      this.context = context;
      this.report = report;
      this.reportUploader = reportUploader;
      this.dataCollectionToken = dataCollectionToken;
    }

    @Override
    public void run() {
      if (!CommonUtils.canTryConnection(context)) {
        return;
      }

      Logger.getLogger().d(Logger.TAG, "Attempting to send crash report at time of crash...");

      reportUploader.uploadReport(report, dataCollectionToken);
    }
  }

  private static final class LogFileDirectoryProvider implements LogFileManager.DirectoryProvider {

    private static final String LOG_FILES_DIR = "log-files";

    private final FileStore rootFileStore;

    public LogFileDirectoryProvider(FileStore rootFileStore) {
      this.rootFileStore = rootFileStore;
    }

    @Override
    public File getLogFileDir() {
      final File logFileDir = new File(rootFileStore.getFilesDir(), LOG_FILES_DIR);
      if (!logFileDir.exists()) {
        logFileDir.mkdirs();
      }
      return logFileDir;
    }
  }

  // endregion
}
