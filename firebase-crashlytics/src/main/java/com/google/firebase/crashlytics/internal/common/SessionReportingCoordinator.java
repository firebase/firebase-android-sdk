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
import com.google.firebase.crashlytics.internal.metadata.LogFileManager;
import com.google.firebase.crashlytics.internal.metadata.UserMetadata;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport.CustomAttribute;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport.FilesPayload;
import com.google.firebase.crashlytics.internal.persistence.CrashlyticsReportPersistence;
import com.google.firebase.crashlytics.internal.persistence.FileStore;
import com.google.firebase.crashlytics.internal.send.DataTransportCrashlyticsReportSender;
import com.google.firebase.crashlytics.internal.settings.SettingsProvider;
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
      SettingsProvider settingsProvider,
      OnDemandCounter onDemandCounter,
      CrashlyticsAppQualitySessionsSubscriber sessionsSubscriber) {
    final CrashlyticsReportDataCapture dataCapture =
        new CrashlyticsReportDataCapture(
            context, idManager, appData, stackTraceTrimmingStrategy, settingsProvider);
    final CrashlyticsReportPersistence reportPersistence =
        new CrashlyticsReportPersistence(fileStore, settingsProvider, sessionsSubscriber);
    final DataTransportCrashlyticsReportSender reportSender =
        DataTransportCrashlyticsReportSender.create(context, settingsProvider, onDemandCounter);
    return new SessionReportingCoordinator(
        dataCapture, reportPersistence, reportSender, logFileManager, userMetadata, idManager);
  }

  private final CrashlyticsReportDataCapture dataCapture;
  private final CrashlyticsReportPersistence reportPersistence;
  private final DataTransportCrashlyticsReportSender reportsSender;
  private final LogFileManager logFileManager;
  private final UserMetadata reportMetadata;
  private final IdManager idManager;

  SessionReportingCoordinator(
      CrashlyticsReportDataCapture dataCapture,
      CrashlyticsReportPersistence reportPersistence,
      DataTransportCrashlyticsReportSender reportsSender,
      LogFileManager logFileManager,
      UserMetadata reportMetadata,
      IdManager idManager) {
    this.dataCapture = dataCapture;
    this.reportPersistence = reportPersistence;
    this.reportsSender = reportsSender;
    this.logFileManager = logFileManager;
    this.reportMetadata = reportMetadata;
    this.idManager = idManager;
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

    CrashlyticsReport.Session.Event eventWithLogsAndCustomKeys =
        addLogsAndCustomKeysToEvent(
            capturedEvent, logFileManagerForSession, userMetadataForSession);
    CrashlyticsReport.Session.Event eventWithRolloutsState =
        addRolloutsStateToEvent(eventWithLogsAndCustomKeys, userMetadataForSession);
    reportPersistence.persistEvent(eventWithRolloutsState, sessionId, true);
  }

  public void finalizeSessionWithNativeEvent(
      @NonNull String sessionId,
      @NonNull List<NativeSessionFile> nativeSessionFiles,
      CrashlyticsReport.ApplicationExitInfo applicationExitInfo) {

    Logger.getLogger().d("SessionReportingCoordinator#finalizeSessionWithNativeEvent");

    ArrayList<FilesPayload.File> nativeFiles = new ArrayList<>();
    for (NativeSessionFile nativeSessionFile : nativeSessionFiles) {
      FilesPayload.File filePayload = nativeSessionFile.asFilePayload();
      if (filePayload != null) {
        nativeFiles.add(filePayload);
      }
    }

    reportPersistence.finalizeSessionWithNativeEvent(
        sessionId,
        FilesPayload.builder().setFiles(Collections.unmodifiableList(nativeFiles)).build(),
        applicationExitInfo);
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
    return sendReports(reportSendCompleteExecutor, /* sessionId= */ null);
  }

  public Task<Void> sendReports(
      @NonNull Executor reportSendCompleteExecutor, @Nullable String sessionId) {
    final List<CrashlyticsReportWithSessionId> reportsToSend =
        reportPersistence.loadFinalizedReports();
    final List<Task<Boolean>> sendTasks = new ArrayList<>();
    for (CrashlyticsReportWithSessionId reportToSend : reportsToSend) {
      if (sessionId == null || sessionId.equals(reportToSend.getSessionId())) {
        sendTasks.add(
            reportsSender
                .enqueueReport(ensureHasFid(reportToSend), sessionId != null)
                .continueWith(reportSendCompleteExecutor, this::onReportSendComplete));
      }
    }
    return Tasks.whenAll(sendTasks);
  }

  /**
   * Ensure reportToSend has a populated fid and auth token.
   *
   * <p>This is needed because it's possible to capture reports while data collection is disabled,
   * and then upload the report later by calling sendUnsentReports or enabling data collection.
   */
  private CrashlyticsReportWithSessionId ensureHasFid(CrashlyticsReportWithSessionId reportToSend) {
    // Only do the update if the fid or auth token is already missing from the report.
    if (reportToSend.getReport().getFirebaseInstallationId() == null
        || reportToSend.getReport().getFirebaseAuthenticationToken() == null) {
      // Fetch the true fid, regardless of automatic data collection since it's uploading.
      FirebaseInstallationId firebaseInstallationId = idManager.fetchTrueFid(/* validate= */ true);
      return CrashlyticsReportWithSessionId.create(
          reportToSend
              .getReport()
              .withFirebaseInstallationId(firebaseInstallationId.getFid())
              .withFirebaseAuthenticationToken(firebaseInstallationId.getAuthToken()),
          reportToSend.getSessionId(),
          reportToSend.getReportFile());
    }

    return reportToSend;
  }

  private CrashlyticsReport.Session.Event addMetaDataToEvent(
      CrashlyticsReport.Session.Event capturedEvent) {
    CrashlyticsReport.Session.Event eventWithLogsAndCustomKeys =
        addLogsAndCustomKeysToEvent(capturedEvent, logFileManager, reportMetadata);
    CrashlyticsReport.Session.Event eventWithRollouts =
        addRolloutsStateToEvent(eventWithLogsAndCustomKeys, reportMetadata);
    return eventWithRollouts;
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

    if (!sortedCustomAttributes.isEmpty() || !sortedInternalKeys.isEmpty()) {
      eventBuilder.setApp(
          capturedEvent.getApp().toBuilder()
              .setCustomAttributes(sortedCustomAttributes)
              .setInternalKeys(sortedInternalKeys)
              .build());
    }

    return eventBuilder.build();
  }

  private CrashlyticsReport.Session.Event addRolloutsStateToEvent(
      CrashlyticsReport.Session.Event capturedEvent, UserMetadata reportMetadata) {
    List<CrashlyticsReport.Session.Event.RolloutAssignment> reportRolloutAssignments =
        reportMetadata.getRolloutsState();

    if (reportRolloutAssignments.isEmpty()) {
      return capturedEvent;
    }

    CrashlyticsReport.Session.Event.Builder eventBuilder = capturedEvent.toBuilder();
    eventBuilder.setRollouts(
        CrashlyticsReport.Session.Event.RolloutsState.builder()
            .setRolloutAssignments(reportRolloutAssignments)
            .build());
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

    reportPersistence.persistEvent(addMetaDataToEvent(capturedEvent), sessionId, isHighPriority);
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

    return Collections.unmodifiableList(attributesList);
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
        continue;
      }

      return applicationExitInfo;
    }

    return null;
  }
}
