/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.crashlytics.buildtools.api;

import com.google.firebase.crashlytics.buildtools.Buildtools;
import com.google.firebase.crashlytics.buildtools.api.net.Constants;
import com.google.firebase.crashlytics.buildtools.api.net.proxy.ProtocolScheme;
import com.google.firebase.crashlytics.buildtools.api.net.proxy.ProxyFactory;
import com.google.firebase.crashlytics.buildtools.api.net.proxy.ProxySettings;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.FileEntity;

public class RestfulWebApi implements WebApi {

  private final ProxyFactory _proxyFactory;

  private String _userAgent;
  private String _clientVersion = null;
  private String _clientType = null;

  private final String _codeMappingApiUrl;

  @Override
  public String toString() {
    // Intentionally only passing diagnostic information here, not _baseApiUrl, _userAgent, or
    // cached app / orgs / user
    return " ClientType: " + _clientType + " (" + _clientVersion + ")";
  }

  public RestfulWebApi(String codeMappingApiUrl, ProxyFactory proxyFactory) {
    _proxyFactory = proxyFactory;
    _codeMappingApiUrl = codeMappingApiUrl;
  }

  public synchronized void setUserAgent(String userAgent) {
    _userAgent = userAgent;
  }

  @Override
  public synchronized void setClientType(String clientType) {
    _clientType = clientType;
  }

  @Override
  public synchronized void setClientVersion(String clientVersion) {
    _clientVersion = clientVersion;
  }

  /**
   * Used by both the public sendFile method and the uploadFile method. This has most of the implementation and
   * takes both HTTP body params and HTTP headers as arguments.
   */
  private void sendFile(URL url, File file, Map<String, String> headers) throws IOException {

    Buildtools.logD("PUT file: " + file + " to URL: " + url);

    HttpPut httpPut;
    try {
      httpPut = new HttpPut(url.toURI());
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }

    for (Map.Entry<String, String> header : headers.entrySet()) {
      httpPut.setHeader(header.getKey(), header.getValue());
    }

    applyCommonHeadersTo(httpPut);

    FileEntity fileToUpload = new FileEntity(file);

    httpPut.setEntity(fileToUpload);

    ProxySettings settings = _proxyFactory.create(ProtocolScheme.getType(url));
    HttpClient client = settings.getClientFor();
    httpPut.setConfig(settings.getConfig());

    Buildtools.logD("PUT headers:");
    for (Header header : httpPut.getAllHeaders()) {
      Buildtools.logD("\t" + header.getName() + " = " + header.getValue());
    }

    HttpResponse response = client.execute(httpPut);
    int result = response.getStatusLine().getStatusCode();

    Buildtools.logD("PUT response: [reqId=" + getRequestId(response) + "] " + result);

    final boolean success = isSuccess(result);
    if (!success) {
      // This is likely a backend exception or network timeout.
      throw new IOException(
          "Unknown error while sending file, check network ["
              + file
              + "; response: "
              + response.getStatusLine().getStatusCode()
              + " "
              + response.getStatusLine()
              + "]");
    }
  }

  @Override
  public void uploadFile(URL url, File file) throws IOException {

    final Map<String, String> headers = new HashMap<>();

    sendFile(url, file, headers);
  }

  public void applyCommonHeadersTo(HttpRequestBase request) {
    if (_userAgent != null) {
      request.setHeader(Constants.Http.USER_AGENT_HEADER, _userAgent);
    }

    if (_clientType != null) {
      request.setHeader(Constants.Http.API_CLIENT_TYPE_HEADER, _clientType);
    }

    if (_clientVersion != null) {
      request.setHeader(Constants.Http.API_CLIENT_VERSION_HEADER, _clientVersion);
    }
  }

  @Override
  public String getCodeMappingApiUrl() {
    return _codeMappingApiUrl;
  }

  /**
   * Returns the X-Request-Id header value from the HttpResponse, or literal "null" string if it does not.
   */
  private static String getRequestId(HttpResponse response) {
    Header requestIdHeader = response.getFirstHeader(Constants.Http.REQUEST_ID_HEADER);
    return requestIdHeader == null ? "null" : requestIdHeader.getValue();
  }

  private static boolean isSuccess(int resultCode) {
    return resultCode >= 200 && resultCode < 300;
  }
}
