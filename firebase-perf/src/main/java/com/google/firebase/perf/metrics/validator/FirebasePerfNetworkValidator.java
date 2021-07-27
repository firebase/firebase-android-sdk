// Copyright 2021 Google LLC
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

package com.google.firebase.perf.metrics.validator;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.perf.logging.AndroidLogger;
import com.google.firebase.perf.util.Constants;
import com.google.firebase.perf.util.URLAllowlist;
import com.google.firebase.perf.v1.NetworkRequestMetric;
import com.google.firebase.perf.v1.NetworkRequestMetric.HttpMethod;
import java.net.URI;

/** Utility class that provides static methods for validating network logs entries. */
final class FirebasePerfNetworkValidator extends PerfMetricValidator {

  private static final AndroidLogger logger = AndroidLogger.getInstance();
  private static final String HTTP_SCHEMA = "http";
  private static final String HTTPS = "https";
  // This is the value used by java.net.URI when a port was not specified when a URI is created.
  private static final int EMPTY_PORT = -1;

  private final NetworkRequestMetric networkMetric;
  private final Context appContext;

  FirebasePerfNetworkValidator(NetworkRequestMetric networkRequestMetric, Context appContext) {
    this.appContext = appContext;
    networkMetric = networkRequestMetric;
  }

  /**
   * This method validates a network metric, it validates if the url and host are valid.
   *
   * @return a boolean which indicates if the trace is valid.
   */
  public boolean isValidPerfMetric() {
    if (isEmptyUrl(networkMetric.getUrl())) {
      logger.warn("URL is missing:" + networkMetric.getUrl());
      return false;
    }
    URI uri = getResultUrl(networkMetric.getUrl());
    if (uri == null) {
      logger.warn("URL cannot be parsed");
      return false;
    }

    if (!isAllowlisted(uri, appContext)) {
      logger.warn("URL fails allowlist rule: " + uri);
      return false;
    }
    if (!isValidHost(uri.getHost())) {
      logger.warn("URL host is null or invalid");
      return false;
    }
    if (!isValidScheme(uri.getScheme())) {
      logger.warn("URL scheme is null or invalid");
      return false;
    }
    if (!isValidUserInfo(uri.getUserInfo())) {
      logger.warn("URL user info is null");
      return false;
    }
    if (!isValidPort(uri.getPort())) {
      logger.warn("URL port is less than or equal to 0");
      return false;
    }
    if (!isValidHttpMethod(networkMetric.hasHttpMethod() ? networkMetric.getHttpMethod() : null)) {
      logger.warn("HTTP Method is null or invalid: " + networkMetric.getHttpMethod());
      return false;
    }
    if (networkMetric.hasHttpResponseCode()
        && !isValidHttpResponseCode(networkMetric.getHttpResponseCode())) {
      logger.warn("HTTP ResponseCode is a negative value:" + networkMetric.getHttpResponseCode());
      return false;
    }
    if (networkMetric.hasRequestPayloadBytes()
        && !isValidPayload(networkMetric.getRequestPayloadBytes())) {
      logger.warn("Request Payload is a negative value:" + networkMetric.getRequestPayloadBytes());
      return false;
    }
    if (networkMetric.hasResponsePayloadBytes()
        && !isValidPayload(networkMetric.getResponsePayloadBytes())) {
      logger.warn(
          "Response Payload is a negative value:" + networkMetric.getResponsePayloadBytes());
      return false;
    }
    if (!networkMetric.hasClientStartTimeUs() || networkMetric.getClientStartTimeUs() <= 0) {
      logger.warn(
          "Start time of the request is null, or zero, or a negative value:"
              + networkMetric.getClientStartTimeUs());
      return false;
    }
    if (networkMetric.hasTimeToRequestCompletedUs()
        && !isValidTime(networkMetric.getTimeToRequestCompletedUs())) {
      logger.warn(
          "Time to complete the request is a negative value:"
              + networkMetric.getTimeToRequestCompletedUs());
      return false;
    }
    if (networkMetric.hasTimeToResponseInitiatedUs()
        && !isValidTime(networkMetric.getTimeToResponseInitiatedUs())) {
      logger.warn(
          "Time from the start of the request to the start of the response is null or a "
              + "negative value:"
              + networkMetric.getTimeToResponseInitiatedUs());
      return false;
    }
    if (!networkMetric.hasTimeToResponseCompletedUs()
        || networkMetric.getTimeToResponseCompletedUs() <= 0) {
      logger.warn(
          "Time from the start of the request to the end of the response is null, negative or "
              + "zero:"
              + networkMetric.getTimeToResponseCompletedUs());
      return false;
    }
    // Don't log any requests with a connection error set
    if (!networkMetric.hasHttpResponseCode()) {
      logger.warn("Did not receive a HTTP Response Code");
      return false;
    }
    return true;
  }

  private boolean isEmptyUrl(@Nullable String url) {
    return isBlank(url);
  }

  @Nullable
  private URI getResultUrl(@Nullable String url) {
    if (url == null) {
      return null;
    }
    try {
      return URI.create(url);
    } catch (IllegalArgumentException | IllegalStateException e) {
      logger.warn("getResultUrl throws exception %s", e.getMessage());
    }
    return null;
  }

  private boolean isAllowlisted(@Nullable URI uri, @NonNull Context context) {
    if (uri == null) {
      return false;
    }
    return URLAllowlist.isURLAllowlisted(uri, context);
  }

  private boolean isValidHost(@Nullable String host) {
    return host != null && !isBlank(host) && host.length() <= Constants.MAX_HOST_LENGTH;
  }

  private boolean isValidPort(int port) {
    return port == EMPTY_PORT || port > 0;
  }

  private boolean isValidUserInfo(@Nullable String userInfo) {
    return userInfo == null;
  }

  private boolean isValidScheme(@Nullable String scheme) {
    if (scheme == null) {
      return false;
    }
    return HTTP_SCHEMA.equalsIgnoreCase(scheme) || HTTPS.equalsIgnoreCase(scheme);
  }

  boolean isValidHttpMethod(@Nullable HttpMethod httpMethod) {
    return httpMethod != null && httpMethod != HttpMethod.HTTP_METHOD_UNKNOWN;
  }

  private boolean isValidHttpResponseCode(int responseCode) {
    return responseCode > 0;
  }

  private boolean isValidTime(long time) {
    return time >= 0;
  }

  private boolean isValidPayload(long payload) {
    return payload >= 0;
  }

  private boolean isBlank(@Nullable String str) {
    if (str == null) {
      return true;
    }
    str = str.trim();
    return str.isEmpty();
  }
}
