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
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.FirebaseFirestoreException.Code;
import com.google.firebase.firestore.auth.User;
import com.google.firebase.firestore.core.EventManager.ListenOptions;
import com.google.firebase.firestore.local.LocalSerializer;
import com.google.firebase.firestore.local.LocalStore;
import com.google.firebase.firestore.local.LruGarbageCollector;
import com.google.firebase.firestore.local.MemoryPersistence;
import com.google.firebase.firestore.local.Persistence;
import com.google.firebase.firestore.local.QueryEngine;
import com.google.firebase.firestore.local.TargetData;
import com.google.firebase.firestore.model.DatabaseId;
import com.google.firebase.firestore.remote.RemoteSerializer;
import com.google.firebase.firestore.remote.RemoteStore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
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
    verify(syncSpy, times(1))
        .listen(
            query,
            /** shouldListenToRemote= */
            true);
    verify(syncSpy, times(1))
        .stopListening(
            query,
            /** shouldUnlistenToRemote= */
            true);
  }

  @Test
  public void testUnlistensOnUnknownListeners() {
    Query query = Query.atPath(path("foo/bar"));

    SyncEngine syncSpy = mock(SyncEngine.class);

    EventManager manager = new EventManager(syncSpy);
    manager.removeQueryListener(queryListener(query));
    verify(syncSpy, never()).stopListening(eq(query), anyBoolean());
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

    verify(syncSpy, times(1)).listen(eq(query1), anyBoolean());
    verify(syncSpy, times(1)).listen(eq(query2), anyBoolean());

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

  @Test
  public void xxx() {
    Query query = Query.atPath(path("foo/bar"));

    EventListener<ViewSnapshot> eventListener1 = mock(EventListener.class);
    EventListener<ViewSnapshot> eventListener2 = mock(EventListener.class);

    QueryListener listener1 = new QueryListener(query, new ListenOptions(), eventListener1);
    QueryListener listener2 = new QueryListener(query, new ListenOptions(), eventListener2);

    RemoteStore remoteStore = mockRemoteStore();
    LocalStore localStore = createLruGcMemoryLocalStore();
    SyncEngine syncEngine = spy(new SyncEngine(localStore, remoteStore, User.UNAUTHENTICATED, 100));
    EventManager eventManager = new EventManager(syncEngine);

    eventManager.addQueryListener(listener1);
    eventManager.addQueryListener(listener2);

    syncEngine.handleClearCache();

    verify(syncEngine, times(1))
            .listen(
                    query,
                    /** shouldListenToRemote= */
                    true);

    ArgumentMatcher<FirebaseFirestoreException> abortedExceptionMatcher = e -> e.getCode() == Code.ABORTED;
    verify(eventListener1, times(1))
            .onEvent(isNull(), argThat(abortedExceptionMatcher));

    verify(eventListener2, times(1))
            .onEvent(isNull(), argThat(abortedExceptionMatcher));

    Mockito.verify(remoteStore, times(1)).listen(any(TargetData.class));
    Mockito.verify(remoteStore, atLeastOnce()).canUseNetwork();
    Mockito.verify(remoteStore, times(1)).disableNetwork();
    Mockito.verify(remoteStore, times(1)).enableNetwork();
    Mockito.verifyNoMoreInteractions(remoteStore);

    assertTrue(syncEngine.isEmpty());
    assertTrue(eventManager.isEmpty());
    assertTrue(localStore.isEmpty());
  }

  @NonNull
  private static RemoteStore mockRemoteStore() {
    AtomicBoolean online = new AtomicBoolean(true);
    RemoteStore remoteStore = mock(RemoteStore.class);
    when(remoteStore.canUseNetwork()).thenAnswer(invocation -> online.get());
    doAnswer((Answer<Void>) invocation -> {
      online.set(true);
      return null;
    }).when(remoteStore).enableNetwork();
    doAnswer((Answer<Void>) invocation -> {
      online.set(false);
      return null;
    }).when(remoteStore).disableNetwork();
    return remoteStore;
  }

  @NonNull
  private static LocalStore createLruGcMemoryLocalStore() {
    DatabaseId databaseId = DatabaseId.forProject("projectId");
    LocalSerializer serializer = new LocalSerializer(new RemoteSerializer(databaseId));
    Persistence persistence =  MemoryPersistence.createLruGcMemoryPersistence(
            LruGarbageCollector.Params.Default(), serializer);
    persistence.start();
    return new LocalStore(persistence, new QueryEngine(), User.UNAUTHENTICATED);
  }
}
