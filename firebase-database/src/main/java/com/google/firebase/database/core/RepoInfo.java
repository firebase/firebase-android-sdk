// Copyright 2018 Google LLC
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

package com.google.firebase.database.core;

import com.google.firebase.database.annotations.Nullable;
import com.google.firebase.emulators.EmulatedServiceSettings;
import java.net.URI;

public final class RepoInfo {

  private static final String VERSION_PARAM = "v";
  private static final String LAST_SESSION_ID_PARAM = "ls";

  public String host;
  public boolean secure;
  public String namespace;
  public String internalHost;

  @Override
  public String toString() {
    return "http" + (secure ? "s" : "") + "://" + host;
  }

  public String toDebugString() {
    return "(host="
        + host
        + ", secure="
        + secure
        + ", ns="
        + namespace
        + " internal="
        + internalHost
        + ")";
  }

  public URI getConnectionURL(String optLastSessionId) {
    String scheme = secure ? "wss" : "ws";
    String url =
        scheme
            + "://"
            + internalHost
            + "/.ws?ns="
            + namespace
            + "&"
            + VERSION_PARAM
            + "="
            + Constants.WIRE_PROTOCOL_VERSION;
    if (optLastSessionId != null) {
      url += "&" + LAST_SESSION_ID_PARAM + "=" + optLastSessionId;
    }
    return URI.create(url);
  }

  public void applyEmulatorSettings(@Nullable EmulatedServiceSettings settings) {
    if (settings == null) {
      return;
    }

    this.host = settings.getHost() + ":" + settings.getPort();
    this.internalHost = this.host;
    this.secure = false;
  }

  public boolean isCacheableHost() {
    return internalHost.startsWith("s-");
  }

  public boolean isSecure() {
    return secure;
  }

  public boolean isDemoHost() {
    return host.contains(".firebaseio-demo.com");
  }

  public boolean isCustomHost() {
    return !host.contains(".firebaseio.com") && !host.contains(".firebaseio-demo.com");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    RepoInfo repoInfo = (RepoInfo) o;

    if (secure != repoInfo.secure) {
      return false;
    }
    if (!host.equals(repoInfo.host)) {
      return false;
    }
    return namespace.equals(repoInfo.namespace);
  }

  @Override
  public int hashCode() {
    int result = host.hashCode();
    result = 31 * result + (secure ? 1 : 0);
    result = 31 * result + namespace.hashCode();
    return result;
  }
}
