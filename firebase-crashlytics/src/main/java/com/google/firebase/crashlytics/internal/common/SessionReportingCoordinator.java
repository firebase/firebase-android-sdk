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

import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.concurrent.Executor;

/**
 * This class handles Crashlytics lifecycle events and coordinates session data capture and
 * persistence, as well as sending of reports to Firebase Crashlytics.
 */
public class SessionReportingCoordinator implements CrashlyticsLifecycleEvents {

  private static final String EVENT_TYPE_CRASH = "crash";
  private static final String EVENT_TYPE_LOGGED = "error";
  private static final int EVENT_THREAD_IMPORTANCE = 4;
  private static final int MAX_CHAINED_EXCEPTION_DEPTH = 8;
  private static final int DEFAULT_BUFFER_SIZE = 8192;

  public static SessionReportingCoordinator create(
      Context context,
      IdManager idManager,
      FileStore fileStore,
      AppData appData,
      LogFileManager logFileManager,
      UserMetadata userMetadata,
      StackTraceTrimmingStrategy stackTraceTrimmingStrategy,
      SettingsDataProvider settingsProvider) {
    final CrashlyticsReportDataCapture dataCapture =
        new CrashlyticsReportDataCapture(context, idManager, appData, stackTraceTrimmingStrategy);
    final CrashlyticsReportPersistence reportPersistence =
        new CrashlyticsReportPersistence(fileStore, settingsProvider);
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
  public void onBeginSession(@NonNull String sessionId, long timestampSeconds) {
    final CrashlyticsReport capturedReport =
        dataCapture.captureReportData(sessionId, timestampSeconds);

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
    Logger.getLogger().v("Persisting fatal event for session " + sessionId);
    persistEvent(event, thread, sessionId, EVENT_TYPE_CRASH, timestamp, true);
  }

  public void persistNonFatalEvent(
      @NonNull Throwable event, @NonNull Thread thread, @NonNull String sessionId, long timestamp) {
    Logger.getLogger().v("Persisting non-fatal event for session " + sessionId);
    persistEvent(event, thread, sessionId, EVENT_TYPE_LOGGED, timestamp, false);
  }

  @RequiresApi(api = Build.VERSION_CODES.R)
  public void persistRelevantAppExitInfoEvent(
      String sessionId,
      List<ApplicationExitInfo> applicationExitInfoList,
      LogFileManager logFileManagerForSession,
      UserMetadata userMetadataForSession) {

    ApplicationExitInfo relevantApplicationExitInfo =
        findRelevantApplicationExitInfo(sessionId, applicationExitInfoList);

    if (relevantApplicationExitInfo == null) {
      Logger.getLogger().v("No relevant ApplicationExitInfo occurred during session: " + sessionId);
      return;
    }

    final CrashlyticsReport.Session.Event capturedEvent =
        dataCapture.captureAnrEventData(convertApplicationExitInfo(relevantApplicationExitInfo));

    Logger.getLogger().d("Persisting anr for session " + sessionId);
    reportPersistence.persistEvent(
        addLogsAndCustomKeysToEvent(
            capturedEvent, logFileManagerForSession, userMetadataForSession),
        sessionId,
        true);
  }

  public void finalizeSessionWithNativeEvent(
      @NonNull String sessionId, @NonNull List<NativeSessionFile> nativeSessionFiles) {

    Logger.getLogger().d("SessionReportingCoordinator#finalizeSessionWithNativeEvent");

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
      Logger.getLogger().v("Could not persist user ID; no user ID available");
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

  public SortedSet<String> listSortedOpenSessionIds() {
    return reportPersistence.getOpenSessionIds();
  }

  public boolean hasReportsToSend() {
    return reportPersistence.hasFinalizedReports();
  }

  public void removeAllReports() {
    reportPersistence.deleteAllReports();
  }

  /**
   * Send all finalized reports.
   *
   * @param reportSendCompleteExecutor executor on which to run report cleanup after each report is
   *     sent.
   */
  public Task<Void> sendReports(@NonNull Executor reportSendCompleteExecutor) {
    final List<CrashlyticsReportWithSessionId> reportsToSend =
        reportPersistence.loadFinalizedReports();
    final List<Task<Boolean>> sendTasks = new ArrayList<>();
    for (CrashlyticsReportWithSessionId reportToSend : reportsToSend) {
      sendTasks.add(
          reportsSender
              .sendReport(reportToSend)
              .continueWith(reportSendCompleteExecutor, this::onReportSendComplete));
    }
    return Tasks.whenAll(sendTasks);
  }

  private CrashlyticsReport.Session.Event addLogsAndCustomKeysToEvent(
      CrashlyticsReport.Session.Event capturedEvent) {
    return addLogsAndCustomKeysToEvent(capturedEvent, logFileManager, reportMetadata);
  }

  private CrashlyticsReport.Session.Event addLogsAndCustomKeysToEvent(
      CrashlyticsReport.Session.Event capturedEvent,
      LogFileManager logFileManager,
      UserMetadata reportMetadata) {
    final CrashlyticsReport.Session.Event.Builder eventBuilder = capturedEvent.toBuilder();
    final String content = logFileManager.getLogString();

    if (content != null) {
      eventBuilder.setLog(
          CrashlyticsReport.Session.Event.Log.builder().setContent(content).build());
    } else {
      Logger.getLogger().v("No log data to include with this event.");
    }

    // TODO: Put this back once support for reports endpoint is removed.
    // logFileManager.clearLog(); // Clear log to prepare for next event.

    final List<CustomAttribute> sortedCustomAttributes =
        getSortedCustomAttributes(reportMetadata.getCustomKeys());
    final List<CustomAttribute> sortedInternalKeys =
        getSortedCustomAttributes(reportMetadata.getInternalKeys());

    if (!sortedCustomAttributes.isEmpty()) {
      eventBuilder.setApp(
          capturedEvent.getApp().toBuilder()
              .setCustomAttributes(ImmutableList.from(sortedCustomAttributes))
              .setInternalKeys(ImmutableList.from(sortedInternalKeys))
              .build());
    }

    return eventBuilder.build();
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

    reportPersistence.persistEvent(
        addLogsAndCustomKeysToEvent(capturedEvent), sessionId, isHighPriority);
  }

  private boolean onReportSendComplete(@NonNull Task<CrashlyticsReportWithSessionId> task) {
    if (task.isSuccessful()) {
      CrashlyticsReportWithSessionId report = task.getResult();
      Logger.getLogger()
          .d("Crashlytics report successfully enqueued to DataTransport: " + report.getSessionId());
      File reportFile = report.getReportFile();
      if (reportFile.delete()) {
        Logger.getLogger().d("Deleted report file: " + reportFile.getPath());
      } else {
        Logger.getLogger().w("Crashlytics could not delete report file: " + reportFile.getPath());
      }
      return true;
    }
    Logger.getLogger()
        .w("Crashlytics report could not be enqueued to DataTransport", task.getException());
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

  @RequiresApi(api = Build.VERSION_CODES.R)
  private static CrashlyticsReport.ApplicationExitInfo convertApplicationExitInfo(
      ApplicationExitInfo applicationExitInfo) {
    String traceFile = null;
    try {
      InputStream traceInputStream = applicationExitInfo.getTraceInputStream();
      if (traceInputStream != null) {
        traceFile = convertInputStreamToString(traceInputStream);
      }
    } catch (IOException e) {
      Logger.getLogger()
          .w(
              "Could not get input trace in application exit info: "
                  + applicationExitInfo.toString()
                  + " Error: "
                  + e);
    }

    return CrashlyticsReport.ApplicationExitInfo.builder()
        .setImportance(applicationExitInfo.getImportance())
        .setProcessName(applicationExitInfo.getProcessName())
        .setReasonCode(applicationExitInfo.getReason())
        .setTimestamp(applicationExitInfo.getTimestamp())
        .setPid(applicationExitInfo.getPid())
        .setPss(applicationExitInfo.getPss())
        .setRss(applicationExitInfo.getRss())
        .setTraceFile(traceFile)
        .build();
  }

  @VisibleForTesting
  @RequiresApi(api = Build.VERSION_CODES.KITKAT)
  public static String convertInputStreamToString(InputStream inputStream) throws IOException {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    byte[] bytes = new byte[DEFAULT_BUFFER_SIZE];
    int length;
    while ((length = inputStream.read(bytes)) != -1) {
      byteArrayOutputStream.write(bytes, 0, length);
    }
    return byteArrayOutputStream.toString(StandardCharsets.UTF_8.name());
  }

  /** Finds the first ANR ApplicationExitInfo within the session. */
  @RequiresApi(api = Build.VERSION_CODES.R)
  private @Nullable ApplicationExitInfo findRelevantApplicationExitInfo(
      String sessionId, List<ApplicationExitInfo> applicationExitInfoList) {
    long sessionStartTime = reportPersistence.getStartTimestampMillis(sessionId);
    ApplicationExitInfo alternateApplicationExitInfo = null;

    // The order of ApplicationExitInfos is latest first.
    // Java For-each preserves the order.
    for (ApplicationExitInfo applicationExitInfo : applicationExitInfoList) {
      // ApplicationExitInfo did not occur during the session.
      if (applicationExitInfo.getTimestamp() < sessionStartTime) {
        return null;
      }

      // If the ApplicationExitInfo is not an ANR, but it was within the session, loop through
      // all ApplicationExitInfos that fall within the session.
      if (applicationExitInfo.getReason() != ApplicationExitInfo.REASON_ANR) {
        try {
          // There are cases where the process gets an ANR but recovers, and dies for another reason
          // later, and the stack traces will be included in the record. If it's available, we'll
          // consider
          // this
          // ApplicationExitInfo to be an ANR if an ApplicationExitInfo with REASON_ANR isn't
          // available.
          if (alternateApplicationExitInfo == null
              // Exclude REASON_CRASH_NATIVE because the format of TraceInputStream isn't identical
              // to the format for REASON_ANR.
              && applicationExitInfo.getReason() != ApplicationExitInfo.REASON_CRASH_NATIVE
              && applicationExitInfo.getTraceInputStream() != null) {
            alternateApplicationExitInfo = applicationExitInfo;
          }
        } catch (IOException ignored) {
        }

        continue;
      }

      return applicationExitInfo;
    }

    return alternateApplicationExitInfo;
  }
}
