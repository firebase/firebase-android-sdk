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

  @Test
  public void testDashboardUrl() {
    String url = ConsoleUrlGenerator.generateDashboardUrl("test-project", "test-package");
    assertThat(url)
        .isEqualTo(
            "https://console.firebase.google.com/project/test-project/performance/app/android:test-package/trends?utm_source=perf-android-sdk&utm_medium=android-ide");
  }

  @Test
  public void testCustomTraceUrl() {
    String url =
        ConsoleUrlGenerator.generateCustomTraceUrl("test-project", "test-package", "test-trace");
    assertThat(url)
        .isEqualTo(
            "https://console.firebase.google.com/project/test-project/performance/app/android:test-package/metrics/trace/DURATION_TRACE/test-trace?utm_source=perf-android-sdk&utm_medium=android-ide");
  }

  @Test
  public void testScreenTraceUrl() {
    String url =
        ConsoleUrlGenerator.generateScreenTraceUrl("test-project", "test-package", "test-trace");
    assertThat(url)
        .isEqualTo(
            "https://console.firebase.google.com/project/test-project/performance/app/android:test-package/metrics/trace/SCREEN_TRACE/test-trace?utm_source=perf-android-sdk&utm_medium=android-ide");
  }
}
