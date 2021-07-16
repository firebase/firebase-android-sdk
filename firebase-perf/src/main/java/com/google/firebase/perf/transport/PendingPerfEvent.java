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

import androidx.annotation.NonNull;
import com.google.firebase.perf.v1.ApplicationProcessState;
import com.google.firebase.perf.v1.PerfMetric;

/** Holds a {@link PerfMetric.Builder} and its associated {@link ApplicationProcessState}. */
final class PendingPerfEvent {

  protected final PerfMetric.Builder perfMetricBuilder;
  protected final ApplicationProcessState appState;

  public PendingPerfEvent(
      @NonNull PerfMetric.Builder perfMetricBuilder, @NonNull ApplicationProcessState appState) {
    this.perfMetricBuilder = perfMetricBuilder;
    this.appState = appState;
  }
}
