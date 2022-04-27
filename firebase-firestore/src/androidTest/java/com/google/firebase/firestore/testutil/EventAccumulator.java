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

package com.google.firebase.firestore.testutil;

import static com.google.firebase.firestore.util.Assert.fail;
import static com.google.firebase.firestore.util.Assert.hardAssert;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.util.Logger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/** Event accumulator for integration test */
public class EventAccumulator<T> {
  private static final int MAX_EVENTS = 10;

  private final BlockingQueue<T> events;
  private boolean rejectAdditionalEvents;

  public EventAccumulator() {
    events = new ArrayBlockingQueue<T>(MAX_EVENTS);
  }

  public EventListener<T> listener() {
    return (value, error) -> {
      hardAssert(error == null, "Unexpected error: %s", error);
      hardAssert(
          !rejectAdditionalEvents, "Received event after `assertNoAdditionalEvents()` was called");
      Logger.debug("EventAccumulator", "Received new event: " + value);
      events.offer(value);
    };
  }

  public List<T> await(int numEvents) {
    try {
      List<T> result = new ArrayList<>(numEvents);
      for (int i = 0; i < numEvents; ++i) {
        result.add(events.take());
      }
      return result;
    } catch (InterruptedException e) {
      throw fail("Failed to receive " + numEvents + " events");
    }
  }

  // Await 1 event.
  public T await() {
    return await(1).get(0);
  }

  /** Waits for a snapshot with pending writes. */
  public T awaitLocalEvent() {
    T event;
    do {
      event = await();
    } while (!hasPendingWrites(event));
    return event;
  }

  /** Waits for a snapshot that has no pending writes. */
  public T awaitRemoteEvent() {
    T event;
    do {
      event = await();
    } while (hasPendingWrites(event));
    return event;
  }

  public void assertNoAdditionalEvents() {
    rejectAdditionalEvents = true;
    hardAssert(events.isEmpty(), "There are %d unprocessed events.", events.size());
  }

  private boolean hasPendingWrites(T event) {
    if (event instanceof DocumentSnapshot) {
      return ((DocumentSnapshot) event).getMetadata().hasPendingWrites();
    } else {
      hardAssert(
          event instanceof QuerySnapshot, "hasPendingWrites() called on unknown event: %s", event);
      return ((QuerySnapshot) event).getMetadata().hasPendingWrites();
    }
  }
}
