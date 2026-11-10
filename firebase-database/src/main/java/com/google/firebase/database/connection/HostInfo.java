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

package com.google.firebase.database.connection;

import androidx.annotation.Nullable;
import com.google.firebase.database.logging.LogWrapper;
import java.net.URI;

public class HostInfo {

  private static final String VERSION_PARAM = "v";
  private static final String LAST_SESSION_ID_PARAM = "ls";

  private final String host;
  private final String namespace;
  private final boolean secure;

  public HostInfo(String host, String namespace, boolean secure) {
    this.host = host;
    this.namespace = namespace;
    this.secure = secure;
  }

  @Override
  public String toString() {
    return "http" + (secure ? "s" : "") + "://" + host;
  }

  public static URI getConnectionUrl(
      String host, boolean secure, String namespace, String optLastSessionId) {
    String scheme = secure ? "wss" : "ws";
    String url =
        scheme
            + "://"
            + host
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

  /**
   * Returns the URL for a new connection to this database.
   *
   * <p>The cached host and the last session ID were sent by the server. If one of them can't be
   * used in the URL, it is ignored with a warning: the connection goes to the configured host, or
   * starts without a last session ID. Each value is checked on its own, so that a bad session ID
   * doesn't also discard a good host. Otherwise, if the configured host reset the connection to
   * that host, the client would reconnect in a tight loop.
   */
  URI getConnectionUrl(
      @Nullable String optCachedHost, @Nullable String optLastSessionId, LogWrapper logger) {
    String connectionHost = host;
    if (optCachedHost != null) {
      if (isUsableHost(optCachedHost)) {
        connectionHost = optCachedHost;
      } else {
        logger.warn(
            "Ignoring a host from the server that can't be used in a URL, connecting to the"
                + " configured host instead");
      }
    }
    if (optLastSessionId != null) {
      URI url = tryGetConnectionUrl(connectionHost, optLastSessionId);
      if (url != null) {
        return url;
      }
      logger.warn("Ignoring a session ID from the server that can't be used in a URL");
    }
    return getConnectionUrl(connectionHost, secure, namespace, /* optLastSessionId= */ null);
  }

  /**
   * Returns whether a host that the server sent can be used to connect to this database, that is,
   * whether the connection URL for it is a valid URI that names a host. This only checks the
   * syntax. It doesn't restrict which hosts the server can send.
   */
  boolean isUsableHost(String host) {
    URI url = tryGetConnectionUrl(host, /* optLastSessionId= */ null);
    // Without a host in the URL, the websocket would connect to the loopback address.
    return url != null && url.getHost() != null;
  }

  /** Returns the connection URL, or null if it isn't a valid URI. */
  @Nullable
  private URI tryGetConnectionUrl(String host, @Nullable String optLastSessionId) {
    try {
      return getConnectionUrl(host, secure, namespace, optLastSessionId);
    } catch (IllegalArgumentException e) {
      // URI.create throws if the URL contains characters that aren't allowed in a URI, such as a
      // space.
      return null;
    }
  }

  public String getHost() {
    return this.host;
  }

  public String getNamespace() {
    return this.namespace;
  }

  public boolean isSecure() {
    return secure;
  }
}
