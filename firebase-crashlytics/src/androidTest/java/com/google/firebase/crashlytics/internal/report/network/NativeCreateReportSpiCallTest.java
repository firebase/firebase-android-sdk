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
import static com.google.firebase.crashlytics.internal.report.network.NativeCreateReportSpiCall.ORGANIZATION_IDENTIFIER_PARAM;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.firebase.crashlytics.internal.CrashlyticsTestCase;
import com.google.firebase.crashlytics.internal.network.HttpMethod;
import com.google.firebase.crashlytics.internal.network.HttpRequest;
import com.google.firebase.crashlytics.internal.network.HttpRequestFactory;
import com.google.firebase.crashlytics.internal.network.InspectableHttpRequest;
import com.google.firebase.crashlytics.internal.report.model.CreateReportRequest;
import com.google.firebase.crashlytics.internal.report.model.Report;
import java.io.File;
import java.util.Map;

public class NativeCreateReportSpiCallTest extends CrashlyticsTestCase {

  private static final String ORGANIZATION_ID = "12345abcde12345abcde1234";
  private static final String GOOGLE_APP_ID = "1:12345678901:android:1234567890abcdefg";
  private static final String APP_ID = "com.google.firebase.crashlytics.test";
  private static final String URL =
      "https://reports.crashlytics.com/spi/v1/platforms/android/apps/" + APP_ID + "/reports";

  public void testWebCallContainsOrgIdParam() throws Exception {
    final Report mockNativeReport = mock(Report.class);
    when(mockNativeReport.getFiles()).thenReturn(new File[] {});

    final CreateReportRequest requestData =
        new CreateReportRequest(ORGANIZATION_ID, GOOGLE_APP_ID, mockNativeReport);
    final InspectableHttpRequest request = new InspectableHttpRequest();
    assertTrue(makeSpiCall(request).invoke(requestData, true));

    assertEquals(URL, request.getUrl());

    final Map<String, String> headers = request.getHeaders();
    assertEquals(requestData.googleAppId, headers.get(HEADER_GOOGLE_APP_ID));
    assertEquals(ANDROID_CLIENT_TYPE, headers.get(HEADER_CLIENT_TYPE));
    assertEquals("1.0", headers.get(HEADER_CLIENT_VERSION));

    final Map<String, Object> partsToValues = request.getMultipartValues();
    assertEquals(requestData.organizationId, partsToValues.get(ORGANIZATION_IDENTIFIER_PARAM));
  }

  private NativeCreateReportSpiCall makeSpiCall(final InspectableHttpRequest request) {
    return new NativeCreateReportSpiCall(
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
