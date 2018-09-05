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

import com.google.firebase.database.logging.LogWrapper;
import java.util.HashMap;
import java.util.Map;

class Connection implements WebsocketConnection.Delegate {

  public enum DisconnectReason {
    SERVER_RESET,
    OTHER
  }

  public interface Delegate {
    public void onCacheHost(String host);

    public void onReady(long timestamp, String sessionId);

    public void onDataMessage(Map<String, Object> message);

    public void onDisconnect(DisconnectReason reason);

    public void onKill(String reason);
  }

  private static long connectionIds = 0;

  private enum State {
    REALTIME_CONNECTING,
    REALTIME_CONNECTED,
    REALTIME_DISCONNECTED
  };

  private static final String REQUEST_TYPE = "t";
  private static final String REQUEST_TYPE_DATA = "d";
  private static final String REQUEST_PAYLOAD = "d";

  private static final String SERVER_ENVELOPE_TYPE = "t";
  private static final String SERVER_DATA_MESSAGE = "d";
  private static final String SERVER_CONTROL_MESSAGE = "c";
  private static final String SERVER_ENVELOPE_DATA = "d";

  private static final String SERVER_CONTROL_MESSAGE_TYPE = "t";
  private static final String SERVER_CONTROL_MESSAGE_SHUTDOWN = "s";
  private static final String SERVER_CONTROL_MESSAGE_RESET = "r";
  private static final String SERVER_CONTROL_MESSAGE_HELLO = "h";
  private static final String SERVER_CONTROL_MESSAGE_DATA = "d";

  private static final String SERVER_HELLO_TIMESTAMP = "ts";
  private static final String SERVER_HELLO_HOST = "h";
  private static final String SERVER_HELLO_SESSION_ID = "s";

  private HostInfo hostInfo;
  private WebsocketConnection conn;
  private Delegate delegate;
  private State state;
  private final LogWrapper logger;

  public Connection(
      ConnectionContext context,
      HostInfo hostInfo,
      String cachedHost,
      Delegate delegate,
      String optLastSessionId) {
    long connId = connectionIds++;
    this.hostInfo = hostInfo;
    this.delegate = delegate;
    this.logger = new LogWrapper(context.getLogger(), "Connection", "conn_" + connId);
    this.state = State.REALTIME_CONNECTING;
    this.conn = new WebsocketConnection(context, hostInfo, cachedHost, this, optLastSessionId);
  }

  public void open() {
    if (logger.logsDebug()) logger.debug("Opening a connection");
    conn.open();
  }

  public void close(DisconnectReason reason) {
    if (state != State.REALTIME_DISCONNECTED) {
      if (logger.logsDebug()) logger.debug("closing realtime connection");
      state = State.REALTIME_DISCONNECTED;

      if (conn != null) {
        conn.close();
        conn = null;
      }

      delegate.onDisconnect(reason);
    }
  }

  public void close() {
    close(DisconnectReason.OTHER);
  }

  public void sendRequest(Map<String, Object> message, boolean isSensitive) {
    // This came from the persistent connection. Wrap it in an envelope and send it

    Map<String, Object> request = new HashMap<String, Object>();
    request.put(REQUEST_TYPE, REQUEST_TYPE_DATA);
    request.put(REQUEST_PAYLOAD, message);

    sendData(request, isSensitive);
  }

  @Override
  public void onMessage(Map<String, Object> message) {
    try {
      String messageType = (String) message.get(SERVER_ENVELOPE_TYPE);
      if (messageType != null) {
        if (messageType.equals(SERVER_DATA_MESSAGE)) {
          @SuppressWarnings("unchecked")
          Map<String, Object> data = (Map<String, Object>) message.get(SERVER_ENVELOPE_DATA);
          onDataMessage(data);
        } else if (messageType.equals(SERVER_CONTROL_MESSAGE)) {
          @SuppressWarnings("unchecked")
          Map<String, Object> data = (Map<String, Object>) message.get(SERVER_ENVELOPE_DATA);
          onControlMessage(data);
        } else {
          if (logger.logsDebug())
            logger.debug("Ignoring unknown server message type: " + messageType);
        }
      } else {
        if (logger.logsDebug())
          logger.debug(
              "Failed to parse server message: missing message type:" + message.toString());
        close();
      }
    } catch (ClassCastException e) {
      if (logger.logsDebug()) logger.debug("Failed to parse server message: " + e.toString());
      close();
    }
  }

  @Override
  public void onDisconnect(boolean wasEverConnected) {
    conn = null;
    if (!wasEverConnected && state == State.REALTIME_CONNECTING) {
      if (logger.logsDebug()) logger.debug("Realtime connection failed");
    } else {
      if (logger.logsDebug()) logger.debug("Realtime connection lost");
    }

    close();
  }

  private void onDataMessage(Map<String, Object> data) {
    if (logger.logsDebug()) logger.debug("received data message: " + data.toString());
    // We don't do anything with data messages, just kick them up a level
    delegate.onDataMessage(data);
  }

  private void onControlMessage(Map<String, Object> data) {
    if (logger.logsDebug()) logger.debug("Got control message: " + data.toString());
    try {
      String messageType = (String) data.get(SERVER_CONTROL_MESSAGE_TYPE);
      if (messageType != null) {
        if (messageType.equals(SERVER_CONTROL_MESSAGE_SHUTDOWN)) {
          String reason = (String) data.get(SERVER_CONTROL_MESSAGE_DATA);
          onConnectionShutdown(reason);
        } else if (messageType.equals(SERVER_CONTROL_MESSAGE_RESET)) {
          String host = (String) data.get(SERVER_CONTROL_MESSAGE_DATA);
          onReset(host);
        } else if (messageType.equals(SERVER_CONTROL_MESSAGE_HELLO)) {
          @SuppressWarnings("unchecked")
          Map<String, Object> handshakeData =
              (Map<String, Object>) data.get(SERVER_CONTROL_MESSAGE_DATA);
          onHandshake(handshakeData);
        } else {
          if (logger.logsDebug()) logger.debug("Ignoring unknown control message: " + messageType);
        }
      } else {
        if (logger.logsDebug()) logger.debug("Got invalid control message: " + data.toString());
        close();
      }
    } catch (ClassCastException e) {
      if (logger.logsDebug()) logger.debug("Failed to parse control message: " + e.toString());
      close();
    }
  }

  private void onConnectionShutdown(String reason) {
    if (logger.logsDebug()) logger.debug("Connection shutdown command received. Shutting down...");
    delegate.onKill(reason);
    close();
  }

  private void onHandshake(Map<String, Object> handshake) {
    long timestamp = (Long) handshake.get(SERVER_HELLO_TIMESTAMP);
    String host = (String) handshake.get(SERVER_HELLO_HOST);
    delegate.onCacheHost(host);
    String sessionId = (String) handshake.get(SERVER_HELLO_SESSION_ID);

    if (state == State.REALTIME_CONNECTING) {
      conn.start();
      onConnectionReady(timestamp, sessionId);
    }
  }

  private void onConnectionReady(long timestamp, String sessionId) {
    if (logger.logsDebug()) logger.debug("realtime connection established");
    state = State.REALTIME_CONNECTED;
    delegate.onReady(timestamp, sessionId);
  }

  private void onReset(String host) {
    if (logger.logsDebug())
      logger.debug(
          "Got a reset; killing connection to "
              + this.hostInfo.getHost()
              + "; Updating internalHost to "
              + host);
    delegate.onCacheHost(host);

    // Explicitly close the connection with SERVER_RESET so calling code knows to reconnect
    // immediately.
    close(DisconnectReason.SERVER_RESET);
  }

  private void sendData(Map<String, Object> data, boolean isSensitive) {
    if (state != State.REALTIME_CONNECTED) {
      logger.debug("Tried to send on an unconnected connection");
    } else {
      if (isSensitive) {
        logger.debug("Sending data (contents hidden)");
      } else {
        logger.debug("Sending data: %s", data);
      }
      conn.send(data);
    }
  }

  // For testing
  public void injectConnectionFailure() {
    this.close();
  }
}
