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

package com.google.firebase.crashlytics.internal.common;

import com.google.firebase.crashlytics.internal.CrashlyticsTestCase;
import com.google.firebase.crashlytics.internal.network.HttpMethod;
import com.google.firebase.crashlytics.internal.network.HttpRequest;
import com.google.firebase.crashlytics.internal.network.HttpRequestFactory;
import com.google.firebase.crashlytics.internal.network.InspectableHttpRequest;
import java.util.Map;

public class AbstractSpiCallTest extends CrashlyticsTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
  }

  class TestSpiCall extends AbstractSpiCall {
    public TestSpiCall(
        String protocolAndHostOverride, String url, HttpRequestFactory requestFactory) {
      super(protocolAndHostOverride, url, requestFactory, HttpMethod.GET);
    }
  }

  public void testProtocolHostOverride() throws Exception {
    final String replacementProtocolAndHost = "http://abcd.efg";
    final String originalProtocolAndHost = "https://localhost:3000";
    final String url =
        originalProtocolAndHost
            + "/spi/v1/platforms/android/apps/com.crashlytics.crashdroid/settings.json";
    final String replacedUrl = url.replace(originalProtocolAndHost, replacementProtocolAndHost);

    final InspectableHttpRequest request = new InspectableHttpRequest();
    final TestSpiCall call =
        new TestSpiCall(
            replacementProtocolAndHost,
            url,
            new HttpRequestFactory() {
              @Override
              public HttpRequest buildHttpRequest(
                  HttpMethod method, String url, Map<String, String> queryParams) {
                request.setUrl(url);
                request.setQueryParams(queryParams);
                return request;
              }
            });
    // this causes the buildHttpRequest() method to get called, capturing the replaced url
    // value.
    call.getHttpRequest();
    assertEquals(replacedUrl, request.getUrl());
  }
}
