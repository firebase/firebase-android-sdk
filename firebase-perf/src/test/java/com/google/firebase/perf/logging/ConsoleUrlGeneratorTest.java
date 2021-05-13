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

package com.google.firebase.perf.logging;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

/** Unit tests for {@link ConsoleUrlGenerator}. */
public class ConsoleUrlGeneratorTest {
  private static final String PROJECT_ID = "test-project";
  private static final String PACKAGE_NAME = "test-package";
  private static final String TRACE_NAME = "test-trace";

  @Test
  public void testDashboardUrl() {
    String url = ConsoleUrlGenerator.generateDashboardUrl(PROJECT_ID, PACKAGE_NAME);
    assertThat(url)
        .isEqualTo(
            String.format(
                "https://console.firebase.google.com/project/%s/performance/app/android:%s/trends?utm_source=perf-android-sdk&utm_medium=android-ide",
                PROJECT_ID, PACKAGE_NAME));
  }

  @Test
  public void testCustomTraceUrl() {
    String url = ConsoleUrlGenerator.generateCustomTraceUrl(PROJECT_ID, PACKAGE_NAME, TRACE_NAME);
    assertThat(url)
        .isEqualTo(
            String.format(
                "https://console.firebase.google.com/project/%s/performance/app/android:%s/metrics/trace/DURATION_TRACE/%s?utm_source=perf-android-sdk&utm_medium=android-ide",
                PROJECT_ID, PACKAGE_NAME, TRACE_NAME));
  }

  @Test
  public void testScreenTraceUrl() {
    String url = ConsoleUrlGenerator.generateScreenTraceUrl(PROJECT_ID, PACKAGE_NAME, TRACE_NAME);
    assertThat(url)
        .isEqualTo(
            String.format(
                "https://console.firebase.google.com/project/%s/performance/app/android:%s/metrics/trace/SCREEN_TRACE/%s?utm_source=perf-android-sdk&utm_medium=android-ide",
                PROJECT_ID, PACKAGE_NAME, TRACE_NAME));
  }
}
