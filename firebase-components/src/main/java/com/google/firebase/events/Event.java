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

import com.google.firebase.components.Preconditions;

/**
 * Generic event object identified by its type and payload of that type.
 *
 * @param <T> event type
 * @hide
 */
public class Event<T> {
  private final Class<T> type;

  private final T payload;

  public Event(Class<T> type, T payload) {
    this.type = Preconditions.checkNotNull(type);
    this.payload = Preconditions.checkNotNull(payload);
  }

  /** The type of the event's payload. */
  public Class<T> getType() {
    return type;
  }

  /** The payload of the event. */
  public T getPayload() {
    return payload;
  }

  @Override
  public String toString() {
    return String.format("Event{type: %s, payload: %s}", type, payload);
  }
}
