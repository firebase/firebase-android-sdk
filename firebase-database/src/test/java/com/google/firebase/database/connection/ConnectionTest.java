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
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.firebase.database.MapBuilder;
import com.google.firebase.database.logging.Logger;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class ConnectionTest {

  private static final long SERVER_TIMESTAMP = 1700000000000L;

  private final Logger logger = mock(Logger.class);
  private final Connection.Delegate delegate = mock(Connection.Delegate.class);
  private Connection connection;

  @Before
  public void setUp() {
    // Log everything, so that the debug logging code paths run too.
    when(logger.getLogLevel()).thenReturn(Logger.Level.DEBUG);
    ConnectionContext context =
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
    // The connection is never opened, so it does not connect to the network. Server messages are
    // delivered by calling onMessage directly.
    connection =
        new Connection(
            context,
            new HostInfo("test.firebaseio.com", "test", /* secure= */ true),
            /* cachedHost= */ null,
            delegate,
            /* optLastSessionId= */ null,
            /* appCheckToken= */ "");
  }

  @Test
  public void handshakeWithTimestamp_reportsServerTime() {
    connection.onMessage(
        handshake(
            new MapBuilder()
                .put("ts", SERVER_TIMESTAMP)
                .put("h", "host")
                .put("s", "session")
                .build()));

    verify(delegate).onCacheHost("host");
    verify(delegate).onReady(SERVER_TIMESTAMP, "session");
    verify(delegate, never()).onDisconnect(any());
  }

  @Test
  public void handshakeWithIntegerTimestamp_reportsServerTime() {
    connection.onMessage(handshake(new MapBuilder().put("ts", 42).put("s", "session").build()));

    verify(delegate).onReady(42L, "session");
    verify(delegate, never()).onDisconnect(any());
  }

  @Test
  public void handshakeWithoutTimestamp_usesLocalTime() {
    assertHandshakeUsesLocalTime(new MapBuilder().put("h", "host").put("s", "session").build());
  }

  @Test
  public void handshakeWithNullTimestamp_usesLocalTime() {
    assertHandshakeUsesLocalTime(
        new MapBuilder().put("ts", null).put("h", "host").put("s", "session").build());
  }

  @Test
  public void handshakeWithNonNumericTimestamp_usesLocalTime() {
    assertHandshakeUsesLocalTime(
        new MapBuilder().put("ts", "not a number").put("h", "host").put("s", "session").build());
  }

  @Test
  public void handshakeWithoutData_isAccepted() {
    connection.onMessage(controlMessage(new MapBuilder().put("t", "h").build()));

    verify(delegate).onCacheHost(null);
    verify(delegate).onReady(anyLong(), isNull());
    verify(delegate, never()).onDisconnect(any());
  }

  @Test
  public void handshakeWithNonStringHostAndSessionId_isAccepted() {
    connection.onMessage(
        handshake(
            new MapBuilder().put("ts", SERVER_TIMESTAMP).put("h", 123).put("s", 456).build()));

    verify(delegate).onCacheHost(null);
    verify(delegate).onReady(SERVER_TIMESTAMP, null);
    verify(delegate, never()).onDisconnect(any());
  }

  @Test
  public void shutdownWithoutReason_killsConnection() {
    connection.onMessage(controlMessage(new MapBuilder().put("t", "s").build()));

    verify(delegate).onKill(null);
    verify(delegate).onDisconnect(Connection.DisconnectReason.OTHER);
  }

  @Test
  public void dataMessage_isPassedToDelegate() {
    Map<String, Object> data = new MapBuilder().put("r", 1).build();

    connection.onMessage(dataMessage(data));

    verify(delegate).onDataMessage(data);
    verify(delegate, never()).onDisconnect(any());
  }

  @Test
  public void dataMessageWithoutPayload_closesConnection() {
    connection.onMessage(new MapBuilder().put("t", "d").build());

    verify(delegate).onDisconnect(Connection.DisconnectReason.OTHER);
  }

  @Test
  public void exceptionWhileHandlingDataMessage_closesConnection() {
    doThrow(new NullPointerException()).when(delegate).onDataMessage(any());

    connection.onMessage(dataMessage(new MapBuilder().put("r", 1).build()));

    verify(delegate).onDisconnect(Connection.DisconnectReason.OTHER);
    verifyWarningLogged("closing the connection");
  }

  @Test
  public void exceptionWhileHandlingControlMessage_closesConnection() {
    doThrow(new IllegalStateException()).when(delegate).onReady(anyLong(), any());

    connection.onMessage(
        handshake(new MapBuilder().put("ts", SERVER_TIMESTAMP).put("s", "session").build()));

    verify(delegate).onDisconnect(Connection.DisconnectReason.OTHER);
    verifyWarningLogged("closing the connection");
  }

  @Test
  public void errorWhileHandlingMessage_isNotCaught() {
    // Errors, such as failed internal assertions, must still surface.
    doThrow(new AssertionError("internal error")).when(delegate).onDataMessage(any());
    Map<String, Object> message = dataMessage(new MapBuilder().put("r", 1).build());

    assertThrows(AssertionError.class, () -> connection.onMessage(message));
    verify(delegate, never()).onDisconnect(any());
  }

  private void assertHandshakeUsesLocalTime(Map<String, Object> handshakeData) {
    long before = System.currentTimeMillis();
    connection.onMessage(handshake(handshakeData));
    long after = System.currentTimeMillis();

    ArgumentCaptor<Long> timestamp = ArgumentCaptor.forClass(Long.class);
    verify(delegate).onCacheHost("host");
    verify(delegate).onReady(timestamp.capture(), eq("session"));
    assertThat(timestamp.getValue()).isAtLeast(before);
    assertThat(timestamp.getValue()).isAtMost(after);
    verify(delegate, never()).onDisconnect(any());
    verifyWarningLogged("timestamp");
  }

  private void verifyWarningLogged(String text) {
    verify(logger, atLeastOnce())
        .onLogMessage(eq(Logger.Level.WARN), anyString(), contains(text), anyLong());
  }

  private static Map<String, Object> dataMessage(Map<String, Object> data) {
    return new MapBuilder().put("t", "d").put("d", data).build();
  }

  private static Map<String, Object> controlMessage(Map<String, Object> data) {
    return new MapBuilder().put("t", "c").put("d", data).build();
  }

  private static Map<String, Object> handshake(Map<String, Object> handshakeData) {
    return controlMessage(new MapBuilder().put("t", "h").put("d", handshakeData).build());
  }
}
