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

import com.google.firebase.crashlytics.internal.Logger;
import com.google.firebase.crashlytics.internal.report.model.NativeSessionReport;
import com.google.firebase.crashlytics.internal.report.model.Report;
import com.google.firebase.crashlytics.internal.report.model.SessionReport;
import java.io.File;
import java.util.LinkedList;
import java.util.List;

public class ReportManager {
  private final ReportUploader.ReportFilesProvider reportFilesProvider;

  public ReportManager(ReportUploader.ReportFilesProvider reportFilesProvider) {
    this.reportFilesProvider = reportFilesProvider;
  }

  /** @return true if there are reports on the file system that could be uploaded. */
  public boolean areReportsAvailable() {
    File[] clsFiles = reportFilesProvider.getCompleteSessionFiles();
    File[] nativeReportFiles = reportFilesProvider.getNativeReportFiles();
    if (clsFiles != null) {
      if (clsFiles.length > 0) {
        return true;
      }
    }
    if (nativeReportFiles != null) {
      if (nativeReportFiles.length > 0) {
        return true;
      }
    }
    return false;
  }

  public List<Report> findReports() {
    Logger.getLogger().d("Checking for crash reports...");

    File[] clsFiles = reportFilesProvider.getCompleteSessionFiles();
    File[] nativeReportFiles = reportFilesProvider.getNativeReportFiles();

    final List<Report> reports = new LinkedList<>();
    if (clsFiles != null) {
      for (File file : clsFiles) {
        Logger.getLogger().d("Found crash report " + file.getPath());
        reports.add(new SessionReport(file));
      }
    }

    if (nativeReportFiles != null) {
      for (File dir : nativeReportFiles) {
        reports.add(new NativeSessionReport(dir));
      }
    }

    if (reports.isEmpty()) {
      Logger.getLogger().d("No reports found.");
    }

    return reports;
  }

  public void deleteReport(Report report) {
    report.remove();
  }

  public void deleteReports(List<Report> reports) {
    for (Report report : reports) {
      deleteReport(report);
    }
  }
}
