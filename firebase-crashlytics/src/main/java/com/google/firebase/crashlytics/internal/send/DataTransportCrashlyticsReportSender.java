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

import android.content.Context;
import androidx.annotation.NonNull;
import com.google.android.datatransport.Encoding;
import com.google.android.datatransport.Event;
import com.google.android.datatransport.Transformer;
import com.google.android.datatransport.Transport;
import com.google.android.datatransport.cct.CCTDestination;
import com.google.android.datatransport.runtime.TransportRuntime;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.crashlytics.internal.common.CrashlyticsReportWithSessionId;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport;
import com.google.firebase.crashlytics.internal.model.serialization.CrashlyticsReportJsonTransform;
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
  private static final String CRASHLYTICS_API_KEY =
      mergeStrings("AzSBpY4F0rHiHFdinTvM", "IayrSTFL9eJ69YeSUO2");
  private static final String CRASHLYTICS_TRANSPORT_NAME = "FIREBASE_CRASHLYTICS_REPORT";
  private static final Transformer<CrashlyticsReport, byte[]> DEFAULT_TRANSFORM =
      (r) -> TRANSFORM.reportToJson(r).getBytes(Charset.forName("UTF-8"));

  private final Transport<CrashlyticsReport> transport;
  private final Transformer<CrashlyticsReport, byte[]> transportTransform;

  public static DataTransportCrashlyticsReportSender create(Context context) {
    TransportRuntime.initialize(context);
    final Transport<CrashlyticsReport> transport =
        TransportRuntime.getInstance()
            .newFactory(new CCTDestination(CRASHLYTICS_ENDPOINT, CRASHLYTICS_API_KEY))
            .getTransport(
                CRASHLYTICS_TRANSPORT_NAME,
                CrashlyticsReport.class,
                Encoding.of("json"),
                DEFAULT_TRANSFORM);
    return new DataTransportCrashlyticsReportSender(transport, DEFAULT_TRANSFORM);
  }

  DataTransportCrashlyticsReportSender(
      Transport<CrashlyticsReport> transport,
      Transformer<CrashlyticsReport, byte[]> transportTransform) {
    this.transport = transport;
    this.transportTransform = transportTransform;
  }

  @NonNull
  public Task<CrashlyticsReportWithSessionId> sendReport(
      @NonNull CrashlyticsReportWithSessionId reportWithSessionId) {
    final CrashlyticsReport report = reportWithSessionId.getReport();

    TaskCompletionSource<CrashlyticsReportWithSessionId> tcs = new TaskCompletionSource<>();
    transport.schedule(
        Event.ofUrgent(report),
        error -> {
          if (error != null) {
            tcs.trySetException(error);
            return;
          }
          tcs.trySetResult(reportWithSessionId);
        });
    return tcs.getTask();
  }

  private static String mergeStrings(String part1, String part2) {
    int sizeDiff = part1.length() - part2.length();
    if (sizeDiff < 0 || sizeDiff > 1) {
      throw new IllegalArgumentException("Invalid input received");
    }

    StringBuilder url = new StringBuilder(part1.length() + part2.length());

    for (int i = 0; i < part1.length(); i++) {
      url.append(part1.charAt(i));
      if (part2.length() > i) {
        url.append(part2.charAt(i));
      }
    }

    return url.toString();
  }
}
