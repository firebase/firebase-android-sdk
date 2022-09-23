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

import com.google.firebase.perf.v1.AndroidMemoryReading;
import com.google.firebase.perf.v1.CpuMetricReading;
import com.google.firebase.perf.v1.GaugeMetadata;
import com.google.firebase.perf.v1.GaugeMetric;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Unit tests for {@link
 * com.google.firebase.perf.metrics.validator.FirebasePerfGaugeMetricValidator}.
 */
@RunWith(RobolectricTestRunner.class)
public final class FirebasePerfGaugeManagerValidatorTest {

  @Before
  public void setUp() {}

  @Test
  public void testGaugeMetricIsValid() {
    // Construct a list of Cpu metric readings
    List<CpuMetricReading> expectedCpuMetricReadings = new ArrayList<>();
    expectedCpuMetricReadings.add(
        createValidCpuMetricReading(/* userTimeUs= */ 10, /* systemTimeUs= */ 20));
    expectedCpuMetricReadings.add(
        createValidCpuMetricReading(/* userTimeUs= */ 20, /* systemTimeUs= */ 30));

    // Construct a list of Memory metric readings
    List<AndroidMemoryReading> expectedMemoryMetricReadings = new ArrayList<>();
    expectedMemoryMetricReadings.add(
        createValidAndroidMetricReading(/* currentUsedAppJavaHeapMemoryKb= */ 1234));
    expectedMemoryMetricReadings.add(
        createValidAndroidMetricReading(/* currentUsedAppJavaHeapMemoryKb= */ 23456));

    // Construct GaugeMetadata
    GaugeMetadata gaugeMetadata =
        createValidGaugeMetadata(
            /* deviceRamSizeKb= */ 2000,
            /* maxAppJavaHeapMemoryKb= */ 1000,
            /* maxEncouragedAppJavaHeapMemoryKb= */ 800);

    GaugeMetric.Builder gaugeMetricBuilder = GaugeMetric.newBuilder();
    gaugeMetricBuilder.setSessionId("sessionId");
    gaugeMetricBuilder.addAllCpuMetricReadings(expectedCpuMetricReadings);
    gaugeMetricBuilder.addAllAndroidMemoryReadings(expectedMemoryMetricReadings);
    gaugeMetricBuilder.setGaugeMetadata(gaugeMetadata);

    FirebasePerfGaugeMetricValidator validator =
        new FirebasePerfGaugeMetricValidator(gaugeMetricBuilder.build());

    assertThat(validator.isValidPerfMetric()).isTrue();
  }

  @Test
  public void testGaugeMetricWithOnlyCpuMetricIsValid() {
    // Construct a list of Cpu metric readings
    List<CpuMetricReading> expectedCpuMetricReadings = new ArrayList<>();
    expectedCpuMetricReadings.add(
        createValidCpuMetricReading(/* userTimeUs= */ 10, /* systemTimeUs= */ 20));
    expectedCpuMetricReadings.add(
        createValidCpuMetricReading(/* userTimeUs= */ 20, /* systemTimeUs= */ 30));

    GaugeMetric.Builder gaugeMetricBuilder = GaugeMetric.newBuilder();
    gaugeMetricBuilder.setSessionId("sessionId");
    gaugeMetricBuilder.addAllCpuMetricReadings(expectedCpuMetricReadings);

    FirebasePerfGaugeMetricValidator validator =
        new FirebasePerfGaugeMetricValidator(gaugeMetricBuilder.build());

    assertThat(validator.isValidPerfMetric()).isTrue();
  }

  @Test
  public void testGaugeMetricWithOnlyMemoryMetricIsValid() {
    // Construct a list of Memory metric readings
    List<AndroidMemoryReading> expectedMemoryMetricReadings = new ArrayList<>();
    expectedMemoryMetricReadings.add(
        createValidAndroidMetricReading(/* currentUsedAppJavaHeapMemoryKb= */ 1234));
    expectedMemoryMetricReadings.add(
        createValidAndroidMetricReading(/* currentUsedAppJavaHeapMemoryKb= */ 23456));

    GaugeMetric.Builder gaugeMetricBuilder = GaugeMetric.newBuilder();
    gaugeMetricBuilder.setSessionId("sessionId");
    gaugeMetricBuilder.addAllAndroidMemoryReadings(expectedMemoryMetricReadings);

    FirebasePerfGaugeMetricValidator validator =
        new FirebasePerfGaugeMetricValidator(gaugeMetricBuilder.build());

    assertThat(validator.isValidPerfMetric()).isTrue();
  }

  @Test
  public void testGaugeMetricWithOnlyGaugeMetadataIsValid() {
    GaugeMetadata gaugeMetadata =
        createValidGaugeMetadata(
            /* deviceRamSizeKb= */ 2000,
            /* maxAppJavaHeapMemoryKb= */ 1000,
            /* maxEncouragedAppJavaHeapMemoryKb= */ 800);

    GaugeMetric.Builder gaugeMetricBuilder = GaugeMetric.newBuilder();
    gaugeMetricBuilder.setSessionId("sessionId");
    gaugeMetricBuilder.setGaugeMetadata(gaugeMetadata);

    FirebasePerfGaugeMetricValidator validator =
        new FirebasePerfGaugeMetricValidator(gaugeMetricBuilder.build());

    assertThat(validator.isValidPerfMetric()).isTrue();
  }

  @Test
  public void testGaugeMetadataWithoutMaxJavaHeapIsNotValid() {
    GaugeMetadata gaugeMetadata =
        GaugeMetadata.newBuilder()
            .setDeviceRamSizeKb(2000)
            .setMaxEncouragedAppJavaHeapMemoryKb(800)
            .build();

    GaugeMetric.Builder gaugeMetricBuilder = GaugeMetric.newBuilder();
    gaugeMetricBuilder.setSessionId("sessionId");
    gaugeMetricBuilder.setGaugeMetadata(gaugeMetadata);

    FirebasePerfGaugeMetricValidator validator =
        new FirebasePerfGaugeMetricValidator(gaugeMetricBuilder.build());

    assertThat(validator.isValidPerfMetric()).isFalse();
  }

  @Test
  public void testGaugeMetricWithoutSessionIdIsNotValid() {
    // Construct a list of Cpu metric readings
    List<CpuMetricReading> expectedCpuMetricReadings = new ArrayList<>();
    expectedCpuMetricReadings.add(
        createValidCpuMetricReading(/* userTimeUs= */ 10, /* systemTimeUs= */ 20));
    expectedCpuMetricReadings.add(
        createValidCpuMetricReading(/* userTimeUs= */ 20, /* systemTimeUs= */ 30));

    // Construct a list of Memory metric readings
    List<AndroidMemoryReading> expectedMemoryMetricReadings = new ArrayList<>();
    expectedMemoryMetricReadings.add(
        createValidAndroidMetricReading(/* currentUsedAppJavaHeapMemoryKb= */ 1234));
    expectedMemoryMetricReadings.add(
        createValidAndroidMetricReading(/* currentUsedAppJavaHeapMemoryKb= */ 23456));

    GaugeMetric.Builder gaugeMetricBuilder = GaugeMetric.newBuilder();
    gaugeMetricBuilder.addAllCpuMetricReadings(expectedCpuMetricReadings);
    gaugeMetricBuilder.addAllAndroidMemoryReadings(expectedMemoryMetricReadings);

    FirebasePerfGaugeMetricValidator validator =
        new FirebasePerfGaugeMetricValidator(gaugeMetricBuilder.build());

    assertThat(validator.isValidPerfMetric()).isFalse();
  }

  @Test
  public void testGaugeMetricWithoutAnyMetricsIsNotValid() {
    GaugeMetric.Builder gaugeMetricBuilder = GaugeMetric.newBuilder();
    gaugeMetricBuilder.setSessionId("sessionId");

    FirebasePerfGaugeMetricValidator validator =
        new FirebasePerfGaugeMetricValidator(gaugeMetricBuilder.build());

    assertThat(validator.isValidPerfMetric()).isFalse();
  }

  private CpuMetricReading createValidCpuMetricReading(long userTimeUs, long systemTimeUs) {
    CpuMetricReading.Builder fakeMetricReadingBuilder = CpuMetricReading.newBuilder();
    fakeMetricReadingBuilder.setClientTimeUs(System.currentTimeMillis());
    fakeMetricReadingBuilder.setUserTimeUs(userTimeUs);
    fakeMetricReadingBuilder.setSystemTimeUs(systemTimeUs);
    return fakeMetricReadingBuilder.build();
  }

  private AndroidMemoryReading createValidAndroidMetricReading(int currentUsedAppJavaHeapMemoryKb) {
    AndroidMemoryReading.Builder fakeMetricReadingBuilder = AndroidMemoryReading.newBuilder();
    fakeMetricReadingBuilder.setClientTimeUs(System.currentTimeMillis());
    fakeMetricReadingBuilder.setUsedAppJavaHeapMemoryKb(currentUsedAppJavaHeapMemoryKb);
    return fakeMetricReadingBuilder.build();
  }

  private GaugeMetadata createValidGaugeMetadata(
      int deviceRamSizeKb, int maxAppJavaHeapMemoryKb, int maxEncouragedAppJavaHeapMemoryKb) {
    GaugeMetadata.Builder fakeGaugeMetadataBuilder = GaugeMetadata.newBuilder();
    fakeGaugeMetadataBuilder.setDeviceRamSizeKb(deviceRamSizeKb);
    fakeGaugeMetadataBuilder.setMaxAppJavaHeapMemoryKb(maxAppJavaHeapMemoryKb);
    fakeGaugeMetadataBuilder.setMaxEncouragedAppJavaHeapMemoryKb(maxEncouragedAppJavaHeapMemoryKb);
    return fakeGaugeMetadataBuilder.build();
  }
}
