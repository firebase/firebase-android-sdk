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

package com.google.firebase.crashlytics.internal.report;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.crashlytics.internal.Logger;
import com.google.firebase.crashlytics.internal.common.BackgroundPriorityRunnable;
import com.google.firebase.crashlytics.internal.common.DataTransportState;
import com.google.firebase.crashlytics.internal.report.model.CreateReportRequest;
import com.google.firebase.crashlytics.internal.report.model.Report;
import com.google.firebase.crashlytics.internal.report.network.CreateReportSpiCall;
import com.google.firebase.crashlytics.internal.settings.model.AppSettingsData;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ReportUploader {

  public interface HandlingExceptionCheck {
    boolean isHandlingException();
  }

  /** An interface that can create a ReportUploader. */
  public interface Provider {
    ReportUploader createReportUploader(@NonNull AppSettingsData appSettingsData);
  }

  public interface ReportFilesProvider {
    /** @return an array of complete session files to be uploaded, or null. */
    File[] getCompleteSessionFiles();

    /** @return an array of directories containing native crash report files, or null. */
    File[] getNativeReportFiles();
  }

  private static final short[] RETRY_INTERVALS = {10, 20, 30, 60, 120, 300};

  private final CreateReportSpiCall createReportCall;
  @Nullable private final String organizationId;
  private final String googleAppId;
  private final DataTransportState dataTransportState;
  private final ReportManager reportManager;
  private final HandlingExceptionCheck handlingExceptionCheck;
  private Thread uploadThread;

  public ReportUploader(
      @Nullable String organizationId,
      String googleAppId,
      DataTransportState dataTransportState,
      ReportManager reportManager,
      CreateReportSpiCall createReportCall,
      HandlingExceptionCheck handlingExceptionCheck) {
    if (createReportCall == null) {
      throw new IllegalArgumentException("createReportCall must not be null.");
    }
    this.createReportCall = createReportCall;
    this.organizationId = organizationId;
    this.googleAppId = googleAppId;
    this.dataTransportState = dataTransportState;
    this.reportManager = reportManager;
    this.handlingExceptionCheck = handlingExceptionCheck;
  }

  public synchronized void uploadReportsAsync(
      List<Report> reports, boolean dataCollectionToken, float delay) {
    if (uploadThread != null) {
      Logger.getLogger().d("Report upload has already been started.");
      return;
    }

    final Worker uploadWorker = new Worker(reports, dataCollectionToken, delay);
    uploadThread = new Thread(uploadWorker, "Crashlytics Report Uploader");
    uploadThread.start();
  }

  /** Not an atomic function, should only be used for unit testing. */
  boolean isUploading() {
    return uploadThread != null;
  }

  /**
   * Synchronously uploads a single report, removing it if the upload is successful.
   *
   * @return true if the upload operation completed without error and the file was removed.
   */
  public boolean uploadReport(Report report, boolean dataCollectionToken) {
    boolean removed = false;
    try {
      final CreateReportRequest requestData =
          new CreateReportRequest(organizationId, googleAppId, report);

      boolean shouldDeleteReport = true;

      if (dataTransportState == DataTransportState.ALL) {
        Logger.getLogger().d("Report configured to be sent via DataTransport.");
      } else if (dataTransportState == DataTransportState.JAVA_ONLY
          && report.getType() == Report.Type.JAVA) {
        Logger.getLogger().d("Report configured to be sent via DataTransport.");
      } else {
        final boolean sent = createReportCall.invoke(requestData, dataCollectionToken);
        Logger.getLogger()
            .i(
                "Crashlytics Reports Endpoint upload "
                    + (sent ? "complete: " : "FAILED: ")
                    + report.getIdentifier());
        shouldDeleteReport = sent;
      }

      if (shouldDeleteReport) {
        reportManager.deleteReport(report);
        removed = true;
      }
    } catch (Exception e) {
      Logger.getLogger().e("Error occurred sending report " + report, e);
    }
    return removed;
  }

  private class Worker extends BackgroundPriorityRunnable {
    private final List<Report> reports;
    private final boolean dataCollectionToken;
    private final float delay;

    Worker(List<Report> reports, boolean dataCollectionToken, float delay) {
      this.reports = reports;
      this.dataCollectionToken = dataCollectionToken;
      this.delay = delay;
    }

    @Override
    public void onRun() {
      try {
        attemptUploadWithRetry(reports, dataCollectionToken);
      } catch (Exception e) {
        Logger.getLogger()
            .e("An unexpected error occurred while attempting to upload crash reports.", e);
      }
      uploadThread = null;
    }

    private void attemptUploadWithRetry(List<Report> reports, boolean dataCollectionToken) {
      Logger.getLogger().d("Starting report processing in " + delay + " second(s)...");

      if (delay > 0) {
        try {
          Thread.sleep((long) (delay * 1000));
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return; // cancel on interrupt
        }
      }

      if (handlingExceptionCheck.isHandlingException()) {
        // Need to check if we're handling an exception currently so we don't prompt the
        // user during a crash, which can cause an ANR, which may prevent the crash file
        // from being written correctly.
        return;
      }

      int retryCount = 0;
      while (reports.size() > 0) {
        if (handlingExceptionCheck.isHandlingException()) {
          // abandon attempt to send if we are currently handling a crash.
          // The handler automatically kicks off a send attempt when it is finished.

          // It is important that this check is performed *after* we get reports set,
          // because if otherwise a race condition can result where we may have a
          // partially-written report being written to concurrently while trying
          // to send it. By getting the report set *before* bailing out if we are
          // currently handling any exceptions, it is not possible that we could have a
          // handle to a crash report in progress.
          return;
        }

        Logger.getLogger().d("Attempting to send " + reports.size() + " report(s)");
        ArrayList<Report> remaining = new ArrayList<>();
        for (Report report : reports) {
          boolean removed = uploadReport(report, dataCollectionToken);
          if (!removed) {
            remaining.add(report);
          }
        }
        reports = remaining;
        if (reports.size() > 0) {
          final long interval = RETRY_INTERVALS[Math.min(retryCount++, RETRY_INTERVALS.length - 1)];
          Logger.getLogger()
              .d("Report submission: scheduling delayed retry in " + interval + " seconds");
          try {
            Thread.sleep(interval * 1000);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
          }
        }
      }
    }
  }
}
