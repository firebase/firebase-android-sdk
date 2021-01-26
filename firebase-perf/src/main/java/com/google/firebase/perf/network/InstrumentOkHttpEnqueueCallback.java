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

import com.google.firebase.perf.impl.NetworkRequestMetricBuilder;
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
  private final Callback mCallback;
  private final NetworkRequestMetricBuilder mBuilder;
  private final long mStartTimeMicros;
  private final Timer mTimer;

  public InstrumentOkHttpEnqueueCallback(
      final Callback callback,
      final TransportManager transportManager,
      Timer timer,
      long startTime) {
    mCallback = callback;
    mBuilder = NetworkRequestMetricBuilder.builder(transportManager);
    mStartTimeMicros = startTime;
    mTimer = timer;
  }

  @Override
  public void onFailure(Call call, IOException e) {
    Request request = call.request();
    if (request != null) {
      HttpUrl url = request.url();
      if (url != null) {
        mBuilder.setUrl(url.url().toString());
      }
      String method = request.method();
      if (method != null) {
        mBuilder.setHttpMethod(request.method());
      }
    }
    mBuilder.setRequestStartTimeMicros(mStartTimeMicros);
    mBuilder.setTimeToResponseCompletedMicros(mTimer.getDurationMicros());
    NetworkRequestMetricBuilderUtil.logError(mBuilder);
    mCallback.onFailure(call, e);
  }

  @Override
  public void onResponse(Call call, Response response) throws IOException {
    long responseCompletedTimeMicros = mTimer.getDurationMicros();
    FirebasePerfOkHttpClient.sendNetworkMetric(
        response, mBuilder, mStartTimeMicros, responseCompletedTimeMicros);
    mCallback.onResponse(call, response);
  }
}
