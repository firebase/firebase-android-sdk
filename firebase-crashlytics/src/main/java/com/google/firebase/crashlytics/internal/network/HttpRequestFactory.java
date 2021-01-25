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

import java.util.Collections;
import java.util.Map;

/** Factory for building {@link HttpGetRequest} objects. */
public class HttpRequestFactory {

  public HttpRequestFactory() {}

  /**
   * @param url {@link String} URL to be used in the returned {@link HttpGetRequest}
   * @return {@link HttpGetRequest} configured for the URL
   */
  public HttpGetRequest buildHttpGetRequest(String url) {
    return buildHttpGetRequest(url, Collections.<String, String>emptyMap());
  }

  /**
   * @param url {@link String} URL to be used in the returned {@link HttpGetRequest}
   * @param queryParams {@link Map} of {@link String} key/value pairs to be used as query parameters
   *     in the request.
   * @return {@link HttpGetRequest} configured for the URL
   */
  public HttpGetRequest buildHttpGetRequest(String url, Map<String, String> queryParams) {
    return new HttpGetRequest(url, queryParams);
  }
}
