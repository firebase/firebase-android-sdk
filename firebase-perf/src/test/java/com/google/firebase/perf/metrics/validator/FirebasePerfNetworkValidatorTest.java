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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.perf.FirebasePerformanceTestBase;
import com.google.firebase.perf.metrics.NetworkRequestMetricBuilder;
import com.google.firebase.perf.transport.TransportManager;
import com.google.firebase.perf.v1.NetworkRequestMetric;
import com.google.firebase.perf.v1.NetworkRequestMetric.HttpMethod;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Unit tests for {@link com.google.firebase.perf.metrics.validator.FirebasePerfNetworkValidator}.
 */
@RunWith(RobolectricTestRunner.class)
public class FirebasePerfNetworkValidatorTest extends FirebasePerformanceTestBase {
  private final FirebasePerfNetworkValidator validator =
      new FirebasePerfNetworkValidator(null, ApplicationProvider.getApplicationContext());

  @Test
  public void testIsValid() {
    NetworkRequestMetricBuilder metricBuilder =
        createNetworkRequestMetricBuilderWithRequiredValuesPresent();
    FirebasePerfNetworkValidator validator =
        new FirebasePerfNetworkValidator(
            metricBuilder.build(), ApplicationProvider.getApplicationContext());
    assertTrue(validator.isValidPerfMetric());
  }

  @Test
  public void testEmptyUrl() {
    NetworkRequestMetricBuilder metricBuilder =
        createNetworkRequestMetricBuilderWithRequiredValuesPresent();
    metricBuilder.setUrl("");
    FirebasePerfNetworkValidator validator =
        new FirebasePerfNetworkValidator(
            metricBuilder.build(), ApplicationProvider.getApplicationContext());
    assertFalse(validator.isValidPerfMetric());
  }

  @Test
  public void testInvalidUrl() {
    NetworkRequestMetricBuilder metricBuilder =
        createNetworkRequestMetricBuilderWithRequiredValuesPresent();
    metricBuilder.setUrl("badurl{??//..}");
    FirebasePerfNetworkValidator validator =
        new FirebasePerfNetworkValidator(
            metricBuilder.build(), ApplicationProvider.getApplicationContext());
    assertFalse(validator.isValidPerfMetric());
  }

  @Test
  public void testIsValidHttpMethod() {
    Assert.assertTrue(validator.isValidHttpMethod(HttpMethod.GET));
    Assert.assertTrue(validator.isValidHttpMethod(HttpMethod.PUT));
    Assert.assertTrue(validator.isValidHttpMethod(HttpMethod.POST));
    Assert.assertTrue(validator.isValidHttpMethod(HttpMethod.DELETE));
    Assert.assertTrue(validator.isValidHttpMethod(HttpMethod.HEAD));
    Assert.assertTrue(validator.isValidHttpMethod(HttpMethod.PATCH));
    Assert.assertTrue(validator.isValidHttpMethod(HttpMethod.OPTIONS));
    Assert.assertTrue(validator.isValidHttpMethod(HttpMethod.TRACE));
    Assert.assertTrue(validator.isValidHttpMethod(HttpMethod.CONNECT));
    Assert.assertFalse(validator.isValidHttpMethod(HttpMethod.HTTP_METHOD_UNKNOWN));
    Assert.assertFalse(validator.isValidHttpMethod(null));
  }

  @Test
  public void testInvalidResponseCode() {
    NetworkRequestMetricBuilder metricBuilder =
        createNetworkRequestMetricBuilderWithRequiredValuesPresent();
    metricBuilder.setHttpResponseCode(-2);
    FirebasePerfNetworkValidator validator =
        new FirebasePerfNetworkValidator(
            metricBuilder.build(), ApplicationProvider.getApplicationContext());
    assertFalse(validator.isValidPerfMetric());
  }

  @Test
  public void testNullResponseCode() {
    NetworkRequestMetricBuilder metricBuilder =
        NetworkRequestMetricBuilder.builder(TransportManager.getInstance());

    // Set all the required fields except response code
    metricBuilder.setUrl("https://www.google.com");
    metricBuilder.setHttpMethod("GET");
    metricBuilder.setRequestStartTimeMicros(System.currentTimeMillis() * 1000L);
    metricBuilder.setTimeToResponseCompletedMicros(400L);
    NetworkRequestMetric metric = metricBuilder.build();

    assertFalse(metric.hasHttpResponseCode());
    FirebasePerfNetworkValidator validator =
        new FirebasePerfNetworkValidator(metric, ApplicationProvider.getApplicationContext());
    assertFalse(validator.isValidPerfMetric());
  }

  @Test
  public void testNullRequestPayloadBytes() {
    NetworkRequestMetricBuilder metricBuilder =
        createNetworkRequestMetricBuilderWithRequiredValuesPresent();
    NetworkRequestMetric metric = metricBuilder.build();

    assertFalse(metric.hasRequestPayloadBytes());
    FirebasePerfNetworkValidator validator =
        new FirebasePerfNetworkValidator(metric, ApplicationProvider.getApplicationContext());
    assertTrue(validator.isValidPerfMetric());
  }

  @Test
  public void testRequestPayloadBytes() {
    NetworkRequestMetricBuilder metricBuilder =
        createNetworkRequestMetricBuilderWithRequiredValuesPresent();
    metricBuilder.setRequestPayloadBytes(-1L);
    FirebasePerfNetworkValidator validator =
        new FirebasePerfNetworkValidator(
            metricBuilder.build(), ApplicationProvider.getApplicationContext());
    assertFalse(validator.isValidPerfMetric());
  }

  @Test
  public void testNullResponsePayloadBytes() {
    NetworkRequestMetricBuilder metricBuilder =
        createNetworkRequestMetricBuilderWithRequiredValuesPresent();
    NetworkRequestMetric metric = metricBuilder.build();
    assertFalse(metric.hasResponsePayloadBytes());
    FirebasePerfNetworkValidator validator =
        new FirebasePerfNetworkValidator(metric, ApplicationProvider.getApplicationContext());
    assertTrue(validator.isValidPerfMetric());
  }

  @Test
  public void testInvalidResponsePayloadBytes() {
    NetworkRequestMetricBuilder metricBuilder =
        createNetworkRequestMetricBuilderWithRequiredValuesPresent();
    metricBuilder.setResponsePayloadBytes(-1L);
    FirebasePerfNetworkValidator validator =
        new FirebasePerfNetworkValidator(
            metricBuilder.build(), ApplicationProvider.getApplicationContext());
    assertFalse(validator.isValidPerfMetric());
  }

  @Test
  public void testClientStartTimeUs() {
    NetworkRequestMetricBuilder metricBuilder =
        NetworkRequestMetricBuilder.builder(TransportManager.getInstance());

    // Set all required fields except clientStartTimeUs
    metricBuilder.setUrl("https://www.google.com");
    metricBuilder.setHttpMethod("GET");
    metricBuilder.setHttpResponseCode(200);
    metricBuilder.setTimeToResponseCompletedMicros(400L);

    assertFalse(metricBuilder.build().hasClientStartTimeUs());
    assertFalse(
        new FirebasePerfNetworkValidator(
                metricBuilder.build(), ApplicationProvider.getApplicationContext())
            .isValidPerfMetric());
    metricBuilder.setRequestStartTimeMicros(0L);
    assertFalse(
        new FirebasePerfNetworkValidator(
                metricBuilder.build(), ApplicationProvider.getApplicationContext())
            .isValidPerfMetric());
    metricBuilder.setRequestStartTimeMicros(-1L);
    assertFalse(
        new FirebasePerfNetworkValidator(
                metricBuilder.build(), ApplicationProvider.getApplicationContext())
            .isValidPerfMetric());
    metricBuilder.setRequestStartTimeMicros(System.currentTimeMillis() * 1000L);
    assertTrue(
        new FirebasePerfNetworkValidator(
                metricBuilder.build(), ApplicationProvider.getApplicationContext())
            .isValidPerfMetric());
  }

  @Test
  public void testNullTimeToRequestCompleted() {
    NetworkRequestMetricBuilder metricBuilder =
        createNetworkRequestMetricBuilderWithRequiredValuesPresent();
    NetworkRequestMetric metric = metricBuilder.build();

    assertFalse(metric.hasTimeToRequestCompletedUs());
    FirebasePerfNetworkValidator validator =
        new FirebasePerfNetworkValidator(metric, ApplicationProvider.getApplicationContext());
    assertTrue(validator.isValidPerfMetric());
  }

  @Test
  public void testNegativeTimeToRequestCompleted() {
    NetworkRequestMetricBuilder metricBuilder =
        createNetworkRequestMetricBuilderWithRequiredValuesPresent();
    metricBuilder.setTimeToRequestCompletedMicros(-1L);
    FirebasePerfNetworkValidator validator =
        new FirebasePerfNetworkValidator(
            metricBuilder.build(), ApplicationProvider.getApplicationContext());
    assertFalse(validator.isValidPerfMetric());
  }

  @Test
  public void testNullTimeToResponseInitiated() {
    NetworkRequestMetricBuilder metricBuilder =
        createNetworkRequestMetricBuilderWithRequiredValuesPresent();
    NetworkRequestMetric metric = metricBuilder.build();
    assertFalse(metric.hasTimeToResponseInitiatedUs());
    FirebasePerfNetworkValidator validator =
        new FirebasePerfNetworkValidator(metric, ApplicationProvider.getApplicationContext());
    assertTrue(validator.isValidPerfMetric());
  }

  @Test
  public void testNegativeTimeToResponseInitiated() {
    NetworkRequestMetricBuilder metricBuilder =
        createNetworkRequestMetricBuilderWithRequiredValuesPresent();
    metricBuilder.setTimeToResponseInitiatedMicros(-1L);
    FirebasePerfNetworkValidator validator =
        new FirebasePerfNetworkValidator(
            metricBuilder.build(), ApplicationProvider.getApplicationContext());
    assertFalse(validator.isValidPerfMetric());
  }

  @Test
  public void testNegativeTimeToResponseCompleted() {
    NetworkRequestMetricBuilder metricBuilder =
        createNetworkRequestMetricBuilderWithRequiredValuesPresent();
    metricBuilder.setTimeToResponseCompletedMicros(-1L);
    FirebasePerfNetworkValidator validator =
        new FirebasePerfNetworkValidator(
            metricBuilder.build(), ApplicationProvider.getApplicationContext());
    assertFalse(validator.isValidPerfMetric());
  }

  @Test
  public void testNullContentType() {
    NetworkRequestMetricBuilder metricBuilder =
        createNetworkRequestMetricBuilderWithRequiredValuesPresent();
    NetworkRequestMetric metric = metricBuilder.build();
    assertFalse(metric.hasResponseContentType());
    FirebasePerfNetworkValidator validator =
        new FirebasePerfNetworkValidator(metric, ApplicationProvider.getApplicationContext());
    assertTrue(validator.isValidPerfMetric());
  }

  @Test
  public void testInvalidContentType() {
    NetworkRequestMetricBuilder metricBuilder =
        createNetworkRequestMetricBuilderWithRequiredValuesPresent();
    // invalid content
    metricBuilder.setResponseContentType(
        "badcontenttype&&&&aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

    NetworkRequestMetric metric = metricBuilder.build();
    FirebasePerfNetworkValidator validator =
        new FirebasePerfNetworkValidator(metric, ApplicationProvider.getApplicationContext());
    assertTrue(validator.isValidPerfMetric());
    assertFalse(metric.hasResponseContentType());
  }

  @Test
  public void testValidContentType() {
    NetworkRequestMetricBuilder metricBuilder =
        createNetworkRequestMetricBuilderWithRequiredValuesPresent();
    metricBuilder.setResponseContentType("a\u0020");
    FirebasePerfNetworkValidator validator =
        new FirebasePerfNetworkValidator(
            metricBuilder.build(), ApplicationProvider.getApplicationContext());
    assertTrue(validator.isValidPerfMetric());
  }

  // The following tests validate that the absence of required fields in the proto result in failed
  // validation.
  // The required fields are: url, httpMethod, httpResponseCode, clientStartTimeUs, and
  // timeToResponseInitiatedUs.

  @Test
  public void testAbsenceOfUrlFailsValidation() {
    NetworkRequestMetricBuilder metricBuilder =
        NetworkRequestMetricBuilder.builder(TransportManager.getInstance());

    // Set all required fields except url
    metricBuilder.setHttpMethod("GET");
    metricBuilder.setHttpResponseCode(200);
    metricBuilder.setRequestStartTimeMicros(System.currentTimeMillis() * 1000L);
    metricBuilder.setTimeToResponseCompletedMicros(400L);

    FirebasePerfNetworkValidator metricValidator =
        new FirebasePerfNetworkValidator(
            metricBuilder.build(), ApplicationProvider.getApplicationContext());
    assertThat(metricValidator.isValidPerfMetric()).isFalse();
  }

  @Test
  public void testAbsenceOfHttpMethodFailsValidation() {
    NetworkRequestMetricBuilder metricBuilder =
        NetworkRequestMetricBuilder.builder(TransportManager.getInstance());

    // Set all required fields except httpMethod
    metricBuilder.setUrl("https://www.google.com");
    metricBuilder.setHttpResponseCode(200);
    metricBuilder.setRequestStartTimeMicros(System.currentTimeMillis() * 1000L);
    metricBuilder.setTimeToResponseCompletedMicros(400L);

    FirebasePerfNetworkValidator metricValidator =
        new FirebasePerfNetworkValidator(
            metricBuilder.build(), ApplicationProvider.getApplicationContext());
    assertThat(metricValidator.isValidPerfMetric()).isFalse();
  }

  @Test
  public void testAbsenceOfHttpResponseCodeFailsValidation() {
    NetworkRequestMetricBuilder metricBuilder =
        NetworkRequestMetricBuilder.builder(TransportManager.getInstance());

    // Set all required fields except httpResponseCode
    metricBuilder.setUrl("https://www.google.com");
    metricBuilder.setHttpMethod("GET");
    metricBuilder.setRequestStartTimeMicros(System.currentTimeMillis() * 1000L);
    metricBuilder.setTimeToResponseCompletedMicros(400L);
    NetworkRequestMetric metric = metricBuilder.build();

    assertThat(metric.hasHttpResponseCode()).isFalse();
    FirebasePerfNetworkValidator metricValidator =
        new FirebasePerfNetworkValidator(metric, ApplicationProvider.getApplicationContext());
    assertThat(metricValidator.isValidPerfMetric()).isFalse();
  }

  @Test
  public void testAbsenceOfClientStartTimeUsFailsValidation() {
    NetworkRequestMetricBuilder metricBuilder =
        NetworkRequestMetricBuilder.builder(TransportManager.getInstance());

    // Set all required fields except httpResponseCode
    metricBuilder.setUrl("https://www.google.com");
    metricBuilder.setHttpMethod("GET");
    metricBuilder.setHttpResponseCode(200);
    metricBuilder.setTimeToResponseCompletedMicros(400L);
    NetworkRequestMetric metric = metricBuilder.build();

    assertThat(metric.hasClientStartTimeUs()).isFalse();
    FirebasePerfNetworkValidator metricValidator =
        new FirebasePerfNetworkValidator(metric, ApplicationProvider.getApplicationContext());
    assertThat(metricValidator.isValidPerfMetric()).isFalse();
  }

  @Test
  public void testAbsenceOfTimeToResponseCompletedUsFailsValidation() {
    NetworkRequestMetricBuilder metricBuilder =
        NetworkRequestMetricBuilder.builder(TransportManager.getInstance());

    // Set all required fields except timeToResponseCompletedUs
    metricBuilder.setUrl("https://www.google.com");
    metricBuilder.setHttpMethod("GET");
    metricBuilder.setHttpResponseCode(200);
    metricBuilder.setRequestStartTimeMicros(System.currentTimeMillis() * 1000L);
    NetworkRequestMetric metric = metricBuilder.build();

    assertThat(metric.hasTimeToResponseCompletedUs()).isFalse();
    FirebasePerfNetworkValidator metricValidator =
        new FirebasePerfNetworkValidator(metric, ApplicationProvider.getApplicationContext());
    assertThat(metricValidator.isValidPerfMetric()).isFalse();
  }

  private NetworkRequestMetricBuilder createNetworkRequestMetricBuilderWithRequiredValuesPresent() {

    NetworkRequestMetricBuilder networkRequestMetricBuilder =
        NetworkRequestMetricBuilder.builder(TransportManager.getInstance());

    networkRequestMetricBuilder.setUrl("https://www.google.com");
    networkRequestMetricBuilder.setHttpMethod("GET");
    networkRequestMetricBuilder.setHttpResponseCode(200);
    networkRequestMetricBuilder.setRequestStartTimeMicros(System.currentTimeMillis() * 1000L);
    networkRequestMetricBuilder.setTimeToResponseCompletedMicros(400L);
    return networkRequestMetricBuilder;
  }
}
