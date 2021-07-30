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

package com.google.firebase.perf.network;

import com.google.firebase.perf.metrics.NetworkRequestMetricBuilder;
import com.google.firebase.perf.transport.TransportManager;
import com.google.firebase.perf.util.Timer;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

/** Instruments the callback for the OkHttp Enqueue function */
public class InstrumentOkHttpEnqueueCallback implements Callback {

  private final Callback callback;
  private final NetworkRequestMetricBuilder networkMetricBuilder;
  private final Timer timer;

  private final long startTimeMicros;

  public InstrumentOkHttpEnqueueCallback(
      final Callback callback,
      final TransportManager transportManager,
      Timer timer,
      long startTime) {
    this.callback = callback;
    networkMetricBuilder = NetworkRequestMetricBuilder.builder(transportManager);
    startTimeMicros = startTime;
    this.timer = timer;
  }

  @Override
  public void onFailure(Call call, IOException e) {
    Request request = call.request();
    if (request != null) {
      HttpUrl url = request.url();
      if (url != null) {
        networkMetricBuilder.setUrl(url.url().toString());
      }
      String method = request.method();
      if (method != null) {
        networkMetricBuilder.setHttpMethod(request.method());
      }
    }
    networkMetricBuilder.setRequestStartTimeMicros(startTimeMicros);
    networkMetricBuilder.setTimeToResponseCompletedMicros(timer.getDurationMicros());
    NetworkRequestMetricBuilderUtil.logError(networkMetricBuilder);
    callback.onFailure(call, e);
  }

  @Override
  public void onResponse(Call call, Response response) throws IOException {
    long responseCompletedTimeMicros = timer.getDurationMicros();
    FirebasePerfOkHttpClient.sendNetworkMetric(
        response, networkMetricBuilder, startTimeMicros, responseCompletedTimeMicros);
    callback.onResponse(call, response);
  }
}
