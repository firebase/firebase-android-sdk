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

package com.google.firebase.perf.transport;

import com.google.firebase.perf.v1.AndroidApplicationInfo;
import com.google.firebase.perf.v1.AndroidMemoryReading;
import com.google.firebase.perf.v1.ApplicationInfo;
import com.google.firebase.perf.v1.CpuMetricReading;
import com.google.firebase.perf.v1.GaugeMetric;
import com.google.firebase.perf.v1.NetworkRequestMetric;
import com.google.firebase.perf.v1.PerfMetric;
import com.google.firebase.perf.v1.TraceMetric;

public final class TestConstants {

  public static ApplicationInfo APPLICATION_INFO =
      ApplicationInfo.newBuilder()
          .setAndroidAppInfo(
              AndroidApplicationInfo.newBuilder()
                  .setPackageName("com.google.firebase.package")
                  .setSdkVersion("1.0.0"))
          .setAppInstanceId("ThisIsInstallationsId")
          .setGoogleAppId("1:234:android:567")
          .build();

  public static PerfMetric PERF_METRIC_TRACE =
      PerfMetric.newBuilder()
          .setApplicationInfo(APPLICATION_INFO)
          .setTraceMetric(
              TraceMetric.newBuilder()
                  .setClientStartTimeUs(1000)
                  .setDurationUs(2000)
                  .setIsAuto(true)
                  .setName("TraceMetric"))
          .build();

  public static PerfMetric PERF_METRIC_NETWORK =
      PerfMetric.newBuilder()
          .setApplicationInfo(APPLICATION_INFO)
          .setNetworkRequestMetric(
              NetworkRequestMetric.newBuilder()
                  .setHttpMethod(NetworkRequestMetric.HttpMethod.POST)
                  .setClientStartTimeUs(1000)
                  .setHttpResponseCode(200)
                  .setRequestPayloadBytes(3000L)
                  .setResponseContentType("ContentType")
                  .setTimeToRequestCompletedUs(4000)
                  .setTimeToResponseInitiatedUs(5000)
                  .setTimeToResponseCompletedUs(6000))
          .build();

  public static PerfMetric PERF_METRIC_GAUGE =
      PerfMetric.newBuilder()
          .setApplicationInfo(APPLICATION_INFO)
          .setGaugeMetric(
              GaugeMetric.newBuilder()
                  .addAndroidMemoryReadings(
                      AndroidMemoryReading.newBuilder().setUsedAppJavaHeapMemoryKb(123))
                  .addCpuMetricReadings(CpuMetricReading.newBuilder().setUserTimeUs(456))
                  .build())
          .build();
}
