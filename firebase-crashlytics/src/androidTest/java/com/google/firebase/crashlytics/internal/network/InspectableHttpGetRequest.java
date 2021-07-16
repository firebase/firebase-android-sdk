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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class InspectableHttpGetRequest extends HttpGetRequest {
  private final Map<String, String> _headers = new HashMap<String, String>();
  private Map<?, ?> _queryParams;
  private String _url;
  private int _code = 200;

  private class InspectableHttpResponse extends HttpResponse {
    InspectableHttpResponse() {
      super(0, null);
    }

    @Override
    public String body() {
      return "{ \"test\": true }";
    }

    @Override
    public int code() {
      return getCode();
    }

    public int getCode() {
      return _code;
    }
  }

  public InspectableHttpGetRequest() {
    super("http://test.com", Collections.emptyMap());
  }

  @Override
  public HttpGetRequest header(String name, String value) {
    _headers.put(name, value);
    return this;
  }

  public Map<String, String> getHeaders() {
    return _headers;
  }

  public void setUrl(String url) {
    _url = url;
  }

  public String getUrl() {
    return _url;
  }

  public void setQueryParams(Map<?, ?> queryParams) {
    _queryParams = queryParams;
  }

  public Map<?, ?> getQueryParams() {
    return _queryParams;
  }

  @Override
  public HttpResponse execute() throws IOException {
    return new InspectableHttpResponse();
  }
}
