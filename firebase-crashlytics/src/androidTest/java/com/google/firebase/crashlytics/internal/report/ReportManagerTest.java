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

import com.google.firebase.crashlytics.internal.CrashlyticsTestCase;
import com.google.firebase.crashlytics.internal.common.TestReportFilesProvider;
import com.google.firebase.crashlytics.internal.report.model.Report;
import java.io.IOException;
import java.util.List;

public class ReportManagerTest extends CrashlyticsTestCase {
  private ReportManager reportManager;
  private TestReportFilesProvider reportFilesProvider;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    reportFilesProvider = new TestReportFilesProvider(getContext());
    reportManager = new ReportManager(reportFilesProvider);
  }

  public void testFindReports() throws IOException {
    reportFilesProvider.createTestCrashFile();

    final List<Report> reports = reportManager.findReports();
    assertEquals(1, reports.size());
    assertEquals(1, reports.get(0).getFiles().length);
  }

  public void testFindReports_withInvalidFiles() throws Exception {
    reportFilesProvider.createInvalidCrashFilesInProgress();

    final List<Report> reports = reportManager.findReports();
    assertEquals(0, reports.size());
  }

  public void testFindReports_withValidAndInvalidFiles() throws Exception {
    reportFilesProvider.createTestCrashFile();
    reportFilesProvider.createInvalidCrashFilesInProgress();
    reportFilesProvider.createInvalidCrashFilesNoBinaryImages();

    final List<Report> reports = reportManager.findReports();
    assertEquals(1, reports.size());
    assertEquals(1, reports.get(0).getFiles().length);
  }
}
