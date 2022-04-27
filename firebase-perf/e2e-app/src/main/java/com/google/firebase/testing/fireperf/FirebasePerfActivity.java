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

package com.google.firebase.testing.fireperf;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Activity used to help end to end testing of FirebasePerformance. */
public class FirebasePerfActivity extends Activity {

  public static final String START_TRACES = "START_TRACES";
  public static final String START_NETWORK_REQUESTS = "START_NETWORK_REQUESTS";
  public static final String NUMBER_OF_ITERATIONS = "NUMBER_OF_ITERATIONS";
  public static final String NUMBER_OF_TRACE_ITERATIONS = "NUMBER_OF_TRACE_ITERATIONS";
  public static final String NUMBER_OF_NETWORK_ITERATIONS = "NUMBER_OF_NETWORK_ITERATIONS";

  @Override
  public void onCreate(Bundle onSavedInstanceState) {
    super.onCreate(onSavedInstanceState);

    Intent intent = getIntent();

    int iterations = intent.getIntExtra(NUMBER_OF_ITERATIONS, /* defaultValue= */ 15);
    int traceIterations = intent.getIntExtra(NUMBER_OF_TRACE_ITERATIONS, /* defaultValue= */ 15);
    int networkIterations =
        intent.getIntExtra(NUMBER_OF_NETWORK_ITERATIONS, /* defaultValue= */ 15);

    ExecutorService executorService =
        new ThreadPoolExecutor(
            /* corePoolSize= */ 1,
            /* maximumPoolSize= */ 3,
            /* keepAliveTime= */ 1,
            TimeUnit.MINUTES,
            new LinkedBlockingQueue<>());

    if (intent.getBooleanExtra(START_TRACES, /* defaultValue= */ false)) {
      executorService.execute(() -> FireperfUtils.startTraces(traceIterations));
    }

    if (intent.getBooleanExtra(START_NETWORK_REQUESTS, /* defaultValue= */ false)) {
      executorService.execute(() -> FireperfUtils.startNetworkRequests(networkIterations));
    }

    // If neither network or trace event is specified in Intent, start both events in sequence.
    if (!intent.getBooleanExtra(START_TRACES, /* defaultValue= */ false)
        && !intent.getBooleanExtra(START_NETWORK_REQUESTS, /* defaultValue= */ false)) {
      executorService.execute(() -> FireperfUtils.startEvents(iterations));
    }
  }
}
