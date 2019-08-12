// Copyright 2019 Google LLC
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

package com.google.firebase.components;

import androidx.annotation.GuardedBy;
import com.google.firebase.events.Event;
import com.google.firebase.events.EventHandler;
import com.google.firebase.events.Publisher;
import com.google.firebase.events.Subscriber;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Default implementation of {@link Subscriber} and {@link Publisher}.
 *
 * <p>The EventBus is constructed in the 'disabled' state: events are queued for delivery in an
 * unbounded queue and delivered when the queue is enabled with {@link
 * #enablePublishingAndFlushPending()}. This state is required for the component runtime to ensure
 * that all components have had a chance to subscribe before any publishing begins.
 */
class EventBus implements Subscriber, Publisher {

  @GuardedBy("this")
  private final Map<Class<?>, ConcurrentHashMap<EventHandler<Object>, Executor>> handlerMap =
      new HashMap<>();

  /** Event queue used when the event bus is in the DISABLED state. */
  @GuardedBy("this")
  private Queue<Event<?>> pendingEvents;

  private final Executor defaultExecutor;

  EventBus(Executor defaultExecutor) {
    pendingEvents = new ArrayDeque<Event<?>>();
    this.defaultExecutor = defaultExecutor;
  }

  @Override
  public void publish(Event<?> event) {
    Preconditions.checkNotNull(event);

    synchronized (this) {
      if (pendingEvents != null) {
        pendingEvents.add(event);
        return;
      }
    }

    for (Map.Entry<EventHandler<Object>, Executor> handlerData : getHandlers(event)) {
      // the casted call is safe since type-safety is guaranteed by the signature of #subscribe().
      @SuppressWarnings("unchecked")
      Event<Object> casted = (Event<Object>) event;
      handlerData.getValue().execute(() -> handlerData.getKey().handle(casted));
    }
  }

  private synchronized Set<Map.Entry<EventHandler<Object>, Executor>> getHandlers(Event<?> event) {
    Map<EventHandler<Object>, Executor> handlers = handlerMap.get(event.getType());
    return handlers == null ? Collections.emptySet() : handlers.entrySet();
  }

  @Override
  public synchronized <T> void subscribe(
      Class<T> type, Executor executor, EventHandler<? super T> handler) {
    Preconditions.checkNotNull(type);
    Preconditions.checkNotNull(handler);
    Preconditions.checkNotNull(executor);
    if (!handlerMap.containsKey(type)) {
      handlerMap.put(type, new ConcurrentHashMap<>());
    }

    @SuppressWarnings("unchecked")
    EventHandler<Object> casted = (EventHandler<Object>) handler;
    handlerMap.get(type).put(casted, executor);
  }

  @Override
  public <T> void subscribe(Class<T> type, EventHandler<? super T> handler) {
    subscribe(type, defaultExecutor, handler);
  }

  @Override
  public synchronized <T> void unsubscribe(Class<T> type, EventHandler<? super T> handler) {
    Preconditions.checkNotNull(type);
    Preconditions.checkNotNull(handler);

    if (!handlerMap.containsKey(type)) {
      return;
    }

    ConcurrentHashMap<EventHandler<Object>, Executor> handlers = handlerMap.get(type);

    @SuppressWarnings("unchecked")
    EventHandler<Object> casted = (EventHandler<Object>) handler;
    handlers.remove(casted);

    if (handlers.isEmpty()) {
      handlerMap.remove(type);
    }
  }

  void enablePublishingAndFlushPending() {
    Queue<Event<?>> pending = null;
    synchronized (this) {
      if (pendingEvents != null) {
        pending = pendingEvents;
        pendingEvents = null;
      }
    }
    if (pending != null) {
      for (Event<?> event : pending) {
        publish(event);
      }
    }
  }
}
