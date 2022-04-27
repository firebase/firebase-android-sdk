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

package com.google.firebase.database.core.view;

import static com.google.firebase.database.core.utilities.Utilities.hardAssert;

import com.google.firebase.database.core.EventRegistration;
import com.google.firebase.database.snapshot.ChildKey;
import com.google.firebase.database.snapshot.Index;
import com.google.firebase.database.snapshot.IndexedNode;
import com.google.firebase.database.snapshot.NamedNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class EventGenerator {

  private final QuerySpec query;
  private final Index index;

  public EventGenerator(QuerySpec query) {
    this.query = query;
    this.index = query.getIndex();
  }

  private void generateEventsForType(
      List<DataEvent> events,
      Event.EventType type,
      List<Change> changes,
      List<EventRegistration> eventRegistrations,
      IndexedNode eventCache) {
    List<Change> filteredChanges = new ArrayList<Change>();
    for (Change change : changes) {
      if (change.getEventType().equals(type)) {
        filteredChanges.add(change);
      }
    }
    Collections.sort(filteredChanges, changeComparator());
    for (Change change : filteredChanges) {
      for (EventRegistration registration : eventRegistrations) {
        if (registration.respondsTo(type)) {
          events.add(generateEvent(change, registration, eventCache));
        }
      }
    }
  }

  private DataEvent generateEvent(
      Change change, EventRegistration registration, IndexedNode eventCache) {
    Change newChange;
    if (change.getEventType().equals(Event.EventType.VALUE)
        || change.getEventType().equals(Event.EventType.CHILD_REMOVED)) {
      newChange = change;
    } else {
      ChildKey prevChildKey =
          eventCache.getPredecessorChildName(
              change.getChildKey(), change.getIndexedNode().getNode(), this.index);
      newChange = change.changeWithPrevName(prevChildKey);
    }
    return registration.createEvent(newChange, this.query);
  }

  public List<DataEvent> generateEventsForChanges(
      List<Change> changes, IndexedNode eventCache, List<EventRegistration> eventRegistrations) {
    List<DataEvent> events = new ArrayList<DataEvent>();

    List<Change> moves = new ArrayList<Change>();
    for (Change change : changes) {
      if (change.getEventType().equals(Event.EventType.CHILD_CHANGED)
          && index.indexedValueChanged(
              change.getOldIndexedNode().getNode(), change.getIndexedNode().getNode())) {
        moves.add(Change.childMovedChange(change.getChildKey(), change.getIndexedNode()));
      }
    }

    generateEventsForType(
        events, Event.EventType.CHILD_REMOVED, changes, eventRegistrations, eventCache);
    generateEventsForType(
        events, Event.EventType.CHILD_ADDED, changes, eventRegistrations, eventCache);
    generateEventsForType(
        events, Event.EventType.CHILD_MOVED, moves, eventRegistrations, eventCache);
    generateEventsForType(
        events, Event.EventType.CHILD_CHANGED, changes, eventRegistrations, eventCache);
    generateEventsForType(events, Event.EventType.VALUE, changes, eventRegistrations, eventCache);

    return events;
  }

  private Comparator<Change> changeComparator() {
    return new Comparator<Change>() {
      @Override
      public int compare(Change a, Change b) {
        // should only be comparing child_* events
        hardAssert(a.getChildKey() != null && b.getChildKey() != null);
        NamedNode namedNodeA = new NamedNode(a.getChildKey(), a.getIndexedNode().getNode());
        NamedNode namedNodeB = new NamedNode(b.getChildKey(), b.getIndexedNode().getNode());
        return index.compare(namedNodeA, namedNodeB);
      }
    };
  }
}
