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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import com.google.firebase.perf.FirebasePerformanceTestBase;
import com.google.firebase.perf.application.AppStateMonitor;
import com.google.firebase.perf.session.PerfSession;
import com.google.firebase.perf.session.SessionManager;
import com.google.firebase.perf.session.gauges.GaugeManager;
import com.google.firebase.perf.transport.TransportManager;
import com.google.firebase.perf.util.Constants;
import com.google.firebase.perf.util.Timer;
import com.google.firebase.perf.v1.ApplicationProcessState;
import com.google.firebase.perf.v1.NetworkRequestMetric;
import com.google.firebase.perf.v1.NetworkRequestMetric.HttpMethod;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for {@link com.google.firebase.perf.metrics.NetworkRequestMetricBuilder}. */
@RunWith(RobolectricTestRunner.class)
public class NetworkRequestMetricBuilderTest extends FirebasePerformanceTestBase {

  @Mock private TransportManager mockTransportManager;
  @Mock private GaugeManager mockGaugeManager;
  @Mock private AppStateMonitor mockAppStateMonitor;

  private NetworkRequestMetricBuilder networkRequestBuilder;

  @Before
  public void setUp() {
    initMocks(this);
    networkRequestBuilder =
        new NetworkRequestMetricBuilder(
            mockTransportManager, mockAppStateMonitor, mockGaugeManager);
  }

  @Test
  public void testSetUrl() {
    String url = "www.google.com";
    NetworkRequestMetric metric = networkRequestBuilder.setUrl(url).build();
    assertThat(metric.getUrl()).isEqualTo(url);
  }

  @Test
  public void testIsReportSent() {
    assertThat(networkRequestBuilder.isReportSent()).isFalse();
  }

  @Test
  public void testSetReportSent() {
    networkRequestBuilder.setReportSent();
    assertThat(networkRequestBuilder.isReportSent()).isTrue();
  }

  @Test
  public void testSetHttpMethod() {
    String type = "GET";
    NetworkRequestMetric metric = networkRequestBuilder.setHttpMethod(type).build();
    assertThat(metric.getHttpMethod()).isEqualTo(HttpMethod.GET);

    type = "PUT";
    NetworkRequestMetric metric1 = networkRequestBuilder.setHttpMethod(type).build();
    assertThat(metric1.getHttpMethod()).isEqualTo(HttpMethod.PUT);

    type = "POST";
    NetworkRequestMetric metric2 = networkRequestBuilder.setHttpMethod(type).build();
    assertThat(metric2.getHttpMethod()).isEqualTo(HttpMethod.POST);

    type = "DELETE";
    NetworkRequestMetric metric3 = networkRequestBuilder.setHttpMethod(type).build();
    assertThat(metric3.getHttpMethod()).isEqualTo(HttpMethod.DELETE);

    type = "POST";
    NetworkRequestMetric metric4 = networkRequestBuilder.setHttpMethod(type).build();
    assertThat(metric4.getHttpMethod()).isEqualTo(HttpMethod.POST);

    type = "HEAD";
    NetworkRequestMetric metric5 = networkRequestBuilder.setHttpMethod(type).build();
    assertThat(metric5.getHttpMethod()).isEqualTo(HttpMethod.HEAD);

    type = "PATCH";
    NetworkRequestMetric metric6 = networkRequestBuilder.setHttpMethod(type).build();
    assertThat(metric6.getHttpMethod()).isEqualTo(HttpMethod.PATCH);

    type = "OPTIONS";
    NetworkRequestMetric metric7 = networkRequestBuilder.setHttpMethod(type).build();
    assertThat(metric7.getHttpMethod()).isEqualTo(HttpMethod.OPTIONS);

    type = "TRACE";
    NetworkRequestMetric metric8 = networkRequestBuilder.setHttpMethod(type).build();
    assertThat(metric8.getHttpMethod()).isEqualTo(HttpMethod.TRACE);

    type = "CONNECT";
    NetworkRequestMetric metric9 = networkRequestBuilder.setHttpMethod(type).build();
    assertThat(metric9.getHttpMethod()).isEqualTo(HttpMethod.CONNECT);

    type = "UNKNOWN";
    NetworkRequestMetric metric10 = networkRequestBuilder.setHttpMethod(type).build();
    assertThat(metric10.getHttpMethod()).isEqualTo(HttpMethod.HTTP_METHOD_UNKNOWN);
  }

  @Test
  public void testSetRequestPayloadBytes() {
    long bytes = 256;
    NetworkRequestMetric metric = networkRequestBuilder.setRequestPayloadBytes(bytes).build();
    assertThat(metric.getRequestPayloadBytes()).isEqualTo(bytes);
  }

  @Test
  public void testSetResponsePayloadBytes() {
    long bytes = 256;
    NetworkRequestMetric metric = networkRequestBuilder.setResponsePayloadBytes(bytes).build();
    assertThat(metric.getResponsePayloadBytes()).isEqualTo(bytes);
  }

  @Test
  public void testSetHttpResponseCode() {
    int code = 200;
    NetworkRequestMetric metric = networkRequestBuilder.setHttpResponseCode(code).build();
    assertThat(metric.getHttpResponseCode()).isEqualTo(code);
  }

  @Test
  public void testSetResponseContentType() {
    String contentType = "text/html";
    NetworkRequestMetric metric = networkRequestBuilder.setResponseContentType(contentType).build();
    assertThat(metric.getResponseContentType()).isEqualTo(contentType);
  }

  @Test
  public void testSetRequestStartTimeMicros() {
    long time = 2000;
    NetworkRequestMetric metric = networkRequestBuilder.setRequestStartTimeMicros(time).build();
    assertThat(metric.getClientStartTimeUs()).isEqualTo(time);
  }

  @Test
  public void testSetRequestEndTimeMicros() {
    long time = 2000;
    NetworkRequestMetric metric = networkRequestBuilder.setTimeToRequestCompletedMicros(time).build();
    assertThat(metric.getTimeToRequestCompletedUs()).isEqualTo(time);
  }

  @Test
  public void testSetTimeToResponseInitiatedMicros() {
    long time = 2000;
    NetworkRequestMetric metric = networkRequestBuilder.setTimeToResponseInitiatedMicros(time).build();
    assertThat(metric.getTimeToResponseInitiatedUs()).isEqualTo(time);
  }

  @Test
  public void testSetTimeToResponseCompletedMicros() {
    long time = 2000;
    NetworkRequestMetric metric = networkRequestBuilder.setTimeToResponseCompletedMicros(time).build();
    assertThat(metric.getTimeToResponseCompletedUs()).isEqualTo(time);
  }

  @Test
  public void testAllNullFields() {
    NetworkRequestMetric metric = networkRequestBuilder.build();

    assertThat(metric.hasUrl()).isFalse();
    assertThat(metric.hasHttpMethod()).isFalse();
    assertThat(metric.hasRequestPayloadBytes()).isFalse();
    assertThat(metric.hasResponsePayloadBytes()).isFalse();
    assertThat(metric.hasNetworkClientErrorReason()).isFalse();
    assertThat(metric.hasHttpResponseCode()).isFalse();
    assertThat(metric.hasResponseContentType()).isFalse();
    assertThat(metric.hasClientStartTimeUs()).isFalse();
    assertThat(metric.hasTimeToRequestCompletedUs()).isFalse();
    assertThat(metric.hasTimeToResponseInitiatedUs()).isFalse();
    assertThat(metric.hasTimeToResponseCompletedUs()).isFalse();
  }

  @Test
  public void testNoSessionIdsInNetworkRequestMetricWhenNotStarted() {
    networkRequestBuilder.setUrl(/* url= */ "www.google.com");

    assertThat(networkRequestBuilder.getSessions()).isNotNull();
    assertThat(networkRequestBuilder.getSessions()).isEmpty();
  }

  @Test
  public void testSessionIdsInNetworkRequestMetricAfterStarted() {
    networkRequestBuilder.setUrl(/* url= */ "www.google.com");
    networkRequestBuilder.setRequestStartTimeMicros(/* time= */ 2000);

    assertThat(networkRequestBuilder.getSessions()).isNotNull();
    assertThat(networkRequestBuilder.getSessions()).isNotEmpty();
  }

  @Test
  public void testSessionIdAdditionInNetworkRequestMetric() {
    NetworkRequestMetricBuilder metricBuilder =
        NetworkRequestMetricBuilder.builder(mockTransportManager);
    metricBuilder.setRequestStartTimeMicros(/* time= */ 2000);

    assertThat(this.networkRequestBuilder.getSessions()).isNotNull();
    assertThat(this.networkRequestBuilder.getSessions()).isEmpty();

    int numberOfSessionIds = metricBuilder.getSessions().size();
    SessionManager.getInstance().updatePerfSession(ApplicationProcessState.FOREGROUND);

    assertThat(metricBuilder.getSessions().size()).isEqualTo(numberOfSessionIds + 1);
  }

  @Test
  public void testSessionIdNotAddedIfPerfSessionIsNull() {
    NetworkRequestMetricBuilder metricBuilder =
        NetworkRequestMetricBuilder.builder(mockTransportManager);
    metricBuilder.setRequestStartTimeMicros(/* time= */ 2000);

    assertThat(this.networkRequestBuilder.getSessions()).isNotNull();
    assertThat(this.networkRequestBuilder.getSessions()).isEmpty();

    int numberOfSessionIds = metricBuilder.getSessions().size();

    new SessionManager(mock(GaugeManager.class), null, mock(AppStateMonitor.class))
        .onUpdateAppState(ApplicationProcessState.FOREGROUND);

    assertThat(metricBuilder.getSessions()).hasSize(numberOfSessionIds);
  }

  @Test
  public void testSessionIdNotAddedForBackgroundInNetworkRequestMetric() {
    NetworkRequestMetricBuilder metricBuilder =
        NetworkRequestMetricBuilder.builder(mockTransportManager);
    metricBuilder.setRequestStartTimeMicros(/* time= */ 2000);

    assertThat(this.networkRequestBuilder.getSessions()).isNotNull();
    assertThat(this.networkRequestBuilder.getSessions()).isEmpty();

    int numberOfSessionIds = metricBuilder.getSessions().size();
    SessionManager.getInstance().onUpdateAppState(ApplicationProcessState.BACKGROUND);
    assertThat(metricBuilder.getSessions().size() == numberOfSessionIds).isTrue();
  }

  @Test
  public void testSetRequestStartTimeMicrosTriggerSingleGaugeCollectionOnVerboseSession() {
    forceVerboseSession();
    networkRequestBuilder.setRequestStartTimeMicros(2000).build();
    verify(mockGaugeManager).collectGaugeMetricOnce(ArgumentMatchers.nullable(Timer.class));
  }

  @Test
  public void
      testSetRequestStartTimeMicrosDoesNotSingleTriggerGaugeCollectionOnNonVerboseSession() {
    forceNonVerboseSession();
    networkRequestBuilder.setRequestStartTimeMicros(2000).build();
    verify(mockGaugeManager, never())
        .collectGaugeMetricOnce(ArgumentMatchers.nullable(Timer.class));
  }

  @Test
  public void testSetTimeToResponseCompletedMicrosTriggerSingleGaugeCollectionOnVerboseSession() {
    forceVerboseSession();
    networkRequestBuilder.setTimeToResponseCompletedMicros(3000).build();
    verify(mockGaugeManager).collectGaugeMetricOnce(ArgumentMatchers.nullable(Timer.class));
  }

  @Test
  public void
      testSetTimeToResponseCompletedMicrosDoesNotTriggerSingleGaugeCollectionOnNonVerboseSession() {
    forceNonVerboseSession();
    networkRequestBuilder.setTimeToResponseCompletedMicros(3000).build();
    verify(mockGaugeManager, never())
        .collectGaugeMetricOnce(ArgumentMatchers.nullable(Timer.class));
  }

  @Test
  public void testSettingInvalidContentType() {
    networkRequestBuilder.clearBuilderFields();
    networkRequestBuilder.setResponseContentType("");
    assertThat(networkRequestBuilder.build().hasResponseContentType()).isTrue();

    networkRequestBuilder.clearBuilderFields();
    networkRequestBuilder.setResponseContentType("a\u001f");
    assertThat(networkRequestBuilder.build().hasResponseContentType()).isFalse();

    networkRequestBuilder.clearBuilderFields();
    networkRequestBuilder.setResponseContentType("a\u0020");
    assertThat(networkRequestBuilder.build().hasResponseContentType()).isTrue();

    networkRequestBuilder.clearBuilderFields();
    networkRequestBuilder.setResponseContentType("a\u007f");
    assertThat(networkRequestBuilder.build().hasResponseContentType()).isTrue();

    networkRequestBuilder.clearBuilderFields();
    networkRequestBuilder.setResponseContentType("a\u0080");
    assertThat(networkRequestBuilder.build().hasResponseContentType()).isFalse();

    byte[] bytes = new byte[Constants.MAX_CONTENT_TYPE_LENGTH];
    Arrays.fill(bytes, (byte) 'x');
    String str = new String(bytes, StandardCharsets.UTF_8);

    networkRequestBuilder.clearBuilderFields();
    networkRequestBuilder.setResponseContentType(str);
    assertThat(networkRequestBuilder.build().hasResponseContentType()).isTrue();

    networkRequestBuilder.clearBuilderFields();
    networkRequestBuilder.setResponseContentType(str + "x");
    assertThat(networkRequestBuilder.build().hasResponseContentType()).isFalse();
  }

  @Test
  public void testSettingNullForContentTypeClearsIt() {
    networkRequestBuilder.setResponseContentType("application/json");
    assertThat(networkRequestBuilder.build().hasResponseContentType()).isTrue();

    networkRequestBuilder.setResponseContentType(null);
    assertThat(networkRequestBuilder.build().hasResponseContentType()).isFalse();
  }

  @Test
  public void testUpdateSessionWithValidSessionIsAdded() {
    networkRequestBuilder.setRequestStartTimeMicros(/* time= */ 2000);

    assertThat(networkRequestBuilder.getSessions()).hasSize(1);
    networkRequestBuilder.updateSession(PerfSession.create());
    assertThat(networkRequestBuilder.getSessions()).hasSize(2);
  }

  @Test
  public void testUpdateSessionWithNullIsNotAdded() {
    networkRequestBuilder.setRequestStartTimeMicros(/* time= */ 2000);

    assertThat(networkRequestBuilder.getSessions()).hasSize(1);
    networkRequestBuilder.updateSession(null);
    assertThat(networkRequestBuilder.getSessions()).hasSize(1);
  }
}
