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

package com.google.firebase.firestore.core;

import static com.google.firebase.firestore.testutil.TestUtil.path;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.firebase.firestore.core.EventManager.ListenOptions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Tests EventManager and QueryEventCallbackImpl */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class EventManagerTest {
  private static QueryListener queryListener(Query query) {
    return new QueryListener(query, new ListenOptions(), (value, error) -> {});
  }

  @Test
  public void testMultipleListensPerQuery() {
    Query query = Query.atPath(path("foo/bar"));

    QueryListener listener1 = queryListener(query);
    QueryListener listener2 = queryListener(query);

    SyncEngine syncSpy = mock(SyncEngine.class);

    EventManager manager = new EventManager(syncSpy);
    manager.addQueryListener(listener1);
    manager.addQueryListener(listener2);

    manager.removeQueryListener(listener1);
    manager.removeQueryListener(listener2);
    verify(syncSpy, times(1)).listen(query);
    verify(syncSpy, times(1)).stopListening(query);
  }

  @Test
  public void testUnlistensOnUnknownListeners() {
    Query query = Query.atPath(path("foo/bar"));

    SyncEngine syncSpy = mock(SyncEngine.class);

    EventManager manager = new EventManager(syncSpy);
    manager.removeQueryListener(queryListener(query));
    verify(syncSpy, never()).stopListening(query);
  }

  @Test
  public void testListenCalledInOrder() {
    Query query1 = Query.atPath(path("foo/bar"));
    Query query2 = Query.atPath(path("bar/baz"));

    SyncEngine syncSpy = mock(SyncEngine.class);
    EventManager eventManager = new EventManager(syncSpy);

    QueryListener spy1 = mock(QueryListener.class);
    when(spy1.getQuery()).thenReturn(query1);
    QueryListener spy2 = mock(QueryListener.class);
    when(spy2.getQuery()).thenReturn(query2);
    QueryListener spy3 = mock(QueryListener.class);
    when(spy3.getQuery()).thenReturn(query1);
    eventManager.addQueryListener(spy1);
    eventManager.addQueryListener(spy2);
    eventManager.addQueryListener(spy3);

    verify(syncSpy, times(1)).listen(query1);
    verify(syncSpy, times(1)).listen(query2);

    ViewSnapshot snap1 = mock(ViewSnapshot.class);
    when(snap1.getQuery()).thenReturn(query1);

    ViewSnapshot snap2 = mock(ViewSnapshot.class);
    when(snap2.getQuery()).thenReturn(query2);

    eventManager.onViewSnapshots(Arrays.asList(snap1, snap2));
    InOrder inOrder = inOrder(spy1, spy3, spy2);
    inOrder.verify(spy1).onViewSnapshot(snap1);
    inOrder.verify(spy3).onViewSnapshot(snap1);
    inOrder.verify(spy2).onViewSnapshot(snap2);
  }

  @Test
  public void testWillForwardOnOnlineStateChangedCalls() {
    Query query1 = Query.atPath(path("foo/bar"));

    SyncEngine syncSpy = mock(SyncEngine.class);
    EventManager eventManager = new EventManager(syncSpy);

    List<Object> events = new ArrayList<>();

    QueryListener spy = mock(QueryListener.class);
    when(spy.getQuery()).thenReturn(query1);
    doAnswer(
            invocation -> {
              events.add(invocation.getArguments()[0]);
              return false;
            })
        .when(spy)
        .onOnlineStateChanged(any());

    eventManager.addQueryListener(spy);
    assertEquals(Arrays.asList(OnlineState.UNKNOWN), events);
    eventManager.handleOnlineStateChange(OnlineState.ONLINE);
    assertEquals(Arrays.asList(OnlineState.UNKNOWN, OnlineState.ONLINE), events);
  }
}
