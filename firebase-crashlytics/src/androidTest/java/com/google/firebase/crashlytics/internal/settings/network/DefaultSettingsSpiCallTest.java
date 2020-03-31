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

package com.google.firebase.crashlytics.internal.settings.network;

import static com.google.firebase.crashlytics.internal.common.AbstractSpiCall.ACCEPT_JSON_VALUE;
import static com.google.firebase.crashlytics.internal.common.AbstractSpiCall.ANDROID_CLIENT_TYPE;
import static com.google.firebase.crashlytics.internal.common.AbstractSpiCall.HEADER_ACCEPT;
import static com.google.firebase.crashlytics.internal.common.AbstractSpiCall.HEADER_CLIENT_TYPE;
import static com.google.firebase.crashlytics.internal.common.AbstractSpiCall.HEADER_GOOGLE_APP_ID;
import static com.google.firebase.crashlytics.internal.settings.network.DefaultSettingsSpiCall.HEADER_DEVICE_MODEL;
import static com.google.firebase.crashlytics.internal.settings.network.DefaultSettingsSpiCall.HEADER_INSTALLATION_ID;
import static com.google.firebase.crashlytics.internal.settings.network.DefaultSettingsSpiCall.HEADER_OS_BUILD_VERSION;
import static com.google.firebase.crashlytics.internal.settings.network.DefaultSettingsSpiCall.HEADER_OS_DISPLAY_VERSION;
import static org.mockito.Mockito.*;

import com.google.firebase.crashlytics.internal.CrashlyticsTestCase;
import com.google.firebase.crashlytics.internal.Logger;
import com.google.firebase.crashlytics.internal.common.CommonUtils;
import com.google.firebase.crashlytics.internal.common.InstallIdProvider;
import com.google.firebase.crashlytics.internal.network.HttpMethod;
import com.google.firebase.crashlytics.internal.network.HttpRequest;
import com.google.firebase.crashlytics.internal.network.HttpRequestFactory;
import com.google.firebase.crashlytics.internal.network.HttpResponse;
import com.google.firebase.crashlytics.internal.network.InspectableHttpRequest;
import com.google.firebase.crashlytics.internal.settings.model.SettingsRequest;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.Map;

public class DefaultSettingsSpiCallTest extends CrashlyticsTestCase {

  private static final String GOOGLE_APP_ID = "1:12345678901:android:1234567890abcdefg";
  private static final String BUILD_VERSION = "1";
  private static final String DISPLAY_VERSION = "1.0";
  private static final String DEVICE_MODEL = "Samsung/SM-G920";
  private static final String OS_BUILD_VERSION = "111abc";
  private static final String OS_DISPLAY_VERSION = "4.3.2";
  private static final String INSTALLATION_ID = "d1dc3e52e16cbfe632902aeb112830491690504e";
  private static final int SOURCE = 4;
  private static final String ICON_HASH = "fakeHash";

  private static final String TEST_URL = "http://test";

  private Logger mockLogger;
  private DefaultSettingsSpiCall defaultSettingsSpiCall;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    mockLogger = mock(Logger.class);

    defaultSettingsSpiCall =
        new DefaultSettingsSpiCall(
            null, TEST_URL, mock(HttpRequestFactory.class), HttpMethod.GET, mockLogger);
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  public void testWebCall() throws Exception {
    final String instanceId = CommonUtils.createInstanceIdFrom("fake_build_id");
    final SettingsRequest requestData = buildSettingsRequest(instanceId);

    final String url =
        "http://localhost:3000/spi/v1/platforms/android/apps/com.crashlytics.test/settings";

    final InspectableHttpRequest request = new InspectableHttpRequest();

    final DefaultSettingsSpiCall call =
        new DefaultSettingsSpiCall(
            null,
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

    assertNotNull(call.invoke(requestData, true));

    assertEquals(url, request.getUrl());

    final Map<?, ?> queryParams = request.getQueryParams();
    assertEquals(BUILD_VERSION, queryParams.get(DefaultSettingsSpiCall.BUILD_VERSION_PARAM));
    assertEquals(DISPLAY_VERSION, queryParams.get(DefaultSettingsSpiCall.DISPLAY_VERSION_PARAM));
    assertEquals(instanceId, queryParams.get(DefaultSettingsSpiCall.INSTANCE_PARAM));
    assertEquals(Integer.toString(SOURCE), queryParams.get(DefaultSettingsSpiCall.SOURCE_PARAM));

    final Map<String, String> headers = request.getHeaders();
    assertEquals(GOOGLE_APP_ID, headers.get(HEADER_GOOGLE_APP_ID));
    assertEquals(ANDROID_CLIENT_TYPE, headers.get(HEADER_CLIENT_TYPE));
    assertEquals(DEVICE_MODEL, headers.get(HEADER_DEVICE_MODEL));
    assertEquals(OS_BUILD_VERSION, headers.get(HEADER_OS_BUILD_VERSION));
    assertEquals(OS_DISPLAY_VERSION, headers.get(HEADER_OS_DISPLAY_VERSION));
    assertEquals(INSTALLATION_ID, headers.get(HEADER_INSTALLATION_ID));
    assertEquals(ACCEPT_JSON_VALUE, headers.get(HEADER_ACCEPT));
  }

  public void testWebCallNoInstanceId() throws Exception {
    final SettingsRequest requestData = buildSettingsRequest(null);

    final String url =
        "http://localhost:3000/spi/v1/platforms/android/apps/com.crashlytics.test/settings";

    final InspectableHttpRequest request = new InspectableHttpRequest();

    final DefaultSettingsSpiCall call =
        new DefaultSettingsSpiCall(
            null,
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

    assertNotNull(call.invoke(requestData, true));

    assertEquals(url, request.getUrl());

    final Map<?, ?> queryParams = request.getQueryParams();
    assertEquals(BUILD_VERSION, queryParams.get(DefaultSettingsSpiCall.BUILD_VERSION_PARAM));
    assertEquals(DISPLAY_VERSION, queryParams.get(DefaultSettingsSpiCall.DISPLAY_VERSION_PARAM));
    assertFalse(queryParams.containsKey(DefaultSettingsSpiCall.INSTANCE_PARAM));
    assertEquals(Integer.toString(SOURCE), queryParams.get(DefaultSettingsSpiCall.SOURCE_PARAM));

    final Map<String, String> headers = request.getHeaders();
    assertEquals(GOOGLE_APP_ID, headers.get(HEADER_GOOGLE_APP_ID));
    assertEquals(ANDROID_CLIENT_TYPE, headers.get(HEADER_CLIENT_TYPE));
    assertEquals(DEVICE_MODEL, headers.get(HEADER_DEVICE_MODEL));
    assertEquals(OS_BUILD_VERSION, headers.get(HEADER_OS_BUILD_VERSION));
    assertEquals(OS_DISPLAY_VERSION, headers.get(HEADER_OS_DISPLAY_VERSION));
    assertEquals(INSTALLATION_ID, headers.get(HEADER_INSTALLATION_ID));
    assertEquals(ACCEPT_JSON_VALUE, headers.get(HEADER_ACCEPT));
  }

  public void testHandleResponse_requestSuccessfulValidJson() throws IOException {
    final HttpResponse mockHttpResponse = mock(HttpResponse.class);
    when(mockHttpResponse.code()).thenReturn(HttpURLConnection.HTTP_OK);
    when(mockHttpResponse.body()).thenReturn(getJsonContentFrom("default_settings.json"));

    assertNotNull(defaultSettingsSpiCall.handleResponse(mockHttpResponse));
  }

  private String getJsonContentFrom(String fileName) throws IOException {
    InputStream jsonInputStream = null;
    try {
      jsonInputStream = getContext().getResources().getAssets().open(fileName);
      return CommonUtils.streamToString(jsonInputStream);
    } finally {
      CommonUtils.closeQuietly(jsonInputStream);
    }
  }

  public void testHandleResponse_requestSuccessfulNoJson() {
    final HttpResponse mockHttpResponse = mock(HttpResponse.class);
    when(mockHttpResponse.code()).thenReturn(HttpURLConnection.HTTP_OK);
    when(mockHttpResponse.body()).thenReturn("No Json here!");

    assertNull(defaultSettingsSpiCall.handleResponse(mockHttpResponse));
    // Verify failing to parse a JSON object does not result in an error log.
    verify(mockLogger, never()).e(anyString());
  }

  public void testHandleResponse_requestNotSuccessful() throws IOException {
    final HttpResponse mockHttpResponse = mock(HttpResponse.class);
    when(mockHttpResponse.code()).thenReturn(HttpURLConnection.HTTP_INTERNAL_ERROR);

    assertNull(defaultSettingsSpiCall.handleResponse(mockHttpResponse));
    verify(mockHttpResponse, never()).body();
    verify(mockLogger, times(1)).e(eq("Failed to retrieve settings from " + TEST_URL));
  }

  public void testRequestWasSuccessful_successfulStatusCodes() {
    final int[] statusCodes = {
      HttpURLConnection.HTTP_OK,
      HttpURLConnection.HTTP_CREATED,
      HttpURLConnection.HTTP_ACCEPTED,
      HttpURLConnection.HTTP_NOT_AUTHORITATIVE
    };
    for (int statusCode : statusCodes) {
      assertTrue(defaultSettingsSpiCall.requestWasSuccessful(statusCode));
    }
  }

  public void testRequestWasSuccessful_unsuccessfulStatusCodes() {
    // 204 and 205 are considered unsuccessful in this case, and 206 should never happen. Also
    // test with some of the other common status codes (these are ones that our backend has
    // been known to return).
    final int[] statusCodes = {
      HttpURLConnection.HTTP_NO_CONTENT,
      HttpURLConnection.HTTP_RESET,
      HttpURLConnection.HTTP_PARTIAL,
      HttpURLConnection.HTTP_BAD_REQUEST,
      HttpURLConnection.HTTP_UNAUTHORIZED,
      HttpURLConnection.HTTP_FORBIDDEN,
      HttpURLConnection.HTTP_NOT_FOUND,
      HttpURLConnection.HTTP_NOT_ACCEPTABLE,
      HttpURLConnection.HTTP_INTERNAL_ERROR,
      HttpURLConnection.HTTP_BAD_GATEWAY,
      HttpURLConnection.HTTP_UNAVAILABLE,
      HttpURLConnection.HTTP_GATEWAY_TIMEOUT
    };
    for (int statusCode : statusCodes) {
      assertFalse(defaultSettingsSpiCall.requestWasSuccessful(statusCode));
    }
  }

  private SettingsRequest buildSettingsRequest(String instanceId) {
    final InstallIdProvider installIdProvider =
        new InstallIdProvider() {
          @Override
          public String getCrashlyticsInstallId() {
            return INSTALLATION_ID;
          }
        };
    return new SettingsRequest(
        GOOGLE_APP_ID,
        DEVICE_MODEL,
        OS_BUILD_VERSION,
        OS_DISPLAY_VERSION,
        installIdProvider,
        instanceId,
        DISPLAY_VERSION,
        BUILD_VERSION,
        SOURCE);
  }
}
