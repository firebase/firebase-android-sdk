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

public class Constants {
  // System properties containing proxy overrides
  public static final String HTTP_PROXY_PORT_PROP = "http.proxyPort";
  public static final String HTTP_PROXY_HOST_PROP = "http.proxyHost";
  public static final String HTTP_PROXY_USER_PROP = "http.proxyUser";
  public static final String HTTP_PROXY_PASSWORD_PROP = "http.proxyPassword";

  public static final String HTTPS_PROXY_PORT_PROP = "https.proxyPort";
  public static final String HTTPS_PROXY_HOST_PROP = "https.proxyHost";
  public static final String HTTPS_PROXY_USER_PROP = "https.proxyUser";
  public static final String HTTPS_PROXY_PASSWORD_PROP = "https.proxyPassword";

  // System environment variables proxy overrides (ex: HTTP_PROXY=http://127.0.0.1:12345)
  // Public docs for Firebase CLI: https://firebaseopensource.com/projects/firebase/firebase-tools/
  public static final String HTTPS_PROXY_ENV = "HTTPS_PROXY";
  public static final String HTTP_PROXY_ENV = "HTTP_PROXY";
}
