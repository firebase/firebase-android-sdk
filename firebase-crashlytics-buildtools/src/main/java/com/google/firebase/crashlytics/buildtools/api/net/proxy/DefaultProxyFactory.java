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

public class DefaultProxyFactory implements ProxyFactory {

  // Default ProxySettings used when there's no proxy override specified.
  private static final ProxySettings DEFAULT_PROXY_SETTINGS =
      new ProxySettings(null, null, null, null);

  private static final String OVERRIDE_DEBUG_MSG_FMT =
      "Found proxy override specified in %s. [host=%s; port=%d; username=%s; pw=HIDDEN]";

  public ProxySettings create(ProtocolScheme scheme) throws IOException {

    // Overrides from properties take priority over overrides from environment variables.
    ProxySettings overrideProxySettings = createFromProperties(scheme);
    if (overrideProxySettings == null) {
      overrideProxySettings = createFromEnvironment(scheme);
    }
    return (overrideProxySettings == null) ? DEFAULT_PROXY_SETTINGS : overrideProxySettings;
  }

  private ProxySettings createFromProperties(ProtocolScheme scheme) throws NumberFormatException {
    String proxyHost = null;
    String proxyUser = null;
    String proxyPassword = null;
    String proxyPortString = null;

    switch (scheme) {
      case HTTP:
        proxyHost = System.getProperty(Constants.HTTP_PROXY_HOST_PROP);
        proxyPortString = System.getProperty(Constants.HTTP_PROXY_PORT_PROP);
        proxyUser = System.getProperty(Constants.HTTP_PROXY_USER_PROP);
        proxyPassword = System.getProperty(Constants.HTTP_PROXY_PASSWORD_PROP);
        break;
      case HTTPS:
        proxyHost = System.getProperty(Constants.HTTPS_PROXY_HOST_PROP);
        proxyPortString = System.getProperty(Constants.HTTPS_PROXY_PORT_PROP);
        proxyUser = System.getProperty(Constants.HTTPS_PROXY_USER_PROP);
        proxyPassword = System.getProperty(Constants.HTTPS_PROXY_PASSWORD_PROP);
        break;
      default:
        break;
    }

    if (proxyHost == null
        && proxyPortString == null
        && proxyUser == null
        && proxyPassword == null) {
      return null;
    }

    Integer proxyPort = null;
    if (proxyPortString != null) {
      proxyPort = Integer.parseInt(proxyPortString);
    }

    Buildtools.logD(
        String.format(OVERRIDE_DEBUG_MSG_FMT, "properties", proxyHost, proxyPort, proxyUser));

    return new ProxySettings(proxyHost, proxyPort, proxyUser, proxyPassword);
  }

  private ProxySettings createFromEnvironment(ProtocolScheme scheme) throws NumberFormatException {
    String proxyString = null;

    switch (scheme) {
      case HTTP:
        proxyString = System.getenv().get(Constants.HTTP_PROXY_ENV);
        break;
      case HTTPS:
        proxyString = System.getenv().get(Constants.HTTPS_PROXY_ENV);
        break;
      default:
        break;
    }
    if (proxyString == null) {
      // No override from environment variable
      return null;
    }

    String[] proxyValues = proxyString.split(":");
    if (proxyValues.length != 3) {
      throw new IllegalArgumentException(
          "Could not parse proxy string from environment "
              + "variable value: "
              + proxyString
              + "; expected: http[s]://host:port");
    }

    // remove the "//" characters from the front of the hostname
    String proxyHost = proxyValues[1].substring(2);
    Integer proxyPort = Integer.parseInt(proxyValues[2]);

    Buildtools.logD(
        String.format(OVERRIDE_DEBUG_MSG_FMT, "environment variable", proxyHost, proxyPort, null));

    return new ProxySettings(proxyHost, proxyPort, null, null);
  }
}
