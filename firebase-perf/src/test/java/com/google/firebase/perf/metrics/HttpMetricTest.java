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

package com.google.firebase.perf.metrics;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.google.firebase.perf.FirebasePerformance.HttpMethod;
import com.google.firebase.perf.FirebasePerformanceTestBase;
import com.google.firebase.perf.transport.TransportManager;
import com.google.firebase.perf.util.Constants;
import com.google.firebase.perf.util.Timer;
import com.google.firebase.perf.v1.ApplicationProcessState;
import com.google.firebase.perf.v1.NetworkRequestMetric;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for {@link HttpMetric}. */
@RunWith(RobolectricTestRunner.class)
public class HttpMetricTest extends FirebasePerformanceTestBase {

  @Mock private TransportManager transportManager;
  @Mock private Timer timer;
  @Captor private ArgumentCaptor<NetworkRequestMetric> networkArgumentCaptor;

  @Before
  public void setUp() {
    initMocks(this);
    when(timer.getMicros()).thenReturn(1000L);
    when(timer.getDurationMicros()).thenReturn(2000L).thenReturn(3000L);
  }

  @Test
  public void startStop() {
    HttpMetric metric =
        new HttpMetric("https://www.google.com/", HttpMethod.GET, transportManager, timer);
    metric.start();
    metric.stop();
    verify(transportManager)
        .log(
            networkArgumentCaptor.capture(),
            ArgumentMatchers.nullable(ApplicationProcessState.class));
    verifyMetric(networkArgumentCaptor.getValue());
  }

  @Test
  public void setHttpResponseCode() {
    HttpMetric metric =
        new HttpMetric("https://www.google.com/", HttpMethod.GET, transportManager, timer);
    metric.start();
    metric.setHttpResponseCode(200);
    metric.stop();
    verify(transportManager)
        .log(
            networkArgumentCaptor.capture(),
            ArgumentMatchers.nullable(ApplicationProcessState.class));
    NetworkRequestMetric metricValue = networkArgumentCaptor.getValue();
    verifyMetric(metricValue);
    assertThat(metricValue.getHttpResponseCode()).isEqualTo(200);
  }

  @Test
  public void setRequestSize() {
    HttpMetric metric =
        new HttpMetric("https://www.google.com/", HttpMethod.GET, transportManager, timer);
    metric.start();
    metric.setRequestPayloadSize(256);
    metric.stop();
    verify(transportManager)
        .log(
            networkArgumentCaptor.capture(),
            ArgumentMatchers.nullable(ApplicationProcessState.class));
    NetworkRequestMetric metricValue = networkArgumentCaptor.getValue();
    verifyMetric(metricValue);
    assertThat(metricValue.getRequestPayloadBytes()).isEqualTo(256);
  }

  @Test
  public void setResponseSize() {
    HttpMetric metric =
        new HttpMetric("https://www.google.com/", HttpMethod.GET, transportManager, timer);
    metric.start();
    metric.setResponsePayloadSize(256);
    metric.stop();
    verify(transportManager)
        .log(
            networkArgumentCaptor.capture(),
            ArgumentMatchers.nullable(ApplicationProcessState.class));
    NetworkRequestMetric metricValue = networkArgumentCaptor.getValue();
    verifyMetric(metricValue);
    assertThat(metricValue.getResponsePayloadBytes()).isEqualTo(256);
  }

  @Test
  public void setResponseContentType() {
    HttpMetric metric =
        new HttpMetric("https://www.google.com/", HttpMethod.GET, transportManager, timer);
    metric.start();
    metric.setResponseContentType("text/html");
    metric.stop();
    verify(transportManager)
        .log(
            networkArgumentCaptor.capture(),
            ArgumentMatchers.nullable(ApplicationProcessState.class));
    NetworkRequestMetric metricValue = networkArgumentCaptor.getValue();
    verifyMetric(metricValue);
    assertThat(metricValue.getResponseContentType()).isEqualTo("text/html");
  }

  @Test
  public void markRequestComplete() {
    HttpMetric metric =
        new HttpMetric("https://www.google.com/", HttpMethod.GET, transportManager, timer);
    metric.start();
    metric.markRequestComplete();
    metric.stop();
    verify(transportManager)
        .log(
            networkArgumentCaptor.capture(),
            ArgumentMatchers.nullable(ApplicationProcessState.class));
    NetworkRequestMetric metricValue = networkArgumentCaptor.getValue();
    assertThat(metricValue.getUrl()).isEqualTo("https://www.google.com/");
    assertThat(metricValue.getHttpMethod())
        .isEqualTo(com.google.firebase.perf.v1.NetworkRequestMetric.HttpMethod.GET);
    assertThat(metricValue.getClientStartTimeUs()).isEqualTo(1000);
    assertThat(metricValue.getTimeToRequestCompletedUs()).isEqualTo(2000);
    assertThat(metricValue.getTimeToResponseCompletedUs()).isEqualTo(3000);
  }

  @Test
  public void markResponseStart() {
    HttpMetric metric =
        new HttpMetric("https://www.google.com/", HttpMethod.GET, transportManager, timer);
    metric.start();
    metric.markResponseStart();
    metric.stop();
    verify(transportManager)
        .log(
            networkArgumentCaptor.capture(),
            ArgumentMatchers.nullable(ApplicationProcessState.class));
    NetworkRequestMetric metricValue = networkArgumentCaptor.getValue();
    assertThat(metricValue.getUrl()).isEqualTo("https://www.google.com/");
    assertThat(metricValue.getHttpMethod())
        .isEqualTo(com.google.firebase.perf.v1.NetworkRequestMetric.HttpMethod.GET);
    assertThat(metricValue.getClientStartTimeUs()).isEqualTo(1000);
    assertThat(metricValue.getTimeToResponseInitiatedUs()).isEqualTo(2000);
    assertThat(metricValue.getTimeToResponseCompletedUs()).isEqualTo(3000);
  }

  @Test
  public void putAttribute() {
    HttpMetric metric =
        new HttpMetric("https://www.google.com/", HttpMethod.GET, transportManager, timer);
    metric.start();
    metric.putAttribute("attr1", "free");
    metric.stop();
    verify(transportManager)
        .log(
            networkArgumentCaptor.capture(),
            ArgumentMatchers.nullable(ApplicationProcessState.class));
    NetworkRequestMetric metricValue = networkArgumentCaptor.getValue();
    assertThat(metricValue.getUrl()).isEqualTo("https://www.google.com/");
    assertThat(metricValue.getHttpMethod())
        .isEqualTo(com.google.firebase.perf.v1.NetworkRequestMetric.HttpMethod.GET);
    assertThat(metricValue.getClientStartTimeUs()).isEqualTo(1000);
    assertThat(metricValue.getTimeToResponseCompletedUs()).isEqualTo(2000);
    assertThat(metricValue.getCustomAttributesCount()).isEqualTo(1);
    assertThat(metricValue.getCustomAttributesMap()).containsEntry("attr1", "free");
  }

  @Test
  public void putInvalidAttribute() {
    HttpMetric metric =
        new HttpMetric("https://www.google.com/", HttpMethod.GET, transportManager, timer);
    metric.start();
    metric.putAttribute("_invalidattr1", "free");
    metric.stop();
    verify(transportManager)
        .log(
            networkArgumentCaptor.capture(),
            ArgumentMatchers.nullable(ApplicationProcessState.class));
    NetworkRequestMetric metricValue = networkArgumentCaptor.getValue();
    assertThat(metricValue.getUrl()).isEqualTo("https://www.google.com/");
    assertThat(metricValue.getHttpMethod())
        .isEqualTo(com.google.firebase.perf.v1.NetworkRequestMetric.HttpMethod.GET);
    assertThat(metricValue.getClientStartTimeUs()).isEqualTo(1000);
    assertThat(metricValue.getTimeToResponseCompletedUs()).isEqualTo(2000);
    assertThat(metricValue.getCustomAttributesCount()).isEqualTo(0);
  }

  @Test
  public void putAttributeAfterHttpMetricIsStopped() {
    HttpMetric metric =
        new HttpMetric("https://www.google.com/", HttpMethod.GET, transportManager, timer);
    metric.start();
    metric.stop();
    metric.putAttribute("attr1", "free");
    verify(transportManager)
        .log(
            networkArgumentCaptor.capture(),
            ArgumentMatchers.nullable(ApplicationProcessState.class));
    NetworkRequestMetric metricValue = networkArgumentCaptor.getValue();
    assertThat(metricValue.getUrl()).isEqualTo("https://www.google.com/");
    assertThat(metricValue.getHttpMethod())
        .isEqualTo(com.google.firebase.perf.v1.NetworkRequestMetric.HttpMethod.GET);
    assertThat(metricValue.getClientStartTimeUs()).isEqualTo(1000);
    assertThat(metricValue.getTimeToResponseCompletedUs()).isEqualTo(2000);
    assertThat(metricValue.getCustomAttributesCount()).isEqualTo(0);
  }

  @Test
  public void removeAttribute() {
    HttpMetric metric =
        new HttpMetric("https://www.google.com/", HttpMethod.GET, transportManager, timer);
    metric.start();
    metric.putAttribute("attr1", "free");
    Map<String, String> attributes = metric.getAttributes();
    assertThat(attributes.size()).isEqualTo(1);
    metric.removeAttribute("attr1");
    attributes = metric.getAttributes();
    assertThat(attributes.size()).isEqualTo(0);
    metric.stop();
    verify(transportManager)
        .log(
            networkArgumentCaptor.capture(),
            ArgumentMatchers.nullable(ApplicationProcessState.class));
    NetworkRequestMetric metricValue = networkArgumentCaptor.getValue();
    assertThat(metricValue.getUrl()).isEqualTo("https://www.google.com/");
    assertThat(metricValue.getHttpMethod())
        .isEqualTo(com.google.firebase.perf.v1.NetworkRequestMetric.HttpMethod.GET);
    assertThat(metricValue.getClientStartTimeUs()).isEqualTo(1000);
    assertThat(metricValue.getTimeToResponseCompletedUs()).isEqualTo(2000);
    assertThat(metricValue.getCustomAttributesCount()).isEqualTo(0);
  }

  @Test
  public void removeAttributeAfterStopped() {
    HttpMetric metric =
        new HttpMetric("https://www.google.com/", HttpMethod.GET, transportManager, timer);
    metric.start();
    metric.putAttribute("attr1", "free");
    metric.stop();
    metric.removeAttribute("attr1");
    verify(transportManager)
        .log(
            networkArgumentCaptor.capture(),
            ArgumentMatchers.nullable(ApplicationProcessState.class));
    NetworkRequestMetric metricValue = networkArgumentCaptor.getValue();
    assertThat(metricValue.getUrl()).isEqualTo("https://www.google.com/");
    assertThat(metricValue.getHttpMethod())
        .isEqualTo(com.google.firebase.perf.v1.NetworkRequestMetric.HttpMethod.GET);
    assertThat(metricValue.getClientStartTimeUs()).isEqualTo(1000);
    assertThat(metricValue.getTimeToResponseCompletedUs()).isEqualTo(2000);
    assertThat(metricValue.getCustomAttributesCount()).isEqualTo(1);
    assertThat(metricValue.getCustomAttributesMap()).containsEntry("attr1", "free");
  }

  @Test
  public void addAttributeWithSameName() {
    HttpMetric metric =
        new HttpMetric("https://www.google.com/", HttpMethod.GET, transportManager, timer);
    metric.start();
    metric.putAttribute("attr1", "free");
    metric.putAttribute("attr1", "paid");
    metric.stop();
    verify(transportManager)
        .log(
            networkArgumentCaptor.capture(),
            ArgumentMatchers.nullable(ApplicationProcessState.class));
    NetworkRequestMetric metricValue = networkArgumentCaptor.getValue();
    assertThat(metricValue.getUrl()).isEqualTo("https://www.google.com/");
    assertThat(metricValue.getHttpMethod())
        .isEqualTo(com.google.firebase.perf.v1.NetworkRequestMetric.HttpMethod.GET);
    assertThat(metricValue.getClientStartTimeUs()).isEqualTo(1000);
    assertThat(metricValue.getTimeToResponseCompletedUs()).isEqualTo(2000);
    assertThat(metricValue.getCustomAttributesCount()).isEqualTo(1);
    assertThat(metricValue.getCustomAttributesMap()).containsEntry("attr1", "paid");
  }

  @Test
  public void testMaxAttributes() {
    HttpMetric metric =
        new HttpMetric("https://www.google.com/", HttpMethod.GET, transportManager, timer);
    metric.start();
    for (int i = 0; i <= Constants.MAX_TRACE_CUSTOM_ATTRIBUTES; i++) {
      metric.putAttribute("dim" + i, "value" + i);
    }
    for (int i = 0; i <= Constants.MAX_TRACE_CUSTOM_ATTRIBUTES; i++) {
      metric.putAttribute("dim" + i, "value" + (i + 1));
    }
    metric.stop();
    verify(transportManager)
        .log(
            networkArgumentCaptor.capture(),
            ArgumentMatchers.nullable(ApplicationProcessState.class));
    NetworkRequestMetric metricValue = networkArgumentCaptor.getValue();
    assertThat(metricValue.getUrl()).isEqualTo("https://www.google.com/");
    assertThat(metricValue.getHttpMethod())
        .isEqualTo(com.google.firebase.perf.v1.NetworkRequestMetric.HttpMethod.GET);
    assertThat(metricValue.getClientStartTimeUs()).isEqualTo(1000);
    assertThat(metricValue.getTimeToResponseCompletedUs()).isEqualTo(2000);
    assertThat(metricValue.getCustomAttributesCount())
        .isEqualTo(Constants.MAX_TRACE_CUSTOM_ATTRIBUTES);
    for (int i = 0; i < Constants.MAX_TRACE_CUSTOM_ATTRIBUTES; i++) {
      String attributeValue = "value" + (i + 1);
      String attributeKey = "dim" + i;
      assertThat(metric.getAttribute(attributeKey)).isEqualTo(attributeValue);
    }
  }

  @Test
  public void testMoreThanMaxAttributes() {
    HttpMetric metric =
        new HttpMetric("https://www.google.com/", HttpMethod.GET, transportManager, timer);
    metric.start();
    for (int i = 0; i <= Constants.MAX_TRACE_CUSTOM_ATTRIBUTES; i++) {
      metric.putAttribute("dim" + i, "value" + i);
    }
    metric.stop();
    verify(transportManager)
        .log(
            networkArgumentCaptor.capture(),
            ArgumentMatchers.nullable(ApplicationProcessState.class));
    NetworkRequestMetric metricValue = networkArgumentCaptor.getValue();
    assertThat(metricValue.getUrl()).isEqualTo("https://www.google.com/");
    assertThat(metricValue.getHttpMethod())
        .isEqualTo(com.google.firebase.perf.v1.NetworkRequestMetric.HttpMethod.GET);
    assertThat(metricValue.getClientStartTimeUs()).isEqualTo(1000);
    assertThat(metricValue.getTimeToResponseCompletedUs()).isEqualTo(2000);
    assertThat(metricValue.getCustomAttributesCount())
        .isEqualTo(Constants.MAX_TRACE_CUSTOM_ATTRIBUTES);
    for (int i = 0; i < Constants.MAX_TRACE_CUSTOM_ATTRIBUTES; i++) {
      String attributeValue = "value" + i;
      String attributeKey = "dim" + i;
      assertThat(metric.getAttribute(attributeKey)).isEqualTo(attributeValue);
    }

    assertThat(metricValue.getCustomAttributesMap()).doesNotContainKey("attr6");
  }

  private void verifyMetric(NetworkRequestMetric metricValue) {
    assertThat(metricValue.getUrl()).isEqualTo("https://www.google.com/");
    assertThat(metricValue.getHttpMethod())
        .isEqualTo(com.google.firebase.perf.v1.NetworkRequestMetric.HttpMethod.GET);
    assertThat(metricValue.getClientStartTimeUs()).isEqualTo(1000);
    assertThat(metricValue.getTimeToResponseCompletedUs()).isEqualTo(2000);
  }
}
