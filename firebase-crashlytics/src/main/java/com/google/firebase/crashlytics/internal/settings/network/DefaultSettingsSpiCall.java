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

import com.google.firebase.crashlytics.internal.Logger;
import com.google.firebase.crashlytics.internal.common.AbstractSpiCall;
import com.google.firebase.crashlytics.internal.common.CommonUtils;
import com.google.firebase.crashlytics.internal.common.CrashlyticsCore;
import com.google.firebase.crashlytics.internal.network.HttpMethod;
import com.google.firebase.crashlytics.internal.network.HttpRequest;
import com.google.firebase.crashlytics.internal.network.HttpRequestFactory;
import com.google.firebase.crashlytics.internal.network.HttpResponse;
import com.google.firebase.crashlytics.internal.settings.model.SettingsRequest;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;

/** Default implementation of the {@link SettingsSpiCall} */
public class DefaultSettingsSpiCall extends AbstractSpiCall implements SettingsSpiCall {
  static final String BUILD_VERSION_PARAM = "build_version";
  static final String DISPLAY_VERSION_PARAM = "display_version";
  static final String INSTANCE_PARAM = "instance";
  static final String SOURCE_PARAM = "source";

  static final String HEADER_DEVICE_MODEL = "X-CRASHLYTICS-DEVICE-MODEL";
  static final String HEADER_OS_BUILD_VERSION = "X-CRASHLYTICS-OS-BUILD-VERSION";
  static final String HEADER_OS_DISPLAY_VERSION = "X-CRASHLYTICS-OS-DISPLAY-VERSION";
  static final String HEADER_INSTALLATION_ID = "X-CRASHLYTICS-INSTALLATION-ID";

  private Logger logger;

  /**
   * Create a new GET call on the provided <code>url</code>. That <code>url</code> {@link String}
   * should not include query parameters. Those will be applied automatically from the {@link
   * SettingsRequest} passed to {@link #invoke(SettingsRequest)}
   *
   * @param protocolAndHostOverride {@link String} to use in place of whatever protocol and host are
   *     present in the provide <code>url</code>.
   * @param url {@link String} to use as the endpoint.
   */
  public DefaultSettingsSpiCall(
      String protocolAndHostOverride, String url, HttpRequestFactory requestFactory) {
    this(protocolAndHostOverride, url, requestFactory, HttpMethod.GET, Logger.getLogger());
  }

  /** Meant for use in testing. Prefer DefaultSettingsSpiCall(String) for normal use. */
  DefaultSettingsSpiCall(
      String protocolAndHostOverride,
      String url,
      HttpRequestFactory requestFactory,
      HttpMethod method,
      Logger logger) {
    super(protocolAndHostOverride, url, requestFactory, method);
    this.logger = logger;
  }

  @Override
  public JSONObject invoke(SettingsRequest requestData, boolean dataCollectionToken) {
    if (!dataCollectionToken) {
      throw new RuntimeException("An invalid data collection token was used.");
    }
    JSONObject toReturn = null;

    try {
      final Map<String, String> queryParams = getQueryParamsFor(requestData);
      HttpRequest httpRequest = getHttpRequest(queryParams);
      httpRequest = applyHeadersTo(httpRequest, requestData);

      logger.d("Requesting settings from " + getUrl());
      logger.d("Settings query params were: " + queryParams);

      final HttpResponse httpResponse = httpRequest.execute();
      logger.d("Settings request ID: " + httpResponse.header(AbstractSpiCall.HEADER_REQUEST_ID));

      toReturn = handleResponse(httpResponse);
    } catch (IOException e) {
      logger.e("Settings request failed.", e);
      toReturn = null;
    }

    return toReturn;
  }

  /** package private for testing */
  JSONObject handleResponse(HttpResponse httpResponse) {
    final int statusCode = httpResponse.code();
    logger.d("Settings result was: " + statusCode);

    final JSONObject toReturn;
    if (requestWasSuccessful(statusCode)) {
      toReturn = getJsonObjectFrom(httpResponse.body());
    } else {
      logger.e("Failed to retrieve settings from " + getUrl());
      toReturn = null;
    }
    return toReturn;
  }

  /** package private for testing */
  boolean requestWasSuccessful(int httpStatusCode) {
    // We need to check against explicit 2xx status codes since status codes such as
    // 204s (HTTP_NO_CONTENT) and 205s (HTTP_RESET), are considered unsuccessful and do not
    // return a JSON response. Also, we should never get a 206 (HTTP_PARTIAL) since our request
    // does not include a Range header field.
    return httpStatusCode == HttpURLConnection.HTTP_OK
        || httpStatusCode == HttpURLConnection.HTTP_CREATED
        || httpStatusCode == HttpURLConnection.HTTP_ACCEPTED
        || httpStatusCode == HttpURLConnection.HTTP_NOT_AUTHORITATIVE;
  }

  private JSONObject getJsonObjectFrom(String httpRequestBody) {
    try {
      return new JSONObject(httpRequestBody);
    } catch (Exception e) {
      logger.d("Failed to parse settings JSON from " + getUrl(), e);
      logger.d("Settings response " + httpRequestBody);
      return null;
    }
  }

  private Map<String, String> getQueryParamsFor(SettingsRequest requestData) {
    final Map<String, String> queryParams = new HashMap<>();
    queryParams.put(BUILD_VERSION_PARAM, requestData.buildVersion);
    queryParams.put(DISPLAY_VERSION_PARAM, requestData.displayVersion);
    queryParams.put(SOURCE_PARAM, Integer.toString(requestData.source));

    final String instanceId = requestData.instanceId;
    if (!CommonUtils.isNullOrEmpty(instanceId)) {
      queryParams.put(INSTANCE_PARAM, instanceId);
    }

    return queryParams;
  }

  private HttpRequest applyHeadersTo(HttpRequest request, SettingsRequest requestData) {
    // We have to avoid setting a header with a null value because low Android API levels
    // (e.g. 8) turn this into an invalid HTTP request, while higher API levels do not.
    applyNonNullHeader(request, AbstractSpiCall.HEADER_GOOGLE_APP_ID, requestData.googleAppId);
    applyNonNullHeader(
        request, AbstractSpiCall.HEADER_CLIENT_TYPE, AbstractSpiCall.ANDROID_CLIENT_TYPE);
    applyNonNullHeader(
        request, AbstractSpiCall.HEADER_CLIENT_VERSION, CrashlyticsCore.getVersion());
    applyNonNullHeader(request, AbstractSpiCall.HEADER_ACCEPT, AbstractSpiCall.ACCEPT_JSON_VALUE);
    applyNonNullHeader(request, HEADER_DEVICE_MODEL, requestData.deviceModel);
    applyNonNullHeader(request, HEADER_OS_BUILD_VERSION, requestData.osBuildVersion);
    applyNonNullHeader(request, HEADER_OS_DISPLAY_VERSION, requestData.osDisplayVersion);
    applyNonNullHeader(
        request, HEADER_INSTALLATION_ID, requestData.installIdProvider.getCrashlyticsInstallId());

    return request;
  }

  private void applyNonNullHeader(HttpRequest request, String key, String value) {
    if (value != null) {
      request.header(key, value);
    }
  }
}
