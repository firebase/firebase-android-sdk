// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.perf.internal;

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

  private final NetworkRequestMetric mNetworkMetric;
  private final Context context;

  FirebasePerfNetworkValidator(NetworkRequestMetric networkRequestMetric, Context context) {
    this.context = context;
    mNetworkMetric = networkRequestMetric;
  }

  /**
   * This method validates a network metric, it validates if the url and host are valid.
   *
   * @return a boolean which indicates if the trace is valid.
   */
  public boolean isValidPerfMetric() {
    if (isEmptyUrl(mNetworkMetric.getUrl())) {
      logger.info("URL is missing:" + mNetworkMetric.getUrl());
      return false;
    }
    URI uri = getResultUrl(mNetworkMetric.getUrl());
    if (uri == null) {
      logger.info("URL cannot be parsed");
      return false;
    }

    if (!isAllowlisted(uri, context)) {
      logger.info("URL fails allowlist rule: " + uri);
      return false;
    }
    if (!isValidHost(uri.getHost())) {
      logger.info("URL host is null or invalid");
      return false;
    }
    if (!isValidScheme(uri.getScheme())) {
      logger.info("URL scheme is null or invalid");
      return false;
    }
    if (!isValidUserInfo(uri.getUserInfo())) {
      logger.info("URL user info is null");
      return false;
    }
    if (!isValidPort(uri.getPort())) {
      logger.info("URL port is less than or equal to 0");
      return false;
    }
    if (!isValidHttpMethod(
        mNetworkMetric.hasHttpMethod() ? mNetworkMetric.getHttpMethod() : null)) {
      logger.info("HTTP Method is null or invalid: " + mNetworkMetric.getHttpMethod());
      return false;
    }
    if (mNetworkMetric.hasHttpResponseCode()
        && !isValidHttpResponseCode(mNetworkMetric.getHttpResponseCode())) {
      logger.info("HTTP ResponseCode is a negative value:" + mNetworkMetric.getHttpResponseCode());
      return false;
    }
    if (mNetworkMetric.hasRequestPayloadBytes()
        && !isValidPayload(mNetworkMetric.getRequestPayloadBytes())) {
      logger.info("Request Payload is a negative value:" + mNetworkMetric.getRequestPayloadBytes());
      return false;
    }
    if (mNetworkMetric.hasResponsePayloadBytes()
        && !isValidPayload(mNetworkMetric.getResponsePayloadBytes())) {
      logger.info(
          "Response Payload is a negative value:" + mNetworkMetric.getResponsePayloadBytes());
      return false;
    }
    if (!mNetworkMetric.hasClientStartTimeUs() || mNetworkMetric.getClientStartTimeUs() <= 0) {
      logger.info(
          "Start time of the request is null, or zero, or a negative value:"
              + mNetworkMetric.getClientStartTimeUs());
      return false;
    }
    if (mNetworkMetric.hasTimeToRequestCompletedUs()
        && !isValidTime(mNetworkMetric.getTimeToRequestCompletedUs())) {
      logger.info(
          "Time to complete the request is a negative value:"
              + mNetworkMetric.getTimeToRequestCompletedUs());
      return false;
    }
    if (mNetworkMetric.hasTimeToResponseInitiatedUs()
        && !isValidTime(mNetworkMetric.getTimeToResponseInitiatedUs())) {
      logger.info(
          "Time from the start of the request to the start of the response is null or a "
              + "negative value:"
              + mNetworkMetric.getTimeToResponseInitiatedUs());
      return false;
    }
    if (!mNetworkMetric.hasTimeToResponseCompletedUs()
        || mNetworkMetric.getTimeToResponseCompletedUs() <= 0) {
      logger.info(
          "Time from the start of the request to the end of the response is null, negative or "
              + "zero:"
              + mNetworkMetric.getTimeToResponseCompletedUs());
      return false;
    }
    // Don't log any requests with a connection error set
    if (!mNetworkMetric.hasHttpResponseCode()) {
      logger.info("Did not receive a HTTP Response Code");
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
