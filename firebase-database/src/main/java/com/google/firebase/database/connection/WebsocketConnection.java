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

import com.google.firebase.database.connection.util.StringListReader;
import com.google.firebase.database.logging.LogWrapper;
import com.google.firebase.database.tubesock.WebSocket;
import com.google.firebase.database.tubesock.WebSocketEventHandler;
import com.google.firebase.database.tubesock.WebSocketException;
import com.google.firebase.database.tubesock.WebSocketMessage;
import com.google.firebase.database.util.JsonMapper;
import java.io.EOFException;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

class WebsocketConnection {

  private static long connectionId = 0;
  private static final long KEEP_ALIVE_TIMEOUT_MS = 45 * 1000; // 45 seconds
  private static final long CONNECT_TIMEOUT_MS = 30 * 1000; // 30 seconds
  private static final int MAX_FRAME_SIZE = 16384;

  public interface Delegate {
    public void onMessage(Map<String, Object> message);

    public void onDisconnect(boolean wasEverConnected);
  }

  private interface WSClient {
    public void connect();

    public void close();

    public void send(String msg);
  }

  private class WSClientTubesock implements WSClient, WebSocketEventHandler {

    private WebSocket ws;

    private WSClientTubesock(WebSocket ws) {
      this.ws = ws;
      this.ws.setEventHandler(this);
    }

    @Override
    public void onOpen() {
      executorService.execute(
          new Runnable() {
            @Override
            public void run() {
              connectTimeout.cancel(false);
              everConnected = true;
              if (logger.logsDebug()) logger.debug("websocket opened");
              resetKeepAlive();
            }
          });
    }

    @Override
    public void onMessage(WebSocketMessage msg) {
      final String str = msg.getText();
      if (logger.logsDebug()) logger.debug("ws message: " + str);
      executorService.execute(
          new Runnable() {
            @Override
            public void run() {
              handleIncomingFrame(str);
            }
          });
    }

    @Override
    public void onClose() {
      final String logMessage = "closed";
      executorService.execute(
          new Runnable() {
            @Override
            public void run() {
              if (logger.logsDebug()) logger.debug(logMessage);
              onClosed();
            }
          });
    }

    @Override
    public void onError(final WebSocketException e) {
      executorService.execute(
          new Runnable() {
            @Override
            public void run() {
              if (e.getCause() != null && e.getCause() instanceof EOFException) {
                logger.debug("WebSocket reached EOF.");
              } else {
                logger.debug("WebSocket error.", e);
              }
              onClosed();
            }
          });
    }

    @Override
    public void onLogMessage(String msg) {
      if (logger.logsDebug()) logger.debug("Tubesock: " + msg);
    }

    @Override
    public void send(String msg) {
      ws.send(msg);
    }

    @Override
    public void close() {
      ws.close();
    }

    private void shutdown() {
      ws.close();
      try {
        ws.blockClose();
      } catch (InterruptedException e) {
        logger.error("Interrupted while shutting down websocket threads", e);
      }
    }

    @Override
    public void connect() {
      try {
        ws.connect();
      } catch (WebSocketException e) {
        if (logger.logsDebug()) logger.debug("Error connecting", e);
        shutdown();
      }
    }
  }

  private WSClient conn;
  private boolean everConnected = false;
  private boolean isClosed = false;
  private long totalFrames = 0;
  private StringListReader frameReader;
  private Delegate delegate;
  private ScheduledFuture<?> keepAlive;
  private ScheduledFuture<?> connectTimeout;
  private final ConnectionContext connectionContext;
  private final ScheduledExecutorService executorService;
  private final LogWrapper logger;

  public WebsocketConnection(
      ConnectionContext connectionContext,
      HostInfo hostInfo,
      String optCachedHost,
      Delegate delegate,
      String optLastSessionId) {
    this.connectionContext = connectionContext;
    this.executorService = connectionContext.getExecutorService();
    this.delegate = delegate;
    long connId = connectionId++;
    logger = new LogWrapper(connectionContext.getLogger(), "WebSocket", "ws_" + connId);
    conn = createConnection(hostInfo, optCachedHost, optLastSessionId);
  }

  private WSClient createConnection(
      HostInfo hostInfo, String optCachedHost, String optLastSessionId) {
    String host = (optCachedHost != null) ? optCachedHost : hostInfo.getHost();
    URI uri =
        HostInfo.getConnectionUrl(
            host, hostInfo.isSecure(), hostInfo.getNamespace(), optLastSessionId);
    Map<String, String> extraHeaders = new HashMap<String, String>();
    extraHeaders.put("User-Agent", this.connectionContext.getUserAgent());
    extraHeaders.put("X-Firebase-GMPID", this.connectionContext.getApplicationId());
    WebSocket ws = new WebSocket(this.connectionContext, uri, /*protocol=*/ null, extraHeaders);
    WSClientTubesock client = new WSClientTubesock(ws);
    return client;
  }

  public void open() {
    conn.connect();
    connectTimeout =
        executorService.schedule(
            new Runnable() {
              @Override
              public void run() {
                closeIfNeverConnected();
              }
            },
            CONNECT_TIMEOUT_MS,
            TimeUnit.MILLISECONDS);
  }

  public void start() {
    // No-op in java
  }

  public void close() {
    if (logger.logsDebug()) logger.debug("websocket is being closed");
    isClosed = true;
    // Although true is passed for both of these, they each run on the same event loop, so they will
    // never be
    // running.
    conn.close();
    if (connectTimeout != null) {
      connectTimeout.cancel(true);
    }
    if (keepAlive != null) {
      keepAlive.cancel(true);
    }
  }

  public void send(Map<String, Object> message) {
    resetKeepAlive();

    try {
      String toSend = JsonMapper.serializeJson(message);
      String[] segs = splitIntoFrames(toSend, MAX_FRAME_SIZE);
      if (segs.length > 1) {
        conn.send("" + segs.length);
      }

      for (int i = 0; i < segs.length; ++i) {
        conn.send(segs[i]);
      }
    } catch (IOException e) {
      logger.error("Failed to serialize message: " + message.toString(), e);
      shutdown();
    }
  }

  private void appendFrame(String message) {
    frameReader.addString(message);
    totalFrames -= 1;
    if (totalFrames == 0) {
      // Decode JSON
      try {
        frameReader.freeze();
        Map<String, Object> decoded = JsonMapper.parseJson(frameReader.toString());
        frameReader = null;
        if (logger.logsDebug()) logger.debug("handleIncomingFrame complete frame: " + decoded);
        delegate.onMessage(decoded);
      } catch (IOException e) {
        logger.error("Error parsing frame: " + frameReader.toString(), e);
        close();
        shutdown();
      } catch (ClassCastException e) {
        logger.error("Error parsing frame (cast error): " + frameReader.toString(), e);
        close();
        shutdown();
      }
    }
  }

  private void handleNewFrameCount(int numFrames) {
    totalFrames = numFrames;
    frameReader = new StringListReader();
    if (logger.logsDebug()) logger.debug("HandleNewFrameCount: " + totalFrames);
  }

  private String extractFrameCount(String message) {
    // TODO: The server is only supposed to send up to 9999 frames (i.e. length <= 4), but that
    // isn't being enforced
    // currently.  So allowing larger frame counts (length <= 6).  See
    // https://app.asana.com/0/search/8688598998380/8237608042508
    if (message.length() <= 6) {
      try {
        int frameCount = Integer.parseInt(message);
        if (frameCount > 0) {
          handleNewFrameCount(frameCount);
        }
        return null;
      } catch (NumberFormatException e) {
        // not a number, default to framecount 1
      }
    }
    handleNewFrameCount(1);
    return message;
  }

  private void handleIncomingFrame(String message) {
    if (!isClosed) {
      resetKeepAlive();
      if (isBuffering()) {
        appendFrame(message);
      } else {
        String remaining = extractFrameCount(message);
        if (remaining != null) {
          appendFrame(remaining);
        }
      }
    }
  }

  private void resetKeepAlive() {
    if (!isClosed) {
      if (keepAlive != null) {
        keepAlive.cancel(false);
        if (logger.logsDebug())
          logger.debug("Reset keepAlive. Remaining: " + keepAlive.getDelay(TimeUnit.MILLISECONDS));
      } else {
        if (logger.logsDebug()) logger.debug("Reset keepAlive");
      }
      keepAlive = executorService.schedule(nop(), KEEP_ALIVE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }
  }

  private Runnable nop() {
    return new Runnable() {
      @Override
      public void run() {
        if (conn != null) {
          conn.send("0");
          resetKeepAlive();
        }
      }
    };
  }

  private boolean isBuffering() {
    return frameReader != null;
  }

  // Close methods

  private void onClosed() {
    if (!isClosed) {
      if (logger.logsDebug()) logger.debug("closing itself");
      shutdown();
    }
    conn = null;
    if (keepAlive != null) {
      keepAlive.cancel(false);
    }
  }

  private void shutdown() {
    isClosed = true;
    delegate.onDisconnect(everConnected);
  }

  private void closeIfNeverConnected() {
    if (!everConnected && !isClosed) {
      if (logger.logsDebug()) logger.debug("timed out on connect");
      conn.close();
    }
  }

  private static String[] splitIntoFrames(String src, int maxFrameSize) {
    if (src.length() <= maxFrameSize) {
      return new String[] {src};
    } else {
      ArrayList<String> segs = new ArrayList<String>();
      for (int i = 0; i < src.length(); i += maxFrameSize) {
        int end = Math.min(i + maxFrameSize, src.length());
        String seg = src.substring(i, end);
        segs.add(seg);
      }
      return segs.toArray(new String[segs.size()]);
    }
  }
}
