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

import com.google.firebase.crashlytics.buildtools.api.net.Constants;
import java.net.URI;
import java.net.URL;

public enum ProtocolScheme {
  HTTP, //
  HTTPS, //
  Other;

  public static ProtocolScheme getType(URL url) {
    return getType(url.getProtocol());
  }

  public static ProtocolScheme getType(URI uri) {
    return getType(uri.getScheme());
  }

  private static ProtocolScheme getType(String protocolString) {
    if (protocolString.equalsIgnoreCase(Constants.Http.HTTP)) {
      return ProtocolScheme.HTTP;
    } else if (protocolString.equalsIgnoreCase(Constants.Http.HTTPS)) {
      return ProtocolScheme.HTTPS;
    } else {
      return ProtocolScheme.Other;
    }
  }
}
