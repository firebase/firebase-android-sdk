// Copyright 2026 Google LLC
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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.firebase.database.MapBuilder;
import com.google.firebase.database.logging.Logger;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Tests how a connection handles a reset control message, which tells it to use another host. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class ConnectionResetTest {

  /** Hosts that the server or the emulator can send. */
  private static final List<String> VALID_HOSTS =
      Arrays.asList(
          "s-usc1b-nss-2073.firebaseio.com",
          "x.europe-west1.firebasedatabase.app",
          "localhost:9000",
          "10.0.2.2:9000",
          "[::1]:9000");

  /** Hosts that can't be used in a connection URL. */
  private static final List<String> INVALID_HOSTS =
      Arrays.asList("bad host", "a|b.firebaseio.com", "a^b.firebaseio.com", "");

  private final Logger logger = mock(Logger.class);
  private MockedConstruction<WebsocketConnection> websockets;
  private ConnectionContext context;

  @Before
  public void setUp() {
    // Log everything, so that the debug logging code paths run too.
    when(logger.getLogLevel()).thenReturn(Logger.Level.DEBUG);
    // Replace the websocket with a mock, so that the tests can check that it is closed. The
    // connection is never opened, and server messages are delivered by calling onMessage directly.
    websockets = mockConstruction(WebsocketConnection.class);
    context =
        new ConnectionContext(
            logger,
            mock(ConnectionTokenProvider.class),
            mock(ConnectionTokenProvider.class),
            mock(ScheduledExecutorService.class),
            /* persistenceEnabled= */ false,
            "test-sdk-version",
            "test-user-agent",
            "test-app-id",
            /* sslCacheDirectory= */ null);
  }

  @After
  public void tearDown() {
    websockets.close();
  }

  @Test
  public void resetWithValidHost_cachesHostAndReconnectsImmediately() {
    for (String host : VALID_HOSTS) {
      Connection.Delegate delegate = sendResetToNewConnection(host);

      verify(delegate).onCacheHost(host);
      verify(delegate).onDisconnect(Connection.DisconnectReason.SERVER_RESET);
      verify(lastWebsocket()).close();
    }
  }

  @Test
  public void resetWithoutHost_clearsCachedHostAndReconnectsImmediately() {
    Connection.Delegate delegate = sendResetToNewConnection(/* host= */ null);

    verify(delegate).onCacheHost(null);
    verify(delegate).onDisconnect(Connection.DisconnectReason.SERVER_RESET);
    verify(lastWebsocket()).close();
  }

  @Test
  public void resetWithHostThatIsNotValidInUrl_keepsCachedHostAndReconnectsWithBackoff() {
    for (String host : INVALID_HOSTS) {
      Connection.Delegate delegate = sendResetToNewConnection(host);

      // The persistent connection keeps the host it had. OTHER, unlike SERVER_RESET, makes it
      // reconnect with its usual backoff, so a server that keeps sending this reset can't cause a
      // tight reconnect loop.
      verify(delegate, never()).onCacheHost(any());
      verify(delegate).onDisconnect(Connection.DisconnectReason.OTHER);
      verify(lastWebsocket()).close();
    }
  }

  @Test
  public void resetWithHostThatIsNotValidInUrl_logsWarningWithoutHost() {
    sendResetToNewConnection("bad host");

    ArgumentCaptor<String> warnings = ArgumentCaptor.forClass(String.class);
    verify(logger, atLeastOnce())
        .onLogMessage(eq(Logger.Level.WARN), anyString(), warnings.capture(), anyLong());
    for (String warning : warnings.getAllValues()) {
      assertThat(warning).doesNotContain("bad host");
    }
  }

  /** Sends a reset with {@code host} to a new connection, and returns the connection's delegate. */
  private Connection.Delegate sendResetToNewConnection(String host) {
    Connection.Delegate delegate = mock(Connection.Delegate.class);
    Connection connection =
        new Connection(
            context,
            new HostInfo("test.firebaseio.com", "test", /* secure= */ true),
            /* cachedHost= */ null,
            delegate,
            /* optLastSessionId= */ null,
            "test-app-check-token");
    connection.onMessage(resetMessage(host));
    return delegate;
  }

  private WebsocketConnection lastWebsocket() {
    List<WebsocketConnection> constructed = websockets.constructed();
    return constructed.get(constructed.size() - 1);
  }

  private static Map<String, Object> resetMessage(String host) {
    Map<String, Object> reset = new MapBuilder().put("t", "r").put("d", host).build();
    return new MapBuilder().put("t", "c").put("d", reset).build();
  }
}
