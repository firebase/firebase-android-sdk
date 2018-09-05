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

package com.google.firebase.database;

import com.google.firebase.database.core.view.Event;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

class EventHelper {

  private static class Expectation {

    private Event.EventType eventType;
    private String location;

    private Expectation(Event.EventType eventType, String location) {
      this.eventType = eventType;
      this.location = location;
    }

    boolean matches(EventRecord record) {
      return record.getEventType().equals(eventType)
          && record.getSnapshot().getRef().toString().equals(location);
    }

    @Override
    public String toString() {
      return this.eventType + " => " + this.location;
    }
  }

  private static class ValueExpectation<T> extends Expectation {

    private final T expectedValue;

    private ValueExpectation(Event.EventType eventType, String location, T expectedValue) {
      super(eventType, location);
      this.expectedValue = expectedValue;
    }

    @Override
    boolean matches(EventRecord record) {
      return super.matches(record)
          && record.getSnapshot().getValue(expectedValue.getClass()).equals(expectedValue);
    }
  }

  private List<Expectation> lookingFor;
  private Set<DatabaseReference> locations;
  private Set<DatabaseReference> toListen;
  private List<EventRecord> results;
  private Set<DatabaseReference> uninitializedRefs;
  private int initializationEvents = 0;
  private boolean success;
  private Semaphore semaphore;
  private Semaphore initializationSemaphore;
  private boolean waitingForInitialization = false;
  private Map<DatabaseReference, ValueEventListener> valueListeners =
      new HashMap<DatabaseReference, ValueEventListener>();
  private Map<DatabaseReference, ChildEventListener> childListeners =
      new HashMap<DatabaseReference, ChildEventListener>();

  EventHelper() {
    lookingFor = new ArrayList<Expectation>();
    locations = new HashSet<DatabaseReference>();
    toListen = new HashSet<DatabaseReference>();
    results = new ArrayList<EventRecord>();
    semaphore = new Semaphore(1);
    uninitializedRefs = new HashSet<DatabaseReference>();
    initializationSemaphore = new Semaphore(0);
  }

  public EventHelper addValueExpectation(DatabaseReference ref) {
    if (!locations.contains(ref)) {
      toListen.add(ref);
    }
    lookingFor.add(new Expectation(Event.EventType.VALUE, ref.toString()));
    return this;
  }

  public <T> EventHelper addValueExpectation(DatabaseReference ref, T expectedValue) {
    if (!locations.contains(ref)) {
      toListen.add(ref);
    }
    lookingFor.add(new ValueExpectation<T>(Event.EventType.VALUE, ref.toString(), expectedValue));
    return this;
  }

  public EventHelper addChildExpectation(
      DatabaseReference ref, Event.EventType eventType, String childName) throws DatabaseException {
    if (!locations.contains(ref)) {
      toListen.add(ref);
    }
    lookingFor.add(new Expectation(eventType, ref.child(childName).toString()));
    return this;
  }

  public EventHelper startListening() throws InterruptedException {
    return startListening(false);
  }

  public EventHelper startListening(boolean waitForInitialization) throws InterruptedException {
    waitingForInitialization = waitForInitialization;
    semaphore.acquire(1);
    locations.addAll(toListen);
    List<DatabaseReference> locationList =
        Arrays.asList(toListen.toArray(new DatabaseReference[] {}));
    Collections.sort(
        locationList,
        new Comparator<DatabaseReference>() {
          @Override
          public int compare(DatabaseReference o1, DatabaseReference o2) {
            int o1Length = o1.toString().length();
            int o2Length = o2.toString().length();
            if (o1Length < o2Length) {
              return -1;
            } else if (o1Length == o2Length) {
              return 0;
            } else {
              return 1;
            }
          }
        });

    for (int i = 0; i < locationList.size(); ++i) {
      if (waitForInitialization) {
        uninitializedRefs.add(locationList.get(i));
      }
      listen(locationList.get(i));
    }
    toListen.clear();
    if (waitForInitialization) {
      initializationSemaphore.tryAcquire(
          locationList.size(), IntegrationTestValues.getTimeout(), TimeUnit.MILLISECONDS);
      // Cut out the initialization events
      synchronized (this) {
        waitingForInitialization = false;
        results = results.subList(initializationEvents, results.size());
      }
    }
    return this;
  }

  private void listen(final DatabaseReference ref) {
    valueListeners.put(
        ref,
        ref.addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                recordEvent(new EventRecord(snapshot, Event.EventType.VALUE, null));
                if (uninitializedRefs.remove(ref)) {
                  initializationSemaphore.release(1);
                  initializationEvents++;
                }
              }

              @Override
              public void onCancelled(DatabaseError error) {
                // No-op
              }
            }));

    childListeners.put(
        ref,
        ref.addChildEventListener(
            new ChildEventListener() {
              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                recordEvent(
                    new EventRecord(snapshot, Event.EventType.CHILD_ADDED, previousChildName));
                if (uninitializedRefs.contains(ref)) {
                  initializationEvents++;
                }
              }

              @Override
              public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
                if (uninitializedRefs.contains(ref)) {
                  initializationEvents++;
                }
                recordEvent(
                    new EventRecord(snapshot, Event.EventType.CHILD_CHANGED, previousChildName));
              }

              @Override
              public void onChildRemoved(DataSnapshot snapshot) {
                if (uninitializedRefs.contains(ref)) {
                  initializationEvents++;
                }
                recordEvent(new EventRecord(snapshot, Event.EventType.CHILD_REMOVED, null));
              }

              @Override
              public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
                if (uninitializedRefs.contains(ref)) {
                  initializationEvents++;
                }
                recordEvent(
                    new EventRecord(snapshot, Event.EventType.CHILD_MOVED, previousChildName));
              }

              @Override
              public void onCancelled(DatabaseError error) {
                // No-op
              }
            }));
  }

  private void recordEvent(EventRecord record) {
    synchronized (this) {
      results.add(record);
      checkSuccess();
    }
  }

  public void cleanup() {
    for (Map.Entry<DatabaseReference, ValueEventListener> entry : valueListeners.entrySet()) {
      entry.getKey().removeEventListener(entry.getValue());
    }
    for (Map.Entry<DatabaseReference, ChildEventListener> entry : childListeners.entrySet()) {
      entry.getKey().removeEventListener(entry.getValue());
    }
  }

  private void checkSuccess() {
    if (!waitingForInitialization) {
      int index = results.size() - 1;
      if (index >= lookingFor.size()) {
        // we've seen too many events
        System.out.println("we've seen too many events");
        cleanup();
        success = false;
        semaphore.release(1);
      } else if (lookingFor.get(index).matches(results.get(index))) {
        if (index == lookingFor.size() - 1) {
          // we've seen all the events we're looking for
          success = true;
          semaphore.release(1);
        }
      } else {
        success = false;
        cleanup();
        semaphore.release(1);
      }
    }
  }

  public boolean waitForEvents() throws InterruptedException {
    // Try waiting on the semaphore
    if (!semaphore.tryAcquire(1, IntegrationTestValues.getTimeout(), TimeUnit.MILLISECONDS)) {
      return false;
    } else {
      semaphore.release(1);
      return success;
    }
  }
}
