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

import java.io.IOException;
import okhttp3.Headers;
import okhttp3.Response;

public class HttpResponse {
  private int code;
  private String body;
  private Headers headers;

  HttpResponse(int code, String body, Headers headers) {
    this.code = code;
    this.body = body;
    this.headers = headers;
  }

  static HttpResponse create(Response response) throws IOException {
    String body = (response.body() == null) ? null : response.body().string();
    return new HttpResponse(response.code(), body, response.headers());
  }

  /*
   * Response methods
   */

  public int code() {
    return code;
  }

  public String body() {
    return body;
  }

  public String header(String name) {
    return headers.get(name);
  }
}
