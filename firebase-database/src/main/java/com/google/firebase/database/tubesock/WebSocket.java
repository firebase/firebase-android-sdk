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

package com.google.firebase.database.tubesock;

import android.net.SSLSessionCache;
import androidx.annotation.Nullable;
import com.google.firebase.database.connection.ConnectionContext;
import com.google.firebase.database.logging.LogWrapper;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.SocketFactory;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

/**
 * This is the main class used to create a websocket connection. Create a new instance, set an event
 * handler, and then call connect(). Once the event handler's onOpen method has been called, call
 * send() on the websocket to transmit data.
 */
public class WebSocket {
  private static final int SSL_HANDSHAKE_TIMEOUT_MS = 60 * 1000;
  private static final String THREAD_BASE_NAME = "TubeSock";
  private static final AtomicInteger clientCount = new AtomicInteger(0);

  private enum State {
    NONE,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    DISCONNECTED
  };

  private static final Charset UTF8 = Charset.forName("UTF-8");

  static final byte OPCODE_NONE = 0x0;
  static final byte OPCODE_TEXT = 0x1;
  static final byte OPCODE_BINARY = 0x2;
  static final byte OPCODE_CLOSE = 0x8;
  static final byte OPCODE_PING = 0x9;
  static final byte OPCODE_PONG = 0xA;

  private volatile State state = State.NONE;
  private volatile Socket socket = null;

  private WebSocketEventHandler eventHandler = null;

  private final URI url;
  @Nullable private final String sslCacheDirectory;

  private final WebSocketReceiver receiver;
  private final WebSocketWriter writer;
  private final WebSocketHandshake handshake;
  private final LogWrapper logger;
  private final int clientId = clientCount.incrementAndGet();

  private final Thread innerThread;
  private static ThreadFactory threadFactory = Executors.defaultThreadFactory();
  private static ThreadInitializer intializer =
      new ThreadInitializer() {
        @Override
        public void setName(Thread t, String name) {
          t.setName(name);
        }
      };

  static ThreadFactory getThreadFactory() {
    return threadFactory;
  }

  static ThreadInitializer getIntializer() {
    return intializer;
  }

  public static void setThreadFactory(ThreadFactory threadFactory, ThreadInitializer intializer) {
    WebSocket.threadFactory = threadFactory;
    WebSocket.intializer = intializer;
  }

  /**
   * Create a websocket to connect to a given server
   *
   * @param url The URL of a websocket server
   */
  public WebSocket(ConnectionContext context, URI url) {
    this(context, url, null);
  }

  /**
   * Create a websocket to connect to a given server. Include protocol in websocket handshake
   *
   * @param url The URL of a websocket server
   * @param protocol The protocol to include in the handshake. If null, it will be omitted
   */
  public WebSocket(ConnectionContext context, URI url, String protocol) {
    this(context, url, protocol, null);
  }

  /**
   * Create a websocket to connect to a given server. Include the given protocol in the handshake,
   * as well as any extra HTTP headers specified. Useful if you would like to include a User-Agent
   * or other header
   *
   * @param url The URL of a websocket server
   * @param protocol The protocol to include in the handshake. If null, it will be omitted
   * @param extraHeaders Any extra HTTP headers to be included with the initial request. Pass null
   *     if not extra headers are requested
   */
  public WebSocket(
      ConnectionContext context, URI url, String protocol, Map<String, String> extraHeaders) {
    innerThread =
        getThreadFactory()
            .newThread(
                new Runnable() {
                  @Override
                  public void run() {
                    runReader();
                  }
                });
    this.url = url;
    sslCacheDirectory = context.getSslCacheDirectory();
    logger = new LogWrapper(context.getLogger(), "WebSocket", "sk_" + clientId);
    handshake = new WebSocketHandshake(url, protocol, extraHeaders);
    receiver = new WebSocketReceiver(this);
    writer = new WebSocketWriter(this, THREAD_BASE_NAME, clientId);
  }

  /**
   * Must be called before connect(). Set the handler for all websocket-related events.
   *
   * @param eventHandler The handler to be triggered with relevant events
   */
  public void setEventHandler(WebSocketEventHandler eventHandler) {
    this.eventHandler = eventHandler;
  }

  WebSocketEventHandler getEventHandler() {
    return this.eventHandler;
  }

  /**
   * Start up the socket. This is non-blocking, it will fire up the threads used by the library and
   * then trigger the onOpen handler once the connection is established.
   */
  public synchronized void connect() {
    if (state != State.NONE) {
      eventHandler.onError(new WebSocketException("connect() already called"));
      close();
      return;
    }
    getIntializer().setName(getInnerThread(), THREAD_BASE_NAME + "Reader-" + clientId);
    state = State.CONNECTING;
    getInnerThread().start();
  }

  /**
   * Send a TEXT message over the socket
   *
   * @param data The text payload to be sent
   */
  public synchronized void send(String data) {
    send(OPCODE_TEXT, data.getBytes(UTF8));
  }

  /**
   * Send a BINARY message over the socket
   *
   * @param data The binary payload to be sent
   */
  public synchronized void send(byte[] data) {
    send(OPCODE_BINARY, data);
  }

  synchronized void pong(byte[] data) {
    send(OPCODE_PONG, data);
  }

  private synchronized void send(byte opcode, byte[] data) {
    if (state != State.CONNECTED) {
      // We might have been disconnected on another thread, just report an error
      eventHandler.onError(new WebSocketException("error while sending data: not connected"));
    } else {
      try {
        writer.send(opcode, true, data);
      } catch (IOException e) {
        eventHandler.onError(new WebSocketException("Failed to send frame", e));
        close();
      }
    }
  }

  void handleReceiverError(WebSocketException e) {
    eventHandler.onError(e);
    if (state == State.CONNECTED) {
      close();
    }
    closeSocket();
  }

  /**
   * Close down the socket. Will trigger the onClose handler if the socket has not been previously
   * closed.
   */
  public synchronized void close() {
    switch (state) {
      case NONE:
        state = State.DISCONNECTED;
        return;
      case CONNECTING:
        // don't wait for an established connection, just close the tcp socket
        closeSocket();
        return;
      case CONNECTED:
        // This method also shuts down the writer
        // the socket will be closed once the ack for the close was received
        sendCloseHandshake();
        return;
      case DISCONNECTING:
        return; // no-op;
      case DISCONNECTED:
        return; // No-op
    }
  }

  void onCloseOpReceived() {
    closeSocket();
  }

  private synchronized void closeSocket() {
    if (state == State.DISCONNECTED) {
      return;
    }
    receiver.stopit();
    writer.stopIt();
    if (socket != null) {
      try {
        socket.close();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    state = State.DISCONNECTED;

    eventHandler.onClose();
  }

  private void sendCloseHandshake() {
    try {
      state = State.DISCONNECTING;
      // Set the stop flag then queue up a message. This ensures that the writer thread
      // will wake up, and since we set the stop flag, it will exit its run loop.
      writer.stopIt();
      writer.send(OPCODE_CLOSE, true, new byte[0]);
    } catch (IOException e) {
      eventHandler.onError(new WebSocketException("Failed to send close frame", e));
    }
  }

  private Socket createSocket() {
    String scheme = url.getScheme();
    String host = url.getHost();
    int port = url.getPort();

    Socket socket;

    if (scheme != null && scheme.equals("ws")) {
      if (port == -1) {
        port = 80;
      }
      try {
        socket = new Socket(host, port);
      } catch (UnknownHostException uhe) {
        throw new WebSocketException("unknown host: " + host, uhe);
      } catch (IOException ioe) {
        throw new WebSocketException("error while creating socket to " + url, ioe);
      }
    } else if (scheme != null && scheme.equals("wss")) {
      if (port == -1) {
        port = 443;
      }
      SSLSessionCache sessionCache = null;
      try {
        if (sslCacheDirectory != null) {
          sessionCache = new SSLSessionCache(new File(sslCacheDirectory));
        }
      } catch (IOException e) {
        logger.debug("Failed to initialize SSL session cache", e);
      }
      try {
        @SuppressWarnings("deprecation")
        SocketFactory factory =
            android.net.SSLCertificateSocketFactory.getDefault(
                SSL_HANDSHAKE_TIMEOUT_MS, sessionCache);
        SSLSocket sslSocket = (SSLSocket) factory.createSocket(host, port);
        // TODO: the default hostname verifier on the JVM always returns false.
        // For the JVM we will need a different solution.
        HostnameVerifier hv = HttpsURLConnection.getDefaultHostnameVerifier();
        SSLSession sslSession = sslSocket.getSession();

        if (!hv.verify(host, sslSession)) {
          throw new WebSocketException("Error while verifying secure socket to " + url);
        }

        socket = sslSocket;
      } catch (UnknownHostException uhe) {
        throw new WebSocketException("unknown host: " + host, uhe);
      } catch (IOException ioe) {
        throw new WebSocketException("error while creating secure socket to " + url, ioe);
      }
    } else {
      throw new WebSocketException("unsupported protocol: " + scheme);
    }

    return socket;
  }

  /**
   * Blocks until both threads exit. The actual close must be triggered separately. This is just a
   * convenience method to make sure everything shuts down, if desired.
   *
   * @throws InterruptedException
   */
  public void blockClose() throws InterruptedException {
    // If the thread is new, it will never run, since we closed the connection before we actually
    // connected
    if (writer.getInnerThread().getState() != Thread.State.NEW) {
      writer.getInnerThread().join();
    }
    getInnerThread().join();
  }

  private void runReader() {
    try {
      Socket socket = createSocket();
      synchronized (this) {
        WebSocket.this.socket = socket;
        if (WebSocket.this.state == WebSocket.State.DISCONNECTED) {
          // The connection has been closed while creating the socket, close it immediately and
          // return
          try {
            WebSocket.this.socket.close();
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
          WebSocket.this.socket = null;
          return;
        }
      }

      DataInputStream input = new DataInputStream(socket.getInputStream());
      OutputStream output = socket.getOutputStream();

      output.write(handshake.getHandshake());

      boolean handshakeComplete = false;
      int len = 1000;
      byte[] buffer = new byte[len];
      int pos = 0;
      ArrayList<String> handshakeLines = new ArrayList<String>();

      while (!handshakeComplete) {
        int b = input.read();
        if (b == -1) {
          throw new WebSocketException("Connection closed before handshake was complete");
        }
        buffer[pos] = (byte) b;
        pos += 1;

        if (buffer[pos - 1] == 0x0A && buffer[pos - 2] == 0x0D) {
          String line = new String(buffer, UTF8);
          if (line.trim().equals("")) {
            handshakeComplete = true;
          } else {
            handshakeLines.add(line.trim());
          }

          buffer = new byte[len];
          pos = 0;
        } else if (pos == 1000) {
          // This really shouldn't happen, handshake lines are short, but just to be safe...
          String line = new String(buffer, UTF8);
          throw new WebSocketException("Unexpected long line in handshake: " + line);
        }
      }

      handshake.verifyServerStatusLine(handshakeLines.get(0));
      handshakeLines.remove(0);

      HashMap<String, String> lowercaseHeaders = new HashMap<String, String>();
      for (String line : handshakeLines) {
        String[] keyValue = line.split(": ", 2);
        lowercaseHeaders.put(
            keyValue[0].toLowerCase(Locale.US), keyValue[1].toLowerCase(Locale.US));
      }
      handshake.verifyServerHandshakeHeaders(lowercaseHeaders);

      writer.setOutput(output);
      receiver.setInput(input);
      state = WebSocket.State.CONNECTED;
      writer.getInnerThread().start();
      eventHandler.onOpen();
      receiver.run();
    } catch (WebSocketException wse) {
      eventHandler.onError(wse);
    } catch (Throwable t) {
      eventHandler.onError(new WebSocketException("error while connecting: " + t.getMessage(), t));
    } finally {
      close();
    }
  }

  Thread getInnerThread() {
    return innerThread;
  }
}
