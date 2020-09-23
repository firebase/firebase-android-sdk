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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.crashlytics.internal.Logger;
import com.google.firebase.crashlytics.internal.log.LogFileManager;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport.CustomAttribute;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport.FilesPayload;
import com.google.firebase.crashlytics.internal.model.ImmutableList;
import com.google.firebase.crashlytics.internal.persistence.CrashlyticsReportPersistence;
import com.google.firebase.crashlytics.internal.persistence.FileStore;
import com.google.firebase.crashlytics.internal.send.DataTransportCrashlyticsReportSender;
import com.google.firebase.crashlytics.internal.settings.SettingsDataProvider;
import com.google.firebase.crashlytics.internal.stacktrace.StackTraceTrimmingStrategy;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * This class handles Crashlytics lifecycle events and coordinates session data capture and
 * persistence, as well as sending of reports to Firebase Crashlytics.
 */
class SessionReportingCoordinator implements CrashlyticsLifecycleEvents {

  private static final String EVENT_TYPE_CRASH = "crash";
  private static final String EVENT_TYPE_LOGGED = "error";
  private static final int EVENT_THREAD_IMPORTANCE = 4;
  private static final int MAX_CHAINED_EXCEPTION_DEPTH = 8;

  public static SessionReportingCoordinator create(
      Context context,
      IdManager idManager,
      FileStore fileStore,
      AppData appData,
      LogFileManager logFileManager,
      UserMetadata userMetadata,
      StackTraceTrimmingStrategy stackTraceTrimmingStrategy,
      SettingsDataProvider settingsProvider) {
    final File rootFilesDirectory = new File(fileStore.getFilesDirPath());
    final CrashlyticsReportDataCapture dataCapture =
        new CrashlyticsReportDataCapture(context, idManager, appData, stackTraceTrimmingStrategy);
    final CrashlyticsReportPersistence reportPersistence =
        new CrashlyticsReportPersistence(rootFilesDirectory, settingsProvider);
    final DataTransportCrashlyticsReportSender reportSender =
        DataTransportCrashlyticsReportSender.create(context);
    return new SessionReportingCoordinator(
        dataCapture, reportPersistence, reportSender, logFileManager, userMetadata);
  }

  private final CrashlyticsReportDataCapture dataCapture;
  private final CrashlyticsReportPersistence reportPersistence;
  private final DataTransportCrashlyticsReportSender reportsSender;
  private final LogFileManager logFileManager;
  private final UserMetadata reportMetadata;

  SessionReportingCoordinator(
      CrashlyticsReportDataCapture dataCapture,
      CrashlyticsReportPersistence reportPersistence,
      DataTransportCrashlyticsReportSender reportsSender,
      LogFileManager logFileManager,
      UserMetadata reportMetadata) {
    this.dataCapture = dataCapture;
    this.reportPersistence = reportPersistence;
    this.reportsSender = reportsSender;
    this.logFileManager = logFileManager;
    this.reportMetadata = reportMetadata;
  }

  @Override
  public void onBeginSession(@NonNull String sessionId, long timestamp) {
    final CrashlyticsReport capturedReport = dataCapture.captureReportData(sessionId, timestamp);

    reportPersistence.persistReport(capturedReport);
  }

  @Override
  public void onLog(long timestamp, String log) {
    logFileManager.writeToLog(timestamp, log);
  }

  @Override
  public void onCustomKey(String key, String value) {
    reportMetadata.setCustomKey(key, value);
  }

  @Override
  public void onUserId(String userId) {
    reportMetadata.setUserId(userId);
  }

  public void persistFatalEvent(
      @NonNull Throwable event, @NonNull Thread thread, @NonNull String sessionId, long timestamp) {
    Logger.getLogger().d("Persisting fatal event for session " + sessionId);
    persistEvent(event, thread, sessionId, EVENT_TYPE_CRASH, timestamp, true);
  }

  public void persistNonFatalEvent(
      @NonNull Throwable event, @NonNull Thread thread, @NonNull String sessionId, long timestamp) {
    Logger.getLogger().d("Persisting non-fatal event for session " + sessionId);
    persistEvent(event, thread, sessionId, EVENT_TYPE_LOGGED, timestamp, false);
  }

  public void finalizeSessionWithNativeEvent(
      @NonNull String sessionId, @NonNull List<NativeSessionFile> nativeSessionFiles) {
    ArrayList<FilesPayload.File> nativeFiles = new ArrayList<>();
    for (NativeSessionFile nativeSessionFile : nativeSessionFiles) {
      FilesPayload.File filePayload = nativeSessionFile.asFilePayload();
      if (filePayload != null) {
        nativeFiles.add(filePayload);
      }
    }

    reportPersistence.finalizeSessionWithNativeEvent(
        sessionId, FilesPayload.builder().setFiles(ImmutableList.from(nativeFiles)).build());
  }

  public void persistUserId(@NonNull String sessionId) {
    final String userId = reportMetadata.getUserId();
    if (userId == null) {
      Logger.getLogger().d("Could not persist user ID; no user ID available");
      return;
    }
    reportPersistence.persistUserIdForSession(userId, sessionId);
  }

  /**
   * Creates finalized reports for all sessions besides the given session. If the given session is
   * null, all sessions will be finalized.
   */
  public void finalizeSessions(long timestamp, @Nullable String currentSessionId) {
    reportPersistence.finalizeReports(currentSessionId, timestamp);
  }

  public void removeAllReports() {
    reportPersistence.deleteAllReports();
  }

  /**
   * Send all finalized reports.
   *
   * @param reportSendCompleteExecutor executor on which to run report cleanup after each report is
   *     sent.
   * @param dataTransportState used to determine whether to send the report before cleaning it up.
   */
  Task<Void> sendReports(
      @NonNull Executor reportSendCompleteExecutor,
      @NonNull DataTransportState dataTransportState) {
    if (dataTransportState == DataTransportState.NONE) {
      Logger.getLogger().d("Send via DataTransport disabled. Removing DataTransport reports.");
      reportPersistence.deleteAllReports();
      return Tasks.forResult(null);
    }
    final List<CrashlyticsReportWithSessionId> reportsToSend =
        reportPersistence.loadFinalizedReports();
    final List<Task<Boolean>> sendTasks = new ArrayList<>();
    for (CrashlyticsReportWithSessionId reportToSend : reportsToSend) {
      if (reportToSend.getReport().getType() == CrashlyticsReport.Type.NATIVE
          && dataTransportState != DataTransportState.ALL) {
        Logger.getLogger()
            .d("Send native reports via DataTransport disabled. Removing DataTransport reports.");
        reportPersistence.deleteFinalizedReport(reportToSend.getSessionId());
        continue;
      }

      sendTasks.add(
          reportsSender
              .sendReport(reportToSend)
              .continueWith(reportSendCompleteExecutor, this::onReportSendComplete));
    }
    return Tasks.whenAll(sendTasks);
  }

  private void persistEvent(
      @NonNull Throwable event,
      @NonNull Thread thread,
      @NonNull String sessionId,
      @NonNull String eventType,
      long timestamp,
      boolean includeAllThreads) {

    final boolean isHighPriority = eventType.equals(EVENT_TYPE_CRASH);

    final CrashlyticsReport.Session.Event capturedEvent =
        dataCapture.captureEventData(
            event,
            thread,
            eventType,
            timestamp,
            EVENT_THREAD_IMPORTANCE,
            MAX_CHAINED_EXCEPTION_DEPTH,
            includeAllThreads);

    final CrashlyticsReport.Session.Event.Builder eventBuilder = capturedEvent.toBuilder();

    final String content = logFileManager.getLogString();

    if (content != null) {
      eventBuilder.setLog(
          CrashlyticsReport.Session.Event.Log.builder().setContent(content).build());
    } else {
      Logger.getLogger().d("No log data to include with this event.");
    }

    // TODO: Put this back once support for reports endpoint is removed.
    // logFileManager.clearLog(); // Clear log to prepare for next event.

    final List<CustomAttribute> sortedCustomAttributes =
        getSortedCustomAttributes(reportMetadata.getCustomKeys());

    if (!sortedCustomAttributes.isEmpty()) {
      eventBuilder.setApp(
          capturedEvent
              .getApp()
              .toBuilder()
              .setCustomAttributes(ImmutableList.from(sortedCustomAttributes))
              .build());
    }

    reportPersistence.persistEvent(eventBuilder.build(), sessionId, isHighPriority);
  }

  private boolean onReportSendComplete(@NonNull Task<CrashlyticsReportWithSessionId> task) {
    if (task.isSuccessful()) {
      // TODO: Consolidate sending analytics event here, which will capture both native and
      // non-native fatal reports
      final CrashlyticsReportWithSessionId report = task.getResult();
      Logger.getLogger()
          .d("Crashlytics report successfully enqueued to DataTransport: " + report.getSessionId());
      reportPersistence.deleteFinalizedReport(report.getSessionId());
      return true;
    }
    Logger.getLogger()
        .d("Crashlytics report could not be enqueued to DataTransport", task.getException());
    return false;
  }

  @NonNull
  private static List<CustomAttribute> getSortedCustomAttributes(
      @NonNull Map<String, String> attributes) {
    ArrayList<CustomAttribute> attributesList = new ArrayList<>();
    attributesList.ensureCapacity(attributes.size());
    for (Map.Entry<String, String> entry : attributes.entrySet()) {
      attributesList.add(
          CustomAttribute.builder().setKey(entry.getKey()).setValue(entry.getValue()).build());
    }

    // Sort by key
    Collections.sort(
        attributesList,
        (CustomAttribute attr1, CustomAttribute attr2) -> attr1.getKey().compareTo(attr2.getKey()));

    return attributesList;
  }
}
