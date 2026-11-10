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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;

import com.google.firebase.database.logging.Logger;
import com.google.firebase.database.tubesock.WebSocket;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedConstruction;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Tests the URL that a new websocket connection uses. The persistent connection creates a new
 * websocket connection every time it reconnects, with the host and the session ID that the server
 * sent on an earlier connection.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class WebsocketConnectionTest {

  private static final HostInfo HOST_INFO =
      new HostInfo("test.firebaseio.com", "test", /* secure= */ true);
  private static final String SERVER_HOST = "s-usc1b-nss-2073.firebaseio.com";

  private final List<URI> urls = new ArrayList<>();
  private MockedConstruction<WebSocket> webSockets;
  private ConnectionContext context;

  @Before
  public void setUp() {
    // Record the URL of each websocket instead of creating a real one. The tests never call
    // open(), so nothing connects to the network.
    webSockets =
        mockConstruction(
            WebSocket.class,
            (webSocket, construction) -> urls.add((URI) construction.arguments().get(1)));
    context =
        new ConnectionContext(
            mock(Logger.class),
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
    webSockets.close();
  }

  @Test
  public void withoutHostOrSessionIdFromServer_usesConfiguredHost() {
    assertThat(createConnectionUrl(/* cachedHost= */ null, /* lastSessionId= */ null))
        .isEqualTo("wss://test.firebaseio.com/.ws?ns=test&v=5");
  }

  @Test
  public void withValidHostAndSessionIdFromServer_usesBoth() {
    assertThat(createConnectionUrl(SERVER_HOST, "session-id"))
        .isEqualTo("wss://s-usc1b-nss-2073.firebaseio.com/.ws?ns=test&v=5&ls=session-id");
  }

  @Test
  public void withHostFromServerThatIsNotValidInUrl_usesConfiguredHost() {
    assertThat(createConnectionUrl("bad host", "session-id"))
        .isEqualTo("wss://test.firebaseio.com/.ws?ns=test&v=5&ls=session-id");
  }

  @Test
  public void withSessionIdFromServerThatIsNotValidInUrl_omitsSessionId() {
    assertThat(createConnectionUrl(SERVER_HOST, "a b"))
        .isEqualTo("wss://s-usc1b-nss-2073.firebaseio.com/.ws?ns=test&v=5");
  }

  @Test
  public void withHostAndSessionIdFromServerThatAreNotValidInUrl_usesConfiguredHost() {
    assertThat(createConnectionUrl("a|b.firebaseio.com", "a b"))
        .isEqualTo("wss://test.firebaseio.com/.ws?ns=test&v=5");
  }

  /**
   * Creates a websocket connection the way a reconnect does, and returns the URL that it would
   * connect to.
   */
  private String createConnectionUrl(String cachedHost, String lastSessionId) {
    new WebsocketConnection(
        context,
        HOST_INFO,
        cachedHost,
        "test-app-check-token",
        mock(WebsocketConnection.Delegate.class),
        lastSessionId);
    assertThat(urls).hasSize(1);
    return urls.get(0).toString();
  }
}
