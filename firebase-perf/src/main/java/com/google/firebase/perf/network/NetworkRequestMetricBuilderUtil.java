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

package com.google.firebase.perf.network;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.perf.logging.AndroidLogger;
import com.google.firebase.perf.metrics.NetworkRequestMetricBuilder;
import java.util.regex.Pattern;
import org.apache.http.Header;
import org.apache.http.HttpMessage;
import org.apache.http.HttpResponse;

/** Functions that set values in the given network request metric report */
public final class NetworkRequestMetricBuilderUtil {

  // Flg SDK "User-Agent" pattern (https://bityl.co/4IPN).
  private static final Pattern FLG_USER_AGENT_PATTERN =
      Pattern.compile("(^|.*\\s)datatransport/\\S+ android/($|\\s.*)");

  private NetworkRequestMetricBuilderUtil() {}

  /**
   * Gets the content length from the headers of either the request or response
   *
   * @param request
   * @return Long content-length or null if the content-length cannot be found
   */
  public static Long getApacheHttpMessageContentLength(@NonNull HttpMessage request) {
    try {
      Header contentLengthHeader = request.getFirstHeader("content-length");
      if (contentLengthHeader != null) {
        return Long.parseLong(contentLengthHeader.getValue());
      }
    } catch (NumberFormatException e) {
      AndroidLogger.getInstance().debug("The content-length value is not a valid number");
    }
    return null;
  }

  /**
   * Gets the content type from the header of the response
   *
   * @param response
   * @return String content-type or null if the content-type cannot be found
   */
  public static String getApacheHttpResponseContentType(@NonNull HttpResponse response) {
    Header responseContentTypeHeader = response.getFirstHeader("content-type");
    if (responseContentTypeHeader != null) {
      String contentType = responseContentTypeHeader.getValue();
      if (contentType != null) {
        return contentType;
      }
    }
    return null;
  }

  /**
   * logs a network error, usually called in the "catch" clause
   *
   * @param builder
   */
  public static void logError(NetworkRequestMetricBuilder builder) {
    if (!builder.hasHttpResponseCode()) {
      builder.setNetworkClientErrorReason();
    }
    builder.build();
  }

  /** Returns whether the {@code userAgent} is allowed. */
  public static boolean isAllowedUserAgent(@Nullable String userAgent) {
    // All network requests from Flg SDK should be filtered out to avoid endless loop of network
    // requests dispatch (Fireperf -> Flg -> Fireperf -> Flg ...).
    // Refer: go/deny-flg-requests-from-fireperf-sdk
    return userAgent == null || !FLG_USER_AGENT_PATTERN.matcher(userAgent).matches();
  }
}
