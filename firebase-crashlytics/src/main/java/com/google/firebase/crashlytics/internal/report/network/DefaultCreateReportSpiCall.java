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

import com.google.firebase.crashlytics.internal.Logger;
import com.google.firebase.crashlytics.internal.common.AbstractSpiCall;
import com.google.firebase.crashlytics.internal.common.ResponseParser;
import com.google.firebase.crashlytics.internal.network.HttpMethod;
import com.google.firebase.crashlytics.internal.network.HttpRequest;
import com.google.firebase.crashlytics.internal.network.HttpRequestFactory;
import com.google.firebase.crashlytics.internal.network.HttpResponse;
import com.google.firebase.crashlytics.internal.report.model.CreateReportRequest;
import com.google.firebase.crashlytics.internal.report.model.Report;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;

/** Default implementation of the {@link CreateReportSpiCall} */
public class DefaultCreateReportSpiCall extends AbstractSpiCall implements CreateReportSpiCall {

  static final String MULTI_FILE_PARAM = "report[file";
  static final String FILE_PARAM = "report[file]";
  static final String IDENTIFIER_PARAM = "report[identifier]";
  static final String FILE_CONTENT_TYPE = "application/octet-stream";

  private final String version;

  /**
   * Create a new POST call on the provided <code>url</code>
   *
   * @param protocolAndHostOverride {@link String} to use in place of whatever protocol and host are
   *     present in the provide <code>url</code>.
   * @param url {@link String} url on which to make the Create App call.
   * @param requestFactory
   * @param version {@link String} number to include in request headers.
   */
  public DefaultCreateReportSpiCall(
      String protocolAndHostOverride,
      String url,
      HttpRequestFactory requestFactory,
      String version) {
    this(protocolAndHostOverride, url, requestFactory, HttpMethod.POST, version);
  }

  /**
   * Meant for use in testing. Prefer {@link #DefaultCreateReportSpiCall(String, String,
   * com.google.firebase.crashlytics.internal.network.HttpRequestFactory)} for normal use.
   *
   * @param protocolAndHostOverride
   * @param url
   * @param requestFactory
   * @param method
   */
  DefaultCreateReportSpiCall(
      String protocolAndHostOverride,
      String url,
      HttpRequestFactory requestFactory,
      HttpMethod method,
      String version) {
    super(protocolAndHostOverride, url, requestFactory, method);
    this.version = version;
  }

  @Override
  public boolean invoke(CreateReportRequest requestData, boolean dataCollectionToken) {
    if (!dataCollectionToken) {
      throw new RuntimeException("An invalid data collection token was used.");
    }

    HttpRequest httpRequest = getHttpRequest();
    httpRequest = applyHeadersTo(httpRequest, requestData);
    httpRequest = applyMultipartDataTo(httpRequest, requestData.report);

    Logger.getLogger().d("Sending report to: " + getUrl());

    try {
      final HttpResponse httpResponse = httpRequest.execute();

      final int statusCode = httpResponse.code();

      Logger.getLogger().d("Create report request ID: " + httpResponse.header(HEADER_REQUEST_ID));
      Logger.getLogger().d("Result was: " + statusCode);

      return ResponseParser.ResponseActionDiscard == ResponseParser.parse(statusCode);
    } catch (IOException ioe) {
      Logger.getLogger().e("Create report HTTP request failed.", ioe);
      throw new RuntimeException(ioe);
    }
  }

  private HttpRequest applyHeadersTo(HttpRequest request, CreateReportRequest requestData) {
    request =
        request
            .header(HEADER_GOOGLE_APP_ID, requestData.googleAppId)
            .header(HEADER_CLIENT_TYPE, ANDROID_CLIENT_TYPE)
            .header(HEADER_CLIENT_VERSION, version);

    // A Report can have custom headers that it wants applied. Add those if present.
    final Map<String, String> customHeaders = requestData.report.getCustomHeaders();

    for (Entry<String, String> entry : customHeaders.entrySet()) {
      request = request.header(entry);
    }
    return request;
  }

  private HttpRequest applyMultipartDataTo(HttpRequest request, Report report) {
    request = request.part(IDENTIFIER_PARAM, report.getIdentifier());

    if (report.getFiles().length == 1) {
      Logger.getLogger()
          .d("Adding single file " + report.getFileName() + " to report " + report.getIdentifier());
      return request.part(FILE_PARAM, report.getFileName(), FILE_CONTENT_TYPE, report.getFile());
    }

    int i = 0;
    for (File file : report.getFiles()) {
      Logger.getLogger()
          .d("Adding file " + file.getName() + " to report " + report.getIdentifier());
      request = request.part(MULTI_FILE_PARAM + i + "]", file.getName(), FILE_CONTENT_TYPE, file);
      i++;
    }

    return request;
  }
}
