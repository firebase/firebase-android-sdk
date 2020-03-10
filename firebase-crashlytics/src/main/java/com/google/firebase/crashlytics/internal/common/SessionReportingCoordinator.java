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
import com.google.android.gms.tasks.Task;
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

  public interface SendReportPredicate {
    boolean shouldSendViaDataTransport();
  }

  private static final String EVENT_TYPE_CRASH = "crash";
  private static final String EVENT_TYPE_LOGGED = "error";
  private static final int EVENT_THREAD_IMPORTANCE = 4;
  private static final int MAX_CHAINED_EXCEPTION_DEPTH = 8;

  private static final int DEFAULT_MAX_EVENTS_TO_KEEP = 8;
  private static final int DEFAULT_MAX_REPORTS_TO_KEEP = 4;
  private static final int DEFAULT_MAX_SESSIONS_TO_KEEP = 8;

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

  private String currentSessionId;

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
  public void onBeginSession(String sessionId, long timestamp) {
    currentSessionId = sessionId;

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

  @Override
  public void onEndSession() {
    currentSessionId = null;
  }

  public void persistFatalEvent(Throwable event, Thread thread, long timestamp) {
    persistEvent(event, thread, EVENT_TYPE_CRASH, timestamp, true);
  }

  public void persistNonFatalEvent(Throwable event, Thread thread, long timestamp) {
    persistEvent(event, thread, EVENT_TYPE_LOGGED, timestamp, false);
  }

  public void persistNativeEvent(@NonNull File nativeEventFilesDir) {
    // TODO: Consider passing some sort of transformer instead of the raw files dir.
    /*gzipFile(minidump, new File(nativeSessionDirectory, "minidump"));
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
    gzipIfNotEmpty(logs, new File(nativeSessionDirectory, "logs"));*/
    final FilesPayload.File minidump = FilesPayload.File.builder().setFilename("minidump_file").setContents(new byte[0]).build();
  }

  public void persistUserId() {
    reportPersistence.persistUserIdForSession(reportMetadata.getUserId(), currentSessionId);
  }

  /** Creates finalized reports for all sessions besides the current session. */
  public void finalizeSessions(long timestamp) {
    reportPersistence.finalizeReports(currentSessionId, timestamp);
  }

  public void removeAllReports() {
    reportPersistence.deleteAllReports();
  }

  /**
   * Send all finalized reports.
   *
   * @param organizationId The organization ID this crash report should be associated with
   * @param reportSendCompleteExecutor executor on which to run report cleanup after each report is
   *     sent.
   * @param sendReportPredicate Predicate determining whether to send reports before cleaning them
   *     up
   */
  public void sendReports(
      String organizationId,
      Executor reportSendCompleteExecutor,
      SendReportPredicate sendReportPredicate) {
    if (!sendReportPredicate.shouldSendViaDataTransport()) {
      Logger.getLogger().d("Send via DataTransport disabled. Removing reports.");
      reportPersistence.deleteAllReports();
      return;
    }
    final List<CrashlyticsReport> reportsToSend = reportPersistence.loadFinalizedReports();
    for (CrashlyticsReport report : reportsToSend) {
      reportsSender
          .sendReport(report.withOrganizationId(organizationId))
          .continueWith(reportSendCompleteExecutor, this::onReportSendComplete);
    }
  }

  private void persistEvent(
      Throwable event, Thread thread, String eventType, long timestamp, boolean includeAllThreads) {

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

    if (sortedCustomAttributes != null) {
      eventBuilder.setApp(
          capturedEvent
              .getApp()
              .toBuilder()
              .setCustomAttributes(ImmutableList.from(sortedCustomAttributes))
              .build());
    }

    reportPersistence.persistEvent(eventBuilder.build(), currentSessionId, isHighPriority);
  }

  private boolean onReportSendComplete(Task<CrashlyticsReport> task) {
    if (task.isSuccessful()) {
      // TODO: if the report is fatal, send an analytics event.
      final CrashlyticsReport report = task.getResult();
      final String reportId = report.getSession().getIdentifier();
      Logger.getLogger().i("Crashlytics report sent successfully: " + reportId);
      reportPersistence.deleteFinalizedReport(reportId);
      return true;
    }
    // TODO: Something went wrong. Log? Throw?
    return false;
  }

  private static List<CustomAttribute> getSortedCustomAttributes(Map<String, String> attributes) {
    if (attributes == null || attributes.isEmpty()) {
      return null;
    }

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
