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
import com.google.firebase.crashlytics.internal.common.ResponseParser;
import com.google.firebase.crashlytics.internal.network.HttpMethod;
import com.google.firebase.crashlytics.internal.network.HttpRequest;
import com.google.firebase.crashlytics.internal.network.HttpRequestFactory;
import com.google.firebase.crashlytics.internal.network.HttpResponse;
import com.google.firebase.crashlytics.internal.settings.model.AppRequestData;
import java.io.IOException;

/**
 * Since the create and update App SPI calls are basically identical except for their HTTP request
 * method, the majority of their implementation is shared in this abstract class.
 */
abstract class AbstractAppSpiCall extends AbstractSpiCall implements AppSpiCall {
  public static final String ORGANIZATION_ID_PARAM = "org_id";
  public static final String APP_IDENTIFIER_PARAM = "app[identifier]";
  public static final String APP_NAME_PARAM = "app[name]";
  public static final String APP_INSTANCE_IDENTIFIER_PARAM = "app[instance_identifier]";
  public static final String APP_DISPLAY_VERSION_PARAM = "app[display_version]";
  public static final String APP_BUILD_VERSION_PARAM = "app[build_version]";
  public static final String APP_SOURCE_PARAM = "app[source]";
  public static final String APP_MIN_SDK_VERSION_PARAM = "app[minimum_sdk_version]";
  public static final String APP_BUILT_SDK_VERSION_PARAM = "app[built_sdk_version]";

  private final String version;

  public AbstractAppSpiCall(
      String protocolAndHostOverride,
      String url,
      HttpRequestFactory requestFactory,
      HttpMethod method,
      String version) {
    super(protocolAndHostOverride, url, requestFactory, method);
    this.version = version;
  }

  @Override
  public boolean invoke(AppRequestData requestData, boolean dataCollectionToken) {
    if (!dataCollectionToken) {
      throw new RuntimeException("An invalid data collection token was used.");
    }

    HttpRequest httpRequest = getHttpRequest();
    httpRequest = applyHeadersTo(httpRequest, requestData);
    httpRequest = applyMultipartDataTo(httpRequest, requestData);

    Logger.getLogger().d("Sending app info to " + getUrl());

    try {
      final HttpResponse httpResponse = httpRequest.execute();

      final int statusCode = httpResponse.code();
      final String kind = "POST".equalsIgnoreCase(httpRequest.method()) ? "Create" : "Update";

      Logger.getLogger()
          .d(kind + " app request ID: " + httpResponse.header(AbstractSpiCall.HEADER_REQUEST_ID));
      Logger.getLogger().d("Result was " + statusCode);

      return ResponseParser.ResponseActionDiscard == ResponseParser.parse(statusCode);
    } catch (IOException ioe) {
      Logger.getLogger().e("HTTP request failed.", ioe);
      throw new RuntimeException(ioe);
    }
  }

  private HttpRequest applyHeadersTo(HttpRequest request, AppRequestData requestData) {
    return request
        .header(AbstractSpiCall.HEADER_ORG_ID, requestData.organizationId)
        .header(AbstractSpiCall.HEADER_GOOGLE_APP_ID, requestData.googleAppId)
        .header(AbstractSpiCall.HEADER_CLIENT_TYPE, AbstractSpiCall.ANDROID_CLIENT_TYPE)
        .header(AbstractSpiCall.HEADER_CLIENT_VERSION, version);
  }

  private HttpRequest applyMultipartDataTo(HttpRequest request, AppRequestData requestData) {

    request =
        request
            .part(ORGANIZATION_ID_PARAM, requestData.organizationId)
            .part(APP_IDENTIFIER_PARAM, requestData.appId)
            .part(APP_NAME_PARAM, requestData.name)
            .part(APP_DISPLAY_VERSION_PARAM, requestData.displayVersion)
            .part(APP_BUILD_VERSION_PARAM, requestData.buildVersion)
            .part(APP_SOURCE_PARAM, Integer.toString(requestData.source))
            .part(APP_MIN_SDK_VERSION_PARAM, requestData.minSdkVersion)
            .part(APP_BUILT_SDK_VERSION_PARAM, requestData.builtSdkVersion);

    if (!CommonUtils.isNullOrEmpty(requestData.instanceIdentifier)) {
      request.part(APP_INSTANCE_IDENTIFIER_PARAM, requestData.instanceIdentifier);
    }

    // This used to also add two more parts:
    // app[build][libraries][$KIT][version] = $VERSION
    // app[build][libraries][$KIT][type] = $BUILD_TYPE

    return request;
  }
}
