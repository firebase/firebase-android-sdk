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

import com.google.firebase.crashlytics.internal.Logger;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.net.ssl.HttpsURLConnection;

public class HttpGetRequest {
  private static final String METHOD_GET = "GET";
  private static final int DEFAULT_TIMEOUT_MS = 10 * 1000; // 10 seconds in millis
  private static final int READ_BUFFER_SIZE = 8192;

  private final String url;
  private final Map<String, String> queryParams;
  private final Map<String, String> headers;

  public HttpGetRequest(String url, Map<String, String> queryParams) {
    this.url = url;
    this.queryParams = queryParams;
    this.headers = new HashMap<>();
  }

  /*
   * Request setters
   */

  public HttpGetRequest header(String name, String value) {
    headers.put(name, value);
    return this;
  }

  public HttpGetRequest header(Map.Entry<String, String> entry) {
    return header(entry.getKey(), entry.getValue());
  }

  public HttpResponse execute() throws IOException {
    InputStream stream = null;
    HttpsURLConnection connection = null;
    String body = null;
    int responseCode = -1;
    try {
      String urlWithParams = createUrlWithParams(url, queryParams);

      Logger.getLogger().v("GET Request URL: " + urlWithParams);

      connection = (HttpsURLConnection) new URL(urlWithParams).openConnection();
      connection.setReadTimeout(DEFAULT_TIMEOUT_MS);
      connection.setConnectTimeout(DEFAULT_TIMEOUT_MS);
      connection.setRequestMethod(METHOD_GET);

      for (Map.Entry<String, String> entry : headers.entrySet()) {
        connection.addRequestProperty(entry.getKey(), entry.getValue());
      }

      // Open communications link (network traffic occurs here).
      connection.connect();
      responseCode = connection.getResponseCode();

      // Retrieve the response body as an InputStream.
      stream = connection.getInputStream();
      if (stream != null) {
        // Converts Stream to String with max length of 500.
        body = readStream(stream);
      }
    } finally {
      // Close Stream and disconnect HTTPS connection.
      if (stream != null) {
        stream.close();
      }
      if (connection != null) {
        connection.disconnect();
      }
    }

    return new HttpResponse(responseCode, body);
  }

  private String createUrlWithParams(String url, Map<String, String> queryParams)
      throws UnsupportedEncodingException {
    String queryParamsString = createParamsString(queryParams);

    if (queryParamsString.isEmpty()) {
      return url;
    }

    if (url.contains("?")) {
      if (!url.endsWith("&")) {
        queryParamsString = "&" + queryParamsString;
      }
      return url + queryParamsString;
    } else {
      return url + "?" + queryParamsString;
    }
  }

  private String createParamsString(Map<String, String> queryParams)
      throws UnsupportedEncodingException {
    StringBuilder paramsString = new StringBuilder();
    Iterator<Map.Entry<String, String>> iterator = queryParams.entrySet().iterator();
    Map.Entry<String, String> entry = iterator.next();
    paramsString
        .append(entry.getKey())
        .append("=")
        .append(entry.getValue() != null ? URLEncoder.encode(entry.getValue(), "UTF-8") : "");
    while (iterator.hasNext()) {
      entry = iterator.next();
      paramsString
          .append("&")
          .append(entry.getKey())
          .append("=")
          .append(entry.getValue() != null ? URLEncoder.encode(entry.getValue(), "UTF-8") : "");
    }
    return paramsString.toString();
  }

  private String readStream(InputStream stream) throws IOException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
    int charsRead;
    char[] charBuffer = new char[READ_BUFFER_SIZE];
    StringBuilder result = new StringBuilder();
    while ((charsRead = reader.read(charBuffer)) != -1) {
      result.append(charBuffer, 0, charsRead);
    }
    return result.toString();
  }
}
