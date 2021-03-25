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
import com.google.firebase.perf.util.Timer;
import java.io.IOException;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;

/** Instrument response handler for apache http functions */
public class InstrumentApacheHttpResponseHandler<T> implements ResponseHandler<T> {

  private final ResponseHandler<? extends T> responseHandlerDelegate;
  private final Timer timer;
  private final NetworkRequestMetricBuilder networkRequestBuilder;

  public InstrumentApacheHttpResponseHandler(
      ResponseHandler<? extends T> responseHandler,
      Timer timer,
      NetworkRequestMetricBuilder builder) {
    responseHandlerDelegate = responseHandler;
    this.timer = timer;
    networkRequestBuilder = builder;
  }

  @Override
  public T handleResponse(HttpResponse httpResponse) throws IOException {
    networkRequestBuilder.setTimeToResponseCompletedMicros(timer.getDurationMicros());
    networkRequestBuilder.setHttpResponseCode(httpResponse.getStatusLine().getStatusCode());
    Long responseContentLength =
        NetworkRequestMetricBuilderUtil.getApacheHttpMessageContentLength(httpResponse);
    if (responseContentLength != null) {
      networkRequestBuilder.setResponsePayloadBytes(responseContentLength);
    }
    String contentType =
        NetworkRequestMetricBuilderUtil.getApacheHttpResponseContentType(httpResponse);
    if (contentType != null) {
      networkRequestBuilder.setResponseContentType(contentType);
    }
    networkRequestBuilder.build();
    return responseHandlerDelegate.handleResponse(httpResponse);
  }
}
