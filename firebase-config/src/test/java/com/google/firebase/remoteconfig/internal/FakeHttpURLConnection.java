// Copyright 2018 Google LLC
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

package com.google.firebase.remoteconfig.internal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Fake {@link HttpURLConnection} for Firebase Remote Config Android SDK testing purposes.
 *
 * @author Lucas Png
 */
public class FakeHttpURLConnection extends HttpURLConnection {
  private byte[] response;

  /** A stream that collects the POST body that the caller might give us. */
  private final ByteArrayOutputStream out = new ByteArrayOutputStream();

  private final Map<String, String> requestHeaders = new HashMap<>();
  private final Map<String, String> responseHeaders = new HashMap<>();

  public FakeHttpURLConnection(URL url) {
    super(url);
  }

  void setFakeResponse(byte[] response, String responseETag) {
    responseHeaders.put("ETag", responseETag);
    this.response = response;
  }

  /** Registers the given URL as hit. */
  @Override
  public void connect() {}

  @Override
  public boolean usingProxy() {
    return false;
  }

  @Override
  public void disconnect() {}

  /** Intercept any headers that are set, for testing. */
  @Override
  public void setRequestProperty(String header, String value) {
    requestHeaders.put(header, value);
    super.setRequestProperty(header, value);
  }

  public Map<String, String> getRequestHeaders() {
    return requestHeaders;
  }

  /** Returns a subverted output stream that the caller will write their POST body into. */
  @Override
  public OutputStream getOutputStream() {
    return out;
  }

  /** Returns the body of the response, which is empty if there was no response for us. */
  @Override
  public InputStream getInputStream() {
    if (response == null) {
      response = new byte[0];
    }

    return new ByteArrayInputStream(response);
  }

  /** Returns 200 if there is a response or 404 if there isn't. */
  @Override
  public int getResponseCode() {
    return response != null ? 200 : 404;
  }

  @Override
  public String getHeaderField(String headerName) {
    return responseHeaders.get(headerName);
  }

  @Override
  public String getResponseMessage() {
    return response != null ? Arrays.toString(response) : "Bad Request";
  }
}
