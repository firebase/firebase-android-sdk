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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.MapBuilder;
import com.google.firebase.database.logging.Logger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class PersistentConnectionImplTest {

  private static final long SERVER_TIMESTAMP = 1700000000000L;
  private static final List<String> PATH = Arrays.asList("a", "b");
  private static final Map<String, Object> DEFAULT_QUERY = Collections.emptyMap();

  private final Logger logger = mock(Logger.class);
  private final ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
  private final PersistentConnection.Delegate delegate = mock(PersistentConnection.Delegate.class);
  private final RequestResultCallback onComplete = mock(RequestResultCallback.class);
  private MockedConstruction<Connection> connections;
  private PersistentConnectionImpl persistentConnection;
  private Connection realtime;

  @Before
  public void setUp() {
    // Log everything, so that the debug logging code paths run too.
    when(logger.getLogLevel()).thenReturn(Logger.Level.DEBUG);
    // Replaces the realtime connection, which would open a WebSocket, with a mock.
    connections = mockConstruction(Connection.class);
    ConnectionContext context =
        new ConnectionContext(
            logger,
            mock(ConnectionTokenProvider.class),
            mock(ConnectionTokenProvider.class),
            executor,
            /* persistenceEnabled= */ false,
            "1.2.3",
            "test-user-agent",
            "test-app-id",
            /* sslCacheDirectory= */ null);
    persistentConnection =
        new PersistentConnectionImpl(
            context, new HostInfo("test.firebaseio.com", "test", /* secure= */ true), delegate);
  }

  @After
  public void tearDown() {
    connections.close();
  }

  @Test
  public void statsResponseWithoutBody_keepsConnection() {
    connect();
    // The client sends connection stats right after it connects for the first time.
    int statsRequest = lastRequestNumber("s");

    respond(statsRequest, /* body= */ null);

    verify(realtime, never()).close();
    verifyWarningLogged("status");
  }

  @Test
  public void putResponseWithoutBody_failsWrite() {
    connect();
    persistentConnection.put(PATH, "value", onComplete);

    respond(lastRequestNumber("p"), /* body= */ null);

    // A null status would report the write as successful, so the status must be set, and the
    // client must be able to turn it into a DatabaseError.
    ArgumentCaptor<String> status = ArgumentCaptor.forClass(String.class);
    verify(onComplete).onRequestResult(status.capture(), isNull());
    assertThat(status.getValue()).isNotNull();
    assertThat(DatabaseError.fromStatus(status.getValue()).getCode())
        .isEqualTo(DatabaseError.UNKNOWN_ERROR);
  }

  @Test
  public void putResponseWithoutStatus_failsWriteWithServerMessage() {
    connect();
    persistentConnection.put(PATH, "value", onComplete);

    respond(lastRequestNumber("p"), new MapBuilder().put("d", "Something went wrong").build());

    verify(onComplete).onRequestResult(notNull(), eq("Something went wrong"));
  }

  @Test
  public void putResponseOk_completesWrite() {
    connect();
    persistentConnection.put(PATH, "value", onComplete);

    respond(lastRequestNumber("p"), new MapBuilder().put("s", "ok").put("d", "").build());

    verify(onComplete).onRequestResult(null, null);
  }

  @Test
  public void putResponseWithError_failsWrite() {
    connect();
    persistentConnection.put(PATH, "value", onComplete);

    respond(
        lastRequestNumber("p"),
        new MapBuilder().put("s", "permission_denied").put("d", "Permission denied").build());

    verify(onComplete).onRequestResult("permission_denied", "Permission denied");
  }

  @Test
  public void listenResponseWithoutData_completesListen() {
    connect();
    listen();

    respond(lastRequestNumber("q"), new MapBuilder().put("s", "ok").build());

    verify(onComplete).onRequestResult(null, null);
  }

  @Test
  public void listenResponseWithNullWarnings_completesListen() {
    connect();
    listen();

    respond(
        lastRequestNumber("q"),
        new MapBuilder().put("s", "ok").put("d", new MapBuilder().put("w", null).build()).build());

    verify(onComplete).onRequestResult(null, null);
  }

  @Test
  public void listenResponseWithWarnings_completesListenAndLogsWarning() {
    connect();
    listen();

    respond(
        lastRequestNumber("q"),
        new MapBuilder()
            .put("s", "ok")
            .put("d", new MapBuilder().put("w", Collections.singletonList("no_index")).build())
            .build());

    verify(onComplete).onRequestResult(null, null);
    verifyWarningLogged("indexOn");
  }

  @Test
  public void listenResponseWithoutStatus_failsListen() {
    connect();
    listen();

    respond(lastRequestNumber("q"), new MapBuilder().put("d", new MapBuilder().build()).build());

    verify(onComplete).onRequestResult(notNull(), isNull());
  }

  @Test
  public void getResponseWithoutBody_failsGet() {
    connect();
    Task<Object> task = persistentConnection.get(PATH, DEFAULT_QUERY);

    respond(lastRequestNumber("g"), /* body= */ null);

    assertThat(task.isComplete()).isTrue();
    assertThat(task.isSuccessful()).isFalse();
  }

  @Test
  public void getResponseWithoutStatus_failsGet() {
    connect();
    Task<Object> task = persistentConnection.get(PATH, DEFAULT_QUERY);

    respond(
        lastRequestNumber("g"),
        new MapBuilder().put("d", new MapBuilder().put("key", "value").build()).build());

    assertThat(task.isComplete()).isTrue();
    assertThat(task.isSuccessful()).isFalse();
  }

  @Test
  public void getResponseOk_returnsData() {
    connect();
    Task<Object> task = persistentConnection.get(PATH, DEFAULT_QUERY);

    respond(lastRequestNumber("g"), new MapBuilder().put("s", "ok").put("d", "value").build());

    assertThat(task.getResult()).isEqualTo("value");
  }

  @Test
  public void onDisconnectResponseWithoutStatus_failsRequest() {
    connect();
    persistentConnection.onDisconnectPut(PATH, "value", onComplete);

    respond(lastRequestNumber("o"), new MapBuilder().build());

    verify(onComplete).onRequestResult(notNull(), isNull());
  }

  @Test
  public void authResponseWithoutStatus_closesConnection() {
    connect(/* authToken= */ "auth-token", /* appCheckToken= */ null);

    respond(lastRequestNumber("auth"), new MapBuilder().build());

    verify(delegate).onConnectionStatus(false);
    verify(realtime).close();
  }

  @Test
  public void appCheckResponseWithoutStatus_keepsConnection() {
    connect(/* authToken= */ null, /* appCheckToken= */ "app-check-token");

    respond(lastRequestNumber("appcheck"), new MapBuilder().build());

    verify(realtime, never()).close();
  }

  @Test
  public void killWithoutReason_stopsReconnecting() {
    connect();

    persistentConnection.onKill(null);

    assertThat(persistentConnection.isInterrupted("server_kill")).isTrue();
    verify(realtime).close();
  }

  @Test
  public void killForInvalidAppCheckToken_keepsReconnecting() {
    connect();

    persistentConnection.onKill("Invalid appcheck token");

    assertThat(persistentConnection.isInterrupted("server_kill")).isFalse();
  }

  @Test
  public void pushWithoutBody_isIgnored() {
    connect();

    for (String action : Arrays.asList("d", "m", "rm", "c", "ac", "apc", "sd")) {
      persistentConnection.onDataMessage(new MapBuilder().put("a", action).build());
    }

    verifyNoDataUpdates();
    verify(realtime, never()).close();
  }

  @Test
  public void pushWithoutPath_isIgnored() {
    connect();

    for (String action : Arrays.asList("d", "m", "rm", "c")) {
      persistentConnection.onDataMessage(
          new MapBuilder()
              .put("a", action)
              .put("b", new MapBuilder().put("d", "private data").build())
              .build());
    }

    verifyNoDataUpdates();
    verifyWarningLogged("push");
    // Warnings must not include the data from the push.
    verify(logger, never())
        .onLogMessage(eq(Logger.Level.WARN), anyString(), contains("private data"), anyLong());
  }

  @Test
  public void pushWithPath_updatesData() {
    connect();

    persistentConnection.onDataMessage(
        new MapBuilder()
            .put("a", "d")
            .put("b", new MapBuilder().put("p", "a/b").put("d", "value").build())
            .build());

    verify(delegate).onDataUpdate(PATH, "value", /* isMerge= */ false, /* optTag= */ null);
  }

  @Test
  public void getResponseWithErrorStatusAndNoMessage_failsGetWithStatus() {
    connect();
    Task<Object> task = persistentConnection.get(PATH, DEFAULT_QUERY);

    respond(lastRequestNumber("g"), new MapBuilder().put("s", "permission_denied").build());

    assertThat(task.isComplete()).isTrue();
    assertThat(task.getException()).hasMessageThat().contains("permission_denied");
  }

  @Test
  public void mergePushWithDataThatIsNotAMap_isIgnored() {
    connect();

    persistentConnection.onDataMessage(
        new MapBuilder()
            .put("a", "m")
            .put("b", new MapBuilder().put("p", "a/b").put("d", "not a map").build())
            .build());

    verifyNoDataUpdates();
    verify(realtime, never()).close();
  }

  @Test
  public void mergePushWithMap_updatesData() {
    connect();
    Map<String, Object> data = new MapBuilder().put("key", "value").build();

    persistentConnection.onDataMessage(
        new MapBuilder()
            .put("a", "m")
            .put("b", new MapBuilder().put("p", "a/b").put("d", data).build())
            .build());

    verify(delegate).onDataUpdate(PATH, data, /* isMerge= */ true, /* optTag= */ null);
  }

  @Test
  public void rangeMergePushWithDataThatIsNotAList_isIgnored() {
    connect();

    persistentConnection.onDataMessage(
        new MapBuilder()
            .put("a", "rm")
            .put("b", new MapBuilder().put("p", "a/b").put("d", "not a list").build())
            .build());

    verifyNoDataUpdates();
    verify(realtime, never()).close();
  }

  @Test
  public void rangeMergePushWithMalformedRangeEntry_isIgnored() {
    connect();
    List<Object> badEntries =
        Arrays.asList(
            "not a map",
            new MapBuilder().put("s", 123).put("e", "z").build(),
            new MapBuilder().put("s", "a").put("e", 123).build());

    for (Object entry : badEntries) {
      persistentConnection.onDataMessage(
          new MapBuilder()
              .put("a", "rm")
              .put(
                  "b",
                  new MapBuilder()
                      .put("p", "a/b")
                      .put("d", Collections.singletonList(entry))
                      .build())
              .build());
    }

    verifyNoDataUpdates();
    verify(realtime, never()).close();
  }

  @Test
  public void rangeMergePushWithValidRanges_updatesData() {
    connect();
    Map<String, Object> range =
        new MapBuilder()
            .put("s", null)
            .put("e", "k2")
            .put("m", new MapBuilder().put("k1", "v1").build())
            .build();

    persistentConnection.onDataMessage(
        new MapBuilder()
            .put("a", "rm")
            .put(
                "b",
                new MapBuilder().put("p", "a/b").put("d", Collections.singletonList(range)).build())
            .build());

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<RangeMerge>> merges = ArgumentCaptor.forClass(List.class);
    verify(delegate).onRangeMergeUpdate(eq(PATH), merges.capture(), isNull());
    assertThat(merges.getValue()).hasSize(1);
    assertThat(merges.getValue().get(0).getOptExclusiveStart()).isNull();
    assertThat(merges.getValue().get(0).getOptInclusiveEnd()).containsExactly("k2");
  }

  @Test
  public void controlPushesWithNonStringFields_areIgnored() {
    connect();
    clearInvocations(delegate);

    for (String action : Arrays.asList("ac", "apc")) {
      persistentConnection.onDataMessage(
          new MapBuilder()
              .put("a", action)
              .put("b", new MapBuilder().put("s", 123).put("d", "reason").build())
              .build());
      persistentConnection.onDataMessage(
          new MapBuilder()
              .put("a", action)
              .put("b", new MapBuilder().put("s", "expired_token").put("d", 123).build())
              .build());
    }
    persistentConnection.onDataMessage(
        new MapBuilder().put("a", "sd").put("b", new MapBuilder().put("msg", 123).build()).build());

    verify(realtime, never()).close();
    verify(delegate, never()).onConnectionStatus(false);
    verify(logger, never())
        .onLogMessage(eq(Logger.Level.INFO), anyString(), anyString(), anyLong());
  }

  private void connect() {
    connect(/* authToken= */ null, /* appCheckToken= */ null);
  }

  /** Connects to the (mocked) server, the same way a successful handshake does. */
  private void connect(String authToken, String appCheckToken) {
    persistentConnection.initialize();
    // initialize() schedules a connection attempt, after the idle timer that the constructor
    // schedules. The attempt starts to fetch tokens, which the mocked token providers never
    // return, so open the connection with the given tokens directly.
    ArgumentCaptor<Runnable> scheduledTask = ArgumentCaptor.forClass(Runnable.class);
    verify(executor, atLeastOnce())
        .schedule(scheduledTask.capture(), anyLong(), any(TimeUnit.class));
    scheduledTask.getValue().run();
    persistentConnection.openNetworkConnection(authToken, appCheckToken);
    realtime = connections.constructed().get(0);
    persistentConnection.onReady(SERVER_TIMESTAMP, "session");
  }

  private void listen() {
    persistentConnection.listen(
        PATH, DEFAULT_QUERY, mock(ListenHashProvider.class), /* tag= */ null, onComplete);
  }

  /** Returns the number of the last request sent to the server, after checking its action. */
  private int lastRequestNumber(String expectedAction) {
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> request = ArgumentCaptor.forClass(Map.class);
    verify(realtime, atLeastOnce()).sendRequest(request.capture(), anyBoolean());
    assertThat(request.getValue()).containsEntry("a", expectedAction);
    return ((Long) request.getValue().get("r")).intValue();
  }

  /** Delivers a response from the server. The response has no body if {@code body} is null. */
  private void respond(int requestNumber, Map<String, Object> body) {
    MapBuilder message = new MapBuilder().put("r", requestNumber);
    if (body != null) {
      message.put("b", body);
    }
    persistentConnection.onDataMessage(message.build());
  }

  private void verifyNoDataUpdates() {
    verify(delegate, never()).onDataUpdate(any(), any(), anyBoolean(), any());
    verify(delegate, never()).onRangeMergeUpdate(any(), any(), any());
  }

  private void verifyWarningLogged(String text) {
    verify(logger, atLeastOnce())
        .onLogMessage(eq(Logger.Level.WARN), anyString(), contains(text), anyLong());
  }
}
