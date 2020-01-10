// Copyright 2019 Google LLC
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

package com.google.firebase.crashlytics.internal.network;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.CacheControl;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public class HttpRequest {
  private static final OkHttpClient CLIENT;
  private static final int DEFAULT_TIMEOUT_MS = 10 * 1000; // 10 seconds in millis

  private final HttpMethod method;
  private final String url;
  private final Map<String, String> queryParams;
  private final Map<String, String> headers;

  private MultipartBody.Builder bodyBuilder = null;

  static {
    CLIENT =
        new OkHttpClient()
            .newBuilder()
            .callTimeout(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build();
  }

  public HttpRequest(HttpMethod method, String url, Map<String, String> queryParams) {
    this.method = method;
    this.url = url;
    this.queryParams = queryParams;
    this.headers = new HashMap<>();
  }

  /*
   * Request setters
   */

  public HttpRequest header(String name, String value) {
    headers.put(name, value);
    return this;
  }

  public HttpRequest header(Map.Entry<String, String> entry) {
    return header(entry.getKey(), entry.getValue());
  }

  private MultipartBody.Builder getOrCreateBodyBuilder() {
    if (bodyBuilder == null) {
      bodyBuilder = new MultipartBody.Builder().setType(MultipartBody.FORM);
    }
    return bodyBuilder;
  }

  public HttpRequest part(String name, String value) {
    bodyBuilder = getOrCreateBodyBuilder().addFormDataPart(name, value);
    return this;
  }

  public HttpRequest part(String name, String filename, String contentType, File part) {
    MediaType type = MediaType.parse(contentType);
    RequestBody body = RequestBody.create(type, part);
    bodyBuilder = getOrCreateBodyBuilder().addFormDataPart(name, filename, body);
    return this;
  }

  /*
   * Request getters
   */

  public String method() {
    return method.name();
  }

  /*
   * Execution
   */

  /**
   * Creates an Request from the fields that have been set.
   *
   * @return the new Request.
   */
  private Request build() {
    Request.Builder builder =
        new Request.Builder().cacheControl(new CacheControl.Builder().noCache().build());

    HttpUrl.Builder urlBuilder = HttpUrl.parse(url).newBuilder();
    for (Map.Entry<String, String> entry : queryParams.entrySet()) {
      urlBuilder = urlBuilder.addEncodedQueryParameter(entry.getKey(), entry.getValue());
    }
    builder = builder.url(urlBuilder.build());

    for (Map.Entry<String, String> entry : headers.entrySet()) {
      builder = builder.header(entry.getKey(), entry.getValue());
    }

    RequestBody body = (bodyBuilder == null) ? null : bodyBuilder.build();
    builder = builder.method(method.name(), body);

    return builder.build();
  }

  public HttpResponse execute() throws IOException {
    Request request = build();
    Call call = CLIENT.newCall(request);
    return HttpResponse.create(call.execute());
  }
}
