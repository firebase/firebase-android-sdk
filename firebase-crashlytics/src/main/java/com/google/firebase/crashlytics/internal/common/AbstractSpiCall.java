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

import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.firebase.crashlytics.internal.network.HttpMethod;
import com.google.firebase.crashlytics.internal.network.HttpRequest;
import com.google.firebase.crashlytics.internal.network.HttpRequestFactory;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Abstract class that provides a reusable base for implementing SPI web calls on top of {@link
 * HttpRequest}.
 */
public abstract class AbstractSpiCall {

  public static final String HEADER_ORG_ID = "X-CRASHLYTICS-ORG-ID";
  public static final String HEADER_GOOGLE_APP_ID = "X-CRASHLYTICS-GOOGLE-APP-ID";
  public static final String HEADER_DEVELOPER_TOKEN = "X-CRASHLYTICS-DEVELOPER-TOKEN";
  public static final String HEADER_CLIENT_TYPE = "X-CRASHLYTICS-API-CLIENT-TYPE";
  public static final String HEADER_CLIENT_VERSION = "X-CRASHLYTICS-API-CLIENT-VERSION";
  public static final String HEADER_REQUEST_ID = "X-REQUEST-ID";

  public static final String HEADER_USER_AGENT = "User-Agent";
  public static final String HEADER_ACCEPT = "Accept";
  public static final String CRASHLYTICS_USER_AGENT = "Crashlytics Android SDK/";
  public static final String ACCEPT_JSON_VALUE = "application/json";

  public static final String ANDROID_CLIENT_TYPE = "android";

  private static final Pattern PROTOCOL_AND_HOST_PATTERN =
      Pattern.compile("http(s?)://[^\\/]+", Pattern.CASE_INSENSITIVE);

  private final String url;
  private final HttpRequestFactory requestFactory;
  private final HttpMethod method;
  private final String protocolAndHostOverride;

  /**
   * Constructs an SPI web call for the provided URL {@link String} and {@link HttpMethod}.
   *
   * <p>The ability to provide a different kind of {@link HttpRequestFactory} should really only be
   * used in testing.
   *
   * @param protocolAndHostOverride {@link String} representing the protocol and host that should be
   *     used in place of those present in the <code>url</code> string. A value of <code>null</code>
   *     or an empty string will be ignored, allowing the protocol and host of the <code>url</code>
   *     to be used.
   * @param url {@link String} representing the base URL to contact in this call. It should
   *     <b>not</b> include any query params in the case of a {@link HttpMethod#GET} request. Query
   *     params should be passed to {@link #getHttpRequest(java.util.Map)} by the subclass
   *     implementation.
   * @param requestFactory {@link HttpRequestFactory} to use in building the {@link HttpRequest}.
   * @param method {@link HttpMethod} HTTP request method
   */
  public AbstractSpiCall(
      String protocolAndHostOverride,
      String url,
      HttpRequestFactory requestFactory,
      HttpMethod method) {
    if (url == null) {
      throw new IllegalArgumentException("url must not be null.");
    }
    if (requestFactory == null) {
      throw new IllegalArgumentException("requestFactory must not be null.");
    }
    this.protocolAndHostOverride = protocolAndHostOverride;
    this.url = overrideProtocolAndHost(url);
    this.requestFactory = requestFactory;
    this.method = method;
  }

  protected String getUrl() {
    return url;
  }

  /**
   * Returns a {@link HttpRequest} for this call, created by the {@link HttpRequestFactory} provided
   * to the constructor. This method uses empty {@link java.util.Map} of query params, making it
   * unsuitable for {@link HttpMethod#GET} that need to specify query params.
   *
   * @return {@link HttpRequest} to be used in further building the HTTP request.
   */
  protected HttpRequest getHttpRequest() {
    return getHttpRequest(Collections.<String, String>emptyMap());
  }

  /**
   * Returns a {@link HttpRequest} for this call, created by the {@link HttpRequestFactory} provided
   * to the constructor. This method takes the provided {@link java.util.Map} of query params, URL
   * escapes them, and applies them to the end of the url {@link String} provided to the
   * constructor.
   *
   * @param queryParams {@link java.util.Map} of key-value pairs to be used as query params and
   *     appended to the end of the url {@link String} provided to the constructor.
   * @return {@link HttpRequest} to be used in further building the HTTP request.
   */
  protected HttpRequest getHttpRequest(Map<String, String> queryParams) {
    final HttpRequest httpRequest = requestFactory.buildHttpRequest(method, getUrl(), queryParams);
    return httpRequest
        .header(HEADER_USER_AGENT, CRASHLYTICS_USER_AGENT + CrashlyticsCore.getVersion())
        .header("X-CRASHLYTICS-DEVELOPER-TOKEN", "470fa2b4ae81cd56ecbcda9735803434cec591fa");
  }

  /**
   * Acts as the hook for supporting client-side re-write of our web request protocol and host, as
   * requested by Square. If the strings file value provided by {@link
   * FirebaseCrashlytics#getOverridenSpiEndpoint()} is non-null and non-empty, we'll rewrite the
   * provided <code>url</code>, replacing the protocol and host portions
   * (theprotocol://the.host/wont/touch/the/rest) with the value returned by that method.
   *
   * @param url URL {@link String} to potentially be rewritten
   * @return URL new URL {@link String}
   */
  private String overrideProtocolAndHost(String url) {
    String toReturn = url;

    if (!CommonUtils.isNullOrEmpty(protocolAndHostOverride)) {
      toReturn = PROTOCOL_AND_HOST_PATTERN.matcher(url).replaceFirst(protocolAndHostOverride);
    }

    return toReturn;
  }
}
