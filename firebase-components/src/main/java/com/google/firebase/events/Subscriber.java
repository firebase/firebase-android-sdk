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

package com.google.firebase.events;

import java.util.concurrent.Executor;

/** Defines the API for event subscription. */
public interface Subscriber {

  /**
   * Subscribe to events of a given {@code type}.
   *
   * <p>Upon receipt of events, the specified {@link EventHandler} will be executed on the specified
   * executor.
   *
   * <p>Note: subscribing the same (type, handler) pair on different executors will not register
   * multiple times. Instead only the last subscription will be respected.
   */
  <T> void subscribe(Class<T> type, Executor executor, EventHandler<? super T> handler);

  /**
   * Subscribe to events of a given {@code type}.
   *
   * <p>Upon receipt of events, the specified {@link EventHandler} will be executed on the
   * publisher's thread.
   *
   * <p>By subscribing the {@link EventHandler}'s lifetime and its captured scope's is extended
   * until an unsubscribe is called.
   */
  <T> void subscribe(Class<T> type, EventHandler<? super T> handler);

  /** Unsubscribe a handler from events of a given type. */
  <T> void unsubscribe(Class<T> type, EventHandler<? super T> handler);
}
