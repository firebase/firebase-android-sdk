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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.google.firebase.crashlytics.internal.CrashlyticsTestCase;
import com.google.firebase.crashlytics.internal.common.DataTransportState;
import com.google.firebase.crashlytics.internal.common.TestNativeReportFilesProvider;
import com.google.firebase.crashlytics.internal.common.TestReportFilesProvider;
import com.google.firebase.crashlytics.internal.report.model.CreateReportRequest;
import com.google.firebase.crashlytics.internal.report.model.Report;
import com.google.firebase.crashlytics.internal.report.network.CreateReportSpiCall;
import java.util.List;
import java.util.concurrent.TimeoutException;

public class ReportUploaderTest extends CrashlyticsTestCase {

  private static final long DEFAULT_TIMEOUT_MS = 10 * 1000L;
  private static final long DEFAULT_INTERVAL_MS = 500L;

  private CreateReportSpiCall mockCall;

  private TestReportFilesProvider reportFilesProvider;
  private ReportManager reportManager;
  private ReportUploader reportUploader;
  private ReportUploader.HandlingExceptionCheck mockHandlingExceptionCheck;

  @Override
  protected void setUp() throws Exception {
    mockCall = mock(CreateReportSpiCall.class);
    mockHandlingExceptionCheck = mock(ReportUploader.HandlingExceptionCheck.class);
    when(mockHandlingExceptionCheck.isHandlingException()).thenReturn(false);

    reportFilesProvider = new TestReportFilesProvider(getContext());

    reportManager = new ReportManager(reportFilesProvider);

    reportUploader =
        new ReportUploader(
            "testOrganizationId",
            "testGoogleAppId",
            DataTransportState.NONE,
            reportManager,
            mockCall,
            mockHandlingExceptionCheck);
  }

  public void testSendReport() throws Exception {
    when(mockCall.invoke(any(CreateReportRequest.class), eq(true))).thenReturn(true);

    reportFilesProvider.createTestCrashFile();

    final boolean sent = uploadAndWait();
    assertTrue(sent);
    assertEquals(0, reportManager.findReports().size());

    verify(mockCall).invoke(any(CreateReportRequest.class), eq(true));

    verifyNoMoreInteractions(mockCall);
  }

  public void testServerDown() throws Exception {
    // Test again with no crash files. Should succeed because the web service call should not be
    // made.

    final boolean sent = uploadAndWait();
    assertTrue(sent);
    assertEquals(0, reportManager.findReports().size());

    verifyZeroInteractions(mockCall);
  }

  public void testRetry() throws Exception {
    // Add a file with the connection still down and make sure it doesn't get added in the retry.

    reportFilesProvider.createTestCrashFile();

    boolean didCatchException = false;

    try {
      // expected to timeout because the send can't complete, so set a very small timeout
      // and interval
      final long timeout = 500L;
      final long interval = 10L;

      uploadAndWait(timeout, interval);
    } catch (TimeoutException ex) {
      didCatchException = true;
    }
    assertTrue(didCatchException);
    // Should still be running:
    assertTrue(reportUploader.isUploading());
    assertFalse(reportManager.findReports().isEmpty());

    verifyZeroInteractions(mockCall);
  }

  public void testSendReport_deletesWithoutSendingWhenDataTransportAll() throws Exception {
    reportUploader =
        new ReportUploader(
            "testOrganizationId",
            "testGoogleAppId",
            DataTransportState.ALL,
            reportManager,
            mockCall,
            mockHandlingExceptionCheck);

    reportFilesProvider.createTestCrashFile();

    final boolean sent = uploadAndWait();
    assertTrue(sent);
    assertEquals(0, reportManager.findReports().size());

    verifyZeroInteractions(mockCall);
  }

  public void testSendReport_sendsNativeCrashesWhenDataTransportJavaOnly() throws Exception {
    final TestNativeReportFilesProvider nativeReportFilesProvider =
        new TestNativeReportFilesProvider(getContext());
    reportManager = new ReportManager(nativeReportFilesProvider);

    reportUploader =
        new ReportUploader(
            "testOrganizationId",
            "testGoogleAppId",
            DataTransportState.JAVA_ONLY,
            reportManager,
            mockCall,
            mockHandlingExceptionCheck);

    when(mockCall.invoke(any(CreateReportRequest.class), eq(true))).thenReturn(true);

    nativeReportFilesProvider.createTestCrashDirectory();

    final boolean sent = uploadAndWait();
    assertTrue(sent);
    assertEquals(0, reportManager.findReports().size());

    verify(mockCall).invoke(any(CreateReportRequest.class), eq(true));

    verifyNoMoreInteractions(mockCall);
  }

  /**
   * kick off an upload and block until it is complete.
   *
   * @return true if the upload was successful.
   * @throws InterruptedException
   */
  private boolean uploadAndWait() throws TimeoutException, InterruptedException {
    return uploadAndWait(DEFAULT_TIMEOUT_MS, DEFAULT_INTERVAL_MS);
  }

  private boolean uploadAndWait(long timeout, long interval)
      throws InterruptedException, TimeoutException {

    List<Report> reports = reportManager.findReports();
    reportUploader.uploadReportsAsync(reports, true, 1.0f);

    int elapsed = 0;
    while (reportUploader.isUploading()) {
      Thread.sleep(interval);
      elapsed += interval;
      if (elapsed > timeout) {
        throw new TimeoutException();
      }
    }
    return reportManager.findReports().isEmpty();
  }
}
