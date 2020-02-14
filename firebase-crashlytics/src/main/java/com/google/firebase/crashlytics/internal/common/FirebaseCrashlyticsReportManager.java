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

import com.google.android.gms.tasks.Task;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport;
import com.google.firebase.crashlytics.internal.persistence.CrashlyticsReportPersistence;
import com.google.firebase.crashlytics.internal.send.DataTransportCrashlyticsReportSender;
import com.google.firebase.crashlytics.internal.settings.model.AppSettingsData;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * This class handles Crashlytics lifecycle events and manages capture, persistence, and sending of
 * reports to Firebase Crashlytics.
 */
public class FirebaseCrashlyticsReportManager implements CrashlyticsLifecycleEvents {

  private static final String EVENT_TYPE_CRASH = "crash";
  private static final int EVENT_THREAD_IMPORTANCE = 4;
  private static final int MAX_CHAINED_EXCEPTION_DEPTH = 8;

  private final CrashlyticsReportDataCapture dataCapture;
  private final CrashlyticsReportPersistence reportPersistence;
  private final DataTransportCrashlyticsReportSender reportsSender;
  private final CurrentTimeProvider currentTimeProvider;

  // TODO: Use the shared executor from Crashlytics
  private final ExecutorService executor = Executors.newCachedThreadPool();

  private String currentSessionId;

  public FirebaseCrashlyticsReportManager(
      CrashlyticsReportDataCapture dataCapture,
      CrashlyticsReportPersistence reportPersistence,
      DataTransportCrashlyticsReportSender reportsSender,
      CurrentTimeProvider currentTimeProvider) {
    this.dataCapture = dataCapture;
    this.reportPersistence = reportPersistence;
    this.reportsSender = reportsSender;
    this.currentTimeProvider = currentTimeProvider;
  }

  @Override
  public void onBeginSession(String sessionId) {
    final long timestamp = currentTimeProvider.getCurrentTimeMillis() / 1000;
    currentSessionId = sessionId;

    final CrashlyticsReport capturedReport = dataCapture.captureReportData(sessionId, timestamp);

    reportPersistence.persistReport(capturedReport);
  }

  @Override
  public void onFatalEvent(Throwable event, Thread thread) {
    final long timestamp = currentTimeProvider.getCurrentTimeMillis() / 1000;

    final CrashlyticsReport.Session.Event capturedEvent =
        dataCapture.captureEventData(
            event,
            thread,
            EVENT_TYPE_CRASH,
            timestamp,
            EVENT_THREAD_IMPORTANCE,
            MAX_CHAINED_EXCEPTION_DEPTH);

    reportPersistence.persistEvent(capturedEvent, currentSessionId);
  }

  @Override
  public void onEndSession() {
    currentSessionId = null;
  }

  @Override
  public void onFinalizeSessions() {
    reportPersistence.finalizeReports(currentSessionId);
  }

  @Override
  public void onSendReports(AppSettingsData appSettingsData) {
    final List<CrashlyticsReport> reportsToSend = reportPersistence.loadFinalizedReports();
    for (CrashlyticsReport report : reportsToSend) {
      reportsSender
          .sendReport(report.withOrganizationId(appSettingsData.organizationId))
          .continueWith(executor, this::onReportSendComplete);
    }
  }

  private boolean onReportSendComplete(Task<CrashlyticsReport> task) {
    if (task.isSuccessful()) {
      // TODO: if the report is fatal, send an analytics event.
      final CrashlyticsReport report = task.getResult();
      reportPersistence.deleteFinalizedReport(report.getSession().getIdentifier());
      return true;
    }
    // TODO: Something went wrong. Log? Throw?
    return false;
  }
}
