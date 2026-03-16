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

package com.google.firebase.crashlytics.buildtools.api.net.proxy;

import com.google.firebase.crashlytics.buildtools.Buildtools;
import java.io.IOException;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClients;

public class ProxySettings {
  private final String _proxyHost;
  private final Integer _proxyPort;
  private final String _proxyUser;
  private final String _proxyPassword;

  public ProxySettings(
      String proxyHost, Integer proxyPort, String proxyUser, String proxyPassword) {
    _proxyHost = proxyHost;
    _proxyPort = proxyPort;
    _proxyUser = proxyUser;
    _proxyPassword = proxyPassword;
  }

  public RequestConfig getConfig() {
    if (_proxyHost == null || _proxyPort == null) {
      return RequestConfig.DEFAULT;
    }

    Buildtools.logD("Crashlytics using custom proxy settings: " + _proxyHost + ":" + _proxyPort);
    HttpHost proxy = new HttpHost(_proxyHost, _proxyPort);

    return RequestConfig.custom().setProxy(proxy).build();
  }

  /**
   * Returns a new DefaultHttpClient instance, to be used as the client for all API calls. Can be
   * overridden to return a mock for testing.
   * <p/>
   * This client will use proxy settings if they have been set from system properties
   *
   * @throws IOException if the port was not a valid string port
   */
  public HttpClient getClientFor() throws IOException {

    // Set the auth password / user if one exists.
    if (_proxyHost == null || _proxyPort == null || _proxyUser == null || _proxyPassword == null) {
      return HttpClients.createDefault();
    }

    Buildtools.logD("Crashlytics using proxy auth:" + _proxyUser);

    CredentialsProvider credsProvider = new BasicCredentialsProvider();
    Credentials credentials = new UsernamePasswordCredentials(_proxyUser, _proxyPassword);
    AuthScope authScope = new AuthScope(_proxyHost, _proxyPort);
    credsProvider.setCredentials(authScope, credentials);

    return HttpClients.custom().setDefaultCredentialsProvider(credsProvider).build();
  }
}
