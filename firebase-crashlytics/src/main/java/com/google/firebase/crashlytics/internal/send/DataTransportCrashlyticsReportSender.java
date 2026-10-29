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

package com.google.firebase.crashlytics.internal.send;

import static com.google.firebase.crashlytics.internal.common.CommonUtils.CRASHLYTICS_API_KEY;
import static com.google.firebase.crashlytics.internal.common.CommonUtils.mergeStrings;

import android.content.Context;
import androidx.annotation.NonNull;
import com.google.android.datatransport.Encoding;
import com.google.android.datatransport.Transformer;
import com.google.android.datatransport.Transport;
import com.google.android.datatransport.cct.CCTDestination;
import com.google.android.datatransport.runtime.TransportRuntime;
import com.google.android.gms.tasks.Task;
import com.google.firebase.crashlytics.internal.common.CrashlyticsReportWithSessionId;
import com.google.firebase.crashlytics.internal.common.OnDemandCounter;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport;
import com.google.firebase.crashlytics.internal.model.serialization.CrashlyticsReportJsonTransform;
import com.google.firebase.crashlytics.internal.settings.SettingsProvider;
import java.nio.charset.Charset;

/**
 * This class is responsible for sending CrashlyticsReport objects to Crashlytics through Google
 * DataTransport.
 */
public class DataTransportCrashlyticsReportSender {

  private static final CrashlyticsReportJsonTransform TRANSFORM =
      new CrashlyticsReportJsonTransform();
  private static final String CRASHLYTICS_ENDPOINT =
      mergeStrings("hts/cahyiseot-agolai.o/1frlglgc/aclg", "tp:/rsltcrprsp.ogepscmv/ieo/eaybtho");
  private static final String CRASHLYTICS_TRANSPORT_NAME = "FIREBASE_CRASHLYTICS_REPORT";
  private static final Transformer<CrashlyticsReport, byte[]> DEFAULT_TRANSFORM =
      (r) -> TRANSFORM.reportToJson(r).getBytes(Charset.forName("UTF-8"));

  private final ReportQueue reportQueue;

  public static DataTransportCrashlyticsReportSender create(
      Context context, SettingsProvider settingsProvider, OnDemandCounter onDemandCounter) {
    TransportRuntime.initialize(context);
    final Transport<CrashlyticsReport> transport =
        TransportRuntime.getInstance()
            .newFactory(new CCTDestination(CRASHLYTICS_ENDPOINT, CRASHLYTICS_API_KEY))
            .getTransport(
                CRASHLYTICS_TRANSPORT_NAME,
                CrashlyticsReport.class,
                Encoding.of("json"),
                DEFAULT_TRANSFORM);
    ReportQueue reportQueue =
        new ReportQueue(transport, settingsProvider.getSettingsSync(), onDemandCounter);
    return new DataTransportCrashlyticsReportSender(reportQueue);
  }

  DataTransportCrashlyticsReportSender(ReportQueue reportQueue) {
    this.reportQueue = reportQueue;
  }

  @NonNull
  public Task<CrashlyticsReportWithSessionId> enqueueReport(
      @NonNull CrashlyticsReportWithSessionId reportWithSessionId, boolean isOnDemand) {
    return reportQueue.enqueueReport(reportWithSessionId, isOnDemand).getTask();
  }
}
