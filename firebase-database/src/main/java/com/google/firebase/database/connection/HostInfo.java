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
      URI url =
          isUsableSessionId(optLastSessionId)
              ? tryGetConnectionUrl(connectionHost, optLastSessionId)
              : null;
      if (url != null) {
        return url;
      }
      logger.warn("Ignoring a session ID from the server that can't be used in a URL");
    }
    return getConnectionUrl(connectionHost, secure, namespace, /* optLastSessionId= */ null);
  }

  /**
   * Returns whether a host that the server sent can be used to connect to this database, that is,
   * whether it is a host name or IP address with an optional port, and nothing else. This only
   * checks the syntax. It doesn't restrict which hosts the server can send.
   */
  boolean isUsableHost(String host) {
    URI url = tryGetConnectionUrl(host, /* optLastSessionId= */ null);
    // Without a host in the URL, the websocket would connect to the loopback address.
    if (url == null || url.getHost() == null) {
      return false;
    }
    int port = url.getPort();
    if (port != -1 && (port < 1 || port > 65535)) {
      return false;
    }
    // The host must be all of the URL's authority. Otherwise it contained a scheme, user info, a
    // path, a query, or a fragment, and the websocket would connect to a different host or lose the
    // /.ws path and its query parameters.
    String authority = port == -1 ? url.getHost() : url.getHost() + ":" + port;
    return authority.equals(host);
  }

  /**
   * Returns whether a session ID that the server sent can be used as the value of the last session
   * ID query parameter.
   *
   * <p>{@code WebSocketHandshake} writes the percent-decoded query ({@link URI#getQuery()}) into
   * the HTTP request line, so the session ID must be a valid query value both as it is and after
   * decoding. Otherwise a percent-encoded space would break the request, and an encoded CR LF
   * would end the request line. It also must not contain {@code &} or {@code #}, which would add a
   * query parameter or start a fragment.
   */
  private static boolean isUsableSessionId(String sessionId) {
    String decoded = decodeQueryValue(sessionId);
    return decoded != null && decoded.indexOf('&') == -1 && decodeQueryValue(decoded) != null;
  }

  /**
   * Returns the percent-decoded value, or null if the value isn't a valid URI query without a
   * fragment.
   */
  @Nullable
  private static String decodeQueryValue(String value) {
    try {
      URI uri = URI.create("?" + value);
      return uri.getRawFragment() == null ? uri.getQuery() : null;
    } catch (IllegalArgumentException e) {
      return null;
    }
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
