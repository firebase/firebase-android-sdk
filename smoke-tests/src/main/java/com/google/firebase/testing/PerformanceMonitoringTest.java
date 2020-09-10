// Copyright 2020 Google LLC
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

package com.google.firebase.testing;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.runner.AndroidJUnit4;
import com.google.firebase.perf.FirebasePerformance;
import com.google.firebase.perf.metrics.HttpMetric;
import com.google.firebase.perf.metrics.Trace;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class PerformanceMonitoringTest {

  @Test
  public void trace() {
    Trace trace = FirebasePerformance.getInstance().newTrace("test_trace");
    trace.start();

    trace.putMetric("counter", 1);
    trace.incrementMetric("counter", 2);
    trace.putAttribute("is_test", "true");

    trace.stop();

    assertThat(trace.getLongMetric("counter")).isEqualTo(3);
    assertThat(trace.getAttribute("is_test")).isEqualTo("true");
  }

  @Test
  public void networkRequest() {
    HttpMetric networkRequest =
        FirebasePerformance.getInstance()
            .newHttpMetric("https://www.google.com", FirebasePerformance.HttpMethod.GET);
    networkRequest.start();

    networkRequest.setRequestPayloadSize(128);
    networkRequest.setResponsePayloadSize(1024);
    networkRequest.setHttpResponseCode(200);
    networkRequest.setResponseContentType("text/html");
    networkRequest.putAttribute("is_test", "true");

    networkRequest.stop();

    assertThat(networkRequest.getAttribute("is_test")).isEqualTo("true");
  }
}
