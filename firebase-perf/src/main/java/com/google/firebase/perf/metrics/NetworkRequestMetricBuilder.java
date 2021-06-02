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

package com.google.firebase.perf.metrics;

import static com.google.firebase.perf.network.NetworkRequestMetricBuilderUtil.isAllowedUserAgent;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.firebase.perf.application.AppStateMonitor;
import com.google.firebase.perf.application.AppStateUpdateHandler;
import com.google.firebase.perf.logging.AndroidLogger;
import com.google.firebase.perf.session.PerfSession;
import com.google.firebase.perf.session.SessionAwareObject;
import com.google.firebase.perf.session.SessionManager;
import com.google.firebase.perf.session.gauges.GaugeManager;
import com.google.firebase.perf.transport.TransportManager;
import com.google.firebase.perf.util.Constants;
import com.google.firebase.perf.util.Timer;
import com.google.firebase.perf.util.Utils;
import com.google.firebase.perf.v1.GaugeMetric;
import com.google.firebase.perf.v1.NetworkRequestMetric;
import com.google.firebase.perf.v1.NetworkRequestMetric.HttpMethod;
import com.google.firebase.perf.v1.NetworkRequestMetric.NetworkClientErrorReason;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * This class is responsible for creating and populating the Network Request used to describe
 * network calls.
 *
 * @hide
 */
public final class NetworkRequestMetricBuilder extends AppStateUpdateHandler
    implements SessionAwareObject {

  private static final AndroidLogger logger = AndroidLogger.getInstance();
  private static final char HIGHEST_CONTROL_CHAR = '\u001f';
  private static final char HIGHEST_ASCII_CHAR = '\u007f';

  // TODO(b/177317027): Consider using a Set to avoid adding same PerfSession object
  private final List<PerfSession> sessions;
  private final GaugeManager gaugeManager;
  private final TransportManager transportManager;

  private final NetworkRequestMetric.Builder builder = NetworkRequestMetric.newBuilder();
  private final WeakReference<SessionAwareObject> weakReference = new WeakReference<>(this);

  @Nullable private String userAgent;

  private boolean isReportSent;
  private boolean isManualNetworkRequestMetric;

  @Override
  public void updateSession(PerfSession session) {
    // Note(b/152218504): Being defensive to fix the NPE
    if (session == null) {
      logger.warn("Unable to add new SessionId to the Network Trace. Continuing without it.");
      return;
    }

    if (hasStarted() && !isStopped()) {
      sessions.add(session);
    }
  }

  /** Creates and returns a {@link NetworkRequestMetricBuilder} object. */
  public static NetworkRequestMetricBuilder builder(TransportManager transportManager) {
    return new NetworkRequestMetricBuilder(transportManager);
  }

  /**
   * SDK facing constructor which calls constructor with AppStateMonitor and GaugeManager to
   * initialize them.
   */
  private NetworkRequestMetricBuilder(TransportManager transportManager) {
    this(transportManager, AppStateMonitor.getInstance(), GaugeManager.getInstance());
  }

  /**
   * Constructs and initializes AppState to allow sending {@link NetworkRequestMetric} on
   * completion.
   */
  public NetworkRequestMetricBuilder(
      TransportManager transportManager,
      AppStateMonitor appStateMonitor,
      GaugeManager gaugeManager) {
    super(appStateMonitor);

    this.transportManager = transportManager;
    this.gaugeManager = gaugeManager;
    sessions = Collections.synchronizedList(new ArrayList<>());

    registerForAppState();
  }

  /**
   * Sets the current {@link NetworkRequestMetric} to be manual which means the Trace has to be
   * manually start and stop.
   *
   * @see HttpMetric#start()
   * @see HttpMetric#stop()
   */
  public void setManualNetworkRequestMetric() {
    isManualNetworkRequestMetric = true;
  }

  /** Sets the url for the current {@link NetworkRequestMetric}. */
  public NetworkRequestMetricBuilder setUrl(@Nullable String url) {
    if (url != null) {
      // Strips query parameters/user info if any
      url = Utils.stripSensitiveInfo(url);

      // Limits the URL length to 2000 chars.
      builder.setUrl(Utils.truncateURL(url, Constants.MAX_URL_LENGTH));
    }

    return this;
  }

  /** Gets the url for the current {@link NetworkRequestMetric}. */
  public String getUrl() {
    return builder.getUrl();
  }

  /** Sets the user-agent for the current network request. */
  public NetworkRequestMetricBuilder setUserAgent(@Nullable String userAgent) {
    this.userAgent = userAgent;
    return this;
  }

  /** Sets the httpMethod for the current {@link NetworkRequestMetric}. */
  public NetworkRequestMetricBuilder setHttpMethod(@Nullable String method) {
    if (method != null) {
      HttpMethod httpMethod = HttpMethod.HTTP_METHOD_UNKNOWN;
      switch (method.toUpperCase()) {
        case "GET":
          httpMethod = HttpMethod.GET;
          break;

        case "PUT":
          httpMethod = HttpMethod.PUT;
          break;

        case "POST":
          httpMethod = HttpMethod.POST;
          break;

        case "DELETE":
          httpMethod = HttpMethod.DELETE;
          break;

        case "HEAD":
          httpMethod = HttpMethod.HEAD;
          break;

        case "PATCH":
          httpMethod = HttpMethod.PATCH;
          break;

        case "OPTIONS":
          httpMethod = HttpMethod.OPTIONS;
          break;

        case "TRACE":
          httpMethod = HttpMethod.TRACE;
          break;

        case "CONNECT":
          httpMethod = HttpMethod.CONNECT;
          break;

        default:
          httpMethod = HttpMethod.HTTP_METHOD_UNKNOWN;
          break;
      }
      builder.setHttpMethod(httpMethod);
    }
    return this;
  }

  /** Sets the httpResponseCode for the current {@link NetworkRequestMetric}. */
  public NetworkRequestMetricBuilder setHttpResponseCode(int code) {
    builder.setHttpResponseCode(code);
    return this;
  }

  /** Answers whether the builder has an HttpResponseCode. */
  public boolean hasHttpResponseCode() {
    return builder.hasHttpResponseCode();
  }

  /** Sets the requestPayloadBytes for the current {@link NetworkRequestMetric}. */
  public NetworkRequestMetricBuilder setRequestPayloadBytes(long bytes) {
    builder.setRequestPayloadBytes(bytes);
    return this;
  }

  /** Sets the customAttributes for the current {@link NetworkRequestMetric}. */
  public NetworkRequestMetricBuilder setCustomAttributes(Map<String, String> attributes) {
    builder.clearCustomAttributes().putAllCustomAttributes(attributes);
    return this;
  }

  /**
   * Sets the clientStartTimeUs for the current {@link NetworkRequestMetric}.
   *
   * <p>Note: The start of the request also trigger collection of a single {@link GaugeMetric} data
   * point depending upon the current {@link PerfSession} verbosity.
   *
   * @see GaugeManager#collectGaugeMetricOnce(Timer)
   * @see PerfSession#isGaugeAndEventCollectionEnabled()
   */
  public NetworkRequestMetricBuilder setRequestStartTimeMicros(long time) {
    SessionManager sessionManager = SessionManager.getInstance();
    PerfSession perfSession = sessionManager.perfSession();
    SessionManager.getInstance().registerForSessionUpdates(weakReference);

    builder.setClientStartTimeUs(time);
    updateSession(perfSession);

    if (perfSession.isGaugeAndEventCollectionEnabled()) {
      gaugeManager.collectGaugeMetricOnce(perfSession.getTimer());
    }

    return this;
  }

  /** Sets the timeToRequestCompletedUs for the current {@link NetworkRequestMetric}. */
  public NetworkRequestMetricBuilder setTimeToRequestCompletedMicros(long time) {
    builder.setTimeToRequestCompletedUs(time);
    return this;
  }

  /** Sets the timeToResponseInitiatedUs for the current {@link NetworkRequestMetric}. */
  public NetworkRequestMetricBuilder setTimeToResponseInitiatedMicros(long time) {
    builder.setTimeToResponseInitiatedUs(time);
    return this;
  }

  /** Gets the timeToResponseInitiatedUs for the current {@link NetworkRequestMetric}. */
  public long getTimeToResponseInitiatedMicros() {
    return builder.getTimeToResponseInitiatedUs();
  }

  /**
   * Sets the timeToResponseCompletedUs for the current {@link NetworkRequestMetric}.
   *
   * <p>Note: The end of the request also trigger collection of a single {@link GaugeMetric} data
   * point depending upon the current {@link PerfSession} Verbosity.
   *
   * @see GaugeManager#collectGaugeMetricOnce(Timer)
   * @see PerfSession#isGaugeAndEventCollectionEnabled()
   */
  public NetworkRequestMetricBuilder setTimeToResponseCompletedMicros(long time) {
    builder.setTimeToResponseCompletedUs(time);

    if (SessionManager.getInstance().perfSession().isGaugeAndEventCollectionEnabled()) {
      gaugeManager.collectGaugeMetricOnce(SessionManager.getInstance().perfSession().getTimer());
    }

    return this;
  }

  /** Sets the responsePayloadBytes for the current {@link NetworkRequestMetric}. */
  public NetworkRequestMetricBuilder setResponsePayloadBytes(long bytes) {
    builder.setResponsePayloadBytes(bytes);
    return this;
  }

  /** Sets the responseContentType for the current {@link NetworkRequestMetric}. */
  public NetworkRequestMetricBuilder setResponseContentType(@Nullable String contentType) {
    if (contentType == null) {
      builder.clearResponseContentType();
      return this;
    }

    if (isValidContentType(contentType)) {
      builder.setResponseContentType(contentType);
    } else {
      logger.warn("The content type of the response is not a valid content-type:" + contentType);
    }
    return this;
  }

  /**
   * Sets the networkClientErrorReason to {@link NetworkClientErrorReason#GENERIC_CLIENT_ERROR} for
   * the current {@link NetworkRequestMetric}.
   */
  public NetworkRequestMetricBuilder setNetworkClientErrorReason() {
    builder.setNetworkClientErrorReason(NetworkClientErrorReason.GENERIC_CLIENT_ERROR);
    return this;
  }

  /** Builds the current {@link NetworkRequestMetric}. */
  public NetworkRequestMetric build() {
    SessionManager.getInstance().unregisterForSessionUpdates(weakReference);
    unregisterForAppState();

    com.google.firebase.perf.v1.PerfSession[] perfSessions =
        PerfSession.buildAndSort(getSessions());
    if (perfSessions != null) {
      builder.addAllPerfSessions(Arrays.asList(perfSessions));
    }

    NetworkRequestMetric metric = builder.build();

    if (!isAllowedUserAgent(userAgent)) {
      logger.debug("Dropping network request from a 'User-Agent' that is not allowed");
      return metric;
    }

    if (!isReportSent) {
      transportManager.log(metric, getAppState());
      isReportSent = true;
      return metric;
    }

    if (isManualNetworkRequestMetric) {
      logger.debug(
          "This metric has already been queued for transmission.  "
              + "Please create a new HttpMetric for each request/response");
    }

    return metric;
  }

  /** Checks if the network request has stopped. */
  private boolean isStopped() {
    return builder.hasTimeToResponseCompletedUs();
  }

  /** Checks if the network request has started. */
  private boolean hasStarted() {
    return builder.hasClientStartTimeUs();
  }

  @VisibleForTesting
  List<PerfSession> getSessions() {
    synchronized (sessions) {
      ArrayList<PerfSession> sessionsListCopy = new ArrayList<>();
      // To be extra safe, filter out nulls before returning the list
      // (b/171730176)
      for (PerfSession session : sessions) {
        if (session != null) {
          sessionsListCopy.add(session);
        }
      }
      return Collections.unmodifiableList(sessionsListCopy);
    }
  }

  @VisibleForTesting
  boolean isReportSent() {
    return isReportSent;
  }

  @VisibleForTesting
  void setReportSent() {
    isReportSent = true;
  }

  @VisibleForTesting
  void clearBuilderFields() {
    builder.clear();
  }

  /**
   * Guava com.google.common.net.MediaType can validate content-type, but its license is not suited
   * to use in SDK. Here we only check content-type length and disallow control characters.
   */
  private static boolean isValidContentType(String str) {
    // Content-type should be no longer than 128 characters.
    if (str.length() > Constants.MAX_CONTENT_TYPE_LENGTH) {
      return false;
    }
    for (int i = 0; i < str.length(); ++i) {
      char c = str.charAt(i);
      if (c <= HIGHEST_CONTROL_CHAR || c > HIGHEST_ASCII_CHAR) {
        return false;
      }
    }
    return true;
  }
}
