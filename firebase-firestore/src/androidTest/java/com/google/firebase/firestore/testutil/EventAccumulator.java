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

import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitFor;
import static com.google.firebase.firestore.util.Assert.hardAssert;

import android.util.Log;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.QuerySnapshot;
import java.util.ArrayList;
import java.util.List;

/** Event accumulator for integration test */
public class EventAccumulator<T> {
  private TaskCompletionSource<Void> completion;
  private final List<T> events;
  private int maxEvents;

  public EventAccumulator() {
    events = new ArrayList<>();
    maxEvents = 0;
  }

  public EventListener<T> listener() {
    return (value, error) -> {
      synchronized (EventAccumulator.this) {
        hardAssert(error == null, "Unexpected error: %s", error);
        Log.i("EventAccumulator", "Received new event: " + value);
        events.add(value);
        checkFulfilled();
      }
    };
  }

  public List<T> await(int numEvents) {
    synchronized (this) {
      hardAssert(completion == null, "calling await while another await is running");
      completion = new TaskCompletionSource<>();
      maxEvents = maxEvents + numEvents;
      checkFulfilled();
    }

    waitFor(completion.getTask());
    completion = null;
    return events.subList(maxEvents - numEvents, maxEvents);
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

  private boolean hasPendingWrites(T event) {
    if (event instanceof DocumentSnapshot) {
      return ((DocumentSnapshot) event).getMetadata().hasPendingWrites();
    } else {
      hardAssert(
          event instanceof QuerySnapshot, "hasPendingWrites() called on unknown event: %s", event);
      return ((QuerySnapshot) event).getMetadata().hasPendingWrites();
    }
  }

  private void checkFulfilled() {
    if (completion != null && events.size() >= maxEvents) {
      completion.setResult(null);
    }
  }
}
