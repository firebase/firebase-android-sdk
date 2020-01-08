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

package com.google.firebase.crashlytics.internal.report.network;

import static com.google.firebase.crashlytics.internal.common.AbstractSpiCall.ANDROID_CLIENT_TYPE;
import static com.google.firebase.crashlytics.internal.common.AbstractSpiCall.HEADER_CLIENT_TYPE;
import static com.google.firebase.crashlytics.internal.common.AbstractSpiCall.HEADER_CLIENT_VERSION;
import static com.google.firebase.crashlytics.internal.common.AbstractSpiCall.HEADER_GOOGLE_APP_ID;
import static com.google.firebase.crashlytics.internal.report.network.DefaultCreateReportSpiCall.FILE_CONTENT_TYPE;
import static com.google.firebase.crashlytics.internal.report.network.DefaultCreateReportSpiCall.FILE_PARAM;
import static com.google.firebase.crashlytics.internal.report.network.DefaultCreateReportSpiCall.IDENTIFIER_PARAM;

import com.google.firebase.crashlytics.internal.CrashlyticsTestCase;
import com.google.firebase.crashlytics.internal.network.HttpMethod;
import com.google.firebase.crashlytics.internal.network.HttpRequest;
import com.google.firebase.crashlytics.internal.network.HttpRequestFactory;
import com.google.firebase.crashlytics.internal.network.InspectableHttpRequest;
import com.google.firebase.crashlytics.internal.report.model.CreateReportRequest;
import com.google.firebase.crashlytics.internal.report.model.SessionReport;
import java.io.File;
import java.util.Collections;
import java.util.Map;

public class DefaultCreateReportSpiCallTest extends CrashlyticsTestCase {

  private static final String ORGANIZATION_ID = "12345abcde12345abcde1234";
  private static final String GOOGLE_APP_ID = "1:12345678901:android:1234567890abcdefg";
  private static final String APP_ID = "com.crashlytics.crashdroid";
  private static final String URL =
      "http://reports.crashlytics.com/spi/v1/platforms/android/apps/" + APP_ID + "/reports";

  private static final String SEND_FLAGS_HEADER = "X-CRASHLYTICS-SEND-FLAGS";
  private static final String SEND_FLAGS_VALUE = "1";
  private static final Map<String, String> CUSTOM_HEADERS =
      Collections.singletonMap(SEND_FLAGS_HEADER, SEND_FLAGS_VALUE);

  public void testWebCall() throws Exception {
    // Not a realistic scenario, but we don't actually need to read the file during the test.
    final SessionReport report =
        new SessionReport(new File("DummySessionFile.cls"), CUSTOM_HEADERS);

    final CreateReportRequest requestData =
        new CreateReportRequest(ORGANIZATION_ID, GOOGLE_APP_ID, report);
    final InspectableHttpRequest request = new InspectableHttpRequest();
    assertTrue(makeSpiCall(request).invoke(requestData, true));

    assertEquals(URL, request.getUrl());

    final Map<String, String> headers = request.getHeaders();
    assertEquals(requestData.googleAppId, headers.get(HEADER_GOOGLE_APP_ID));
    assertEquals(ANDROID_CLIENT_TYPE, headers.get(HEADER_CLIENT_TYPE));
    assertEquals("1.0", headers.get(HEADER_CLIENT_VERSION));
    assertEquals(SEND_FLAGS_VALUE, headers.get(SEND_FLAGS_HEADER));

    final Map<String, Object> partsToValues = request.getMultipartValues();
    assertEquals(requestData.report.getIdentifier(), partsToValues.get(IDENTIFIER_PARAM));
    assertEquals(requestData.report.getFile(), partsToValues.get(FILE_PARAM));

    assertEquals(FILE_CONTENT_TYPE, request.getMultipartContentTypes().get(FILE_PARAM));
  }

  private DefaultCreateReportSpiCall makeSpiCall(final InspectableHttpRequest request) {
    return new DefaultCreateReportSpiCall(
        null,
        URL,
        new HttpRequestFactory() {
          @Override
          public HttpRequest buildHttpRequest(
              HttpMethod method, String url, Map<String, String> queryParams) {
            request.setUrl(url);
            request.setQueryParams(queryParams);
            return request;
          }
        },
        "1.0");
  }
}
