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

import androidx.annotation.Keep;
import com.google.firebase.perf.metrics.NetworkRequestMetricBuilder;
import com.google.firebase.perf.transport.TransportManager;
import com.google.firebase.perf.util.Timer;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** These are the functions that are bytecode instrumented into the apk for OkHttp functions */
public class FirebasePerfOkHttpClient {

  private FirebasePerfOkHttpClient() {}

  @Keep
  public static Response execute(final Call call) throws IOException {
    final Response response;
    NetworkRequestMetricBuilder builder =
        NetworkRequestMetricBuilder.builder(TransportManager.getInstance());
    Timer timer = new Timer();
    long startTimeMicros = timer.getMicros();
    try {
      response = call.execute();
      long responseCompletedTimeMicros = timer.getDurationMicros();
      sendNetworkMetric(response, builder, startTimeMicros, responseCompletedTimeMicros);
    } catch (IOException e) {
      Request request = call.request();
      if (request != null) {
        HttpUrl url = request.url();
        if (url != null) {
          builder.setUrl(url.url().toString());
        }
        String method = request.method();
        if (method != null) {
          builder.setHttpMethod(request.method());
        }
      }
      builder.setRequestStartTimeMicros(startTimeMicros);
      builder.setTimeToResponseCompletedMicros(timer.getDurationMicros());
      NetworkRequestMetricBuilderUtil.logError(builder);
      throw e;
    }
    return response;
  }

  @Keep
  public static void enqueue(final Call call, final Callback callback) {
    Timer timer = new Timer();
    long startTime = timer.getMicros();
    call.enqueue(
        new InstrumentOkHttpEnqueueCallback(
            callback, TransportManager.getInstance(), timer, startTime));
  }

  static void sendNetworkMetric(
      Response response,
      NetworkRequestMetricBuilder builder,
      long startTimeMicros,
      long responseCompletedTimeMicros)
      throws IOException {
    Request request = response.request();
    if (request == null) {
      return;
    }
    builder.setUrl(request.url().url().toString());
    builder.setHttpMethod(request.method());
    RequestBody requestBody = request.body();
    if (requestBody != null) {
      long requestContentLength = request.body().contentLength();
      if (requestContentLength != -1) {
        builder.setRequestPayloadBytes(requestContentLength);
      }
    }
    ResponseBody responseBody = response.body();
    if (responseBody != null) {
      long responseContentLength = responseBody.contentLength();
      if (responseContentLength != -1) {
        builder.setResponsePayloadBytes(responseContentLength);
      }
      MediaType mediaType = responseBody.contentType();
      if (mediaType != null) {
        builder.setResponseContentType(mediaType.toString());
      }
    }
    builder.setHttpResponseCode(response.code());
    builder.setRequestStartTimeMicros(startTimeMicros);
    builder.setTimeToResponseCompletedMicros(responseCompletedTimeMicros);
    builder.build();
  }
}
