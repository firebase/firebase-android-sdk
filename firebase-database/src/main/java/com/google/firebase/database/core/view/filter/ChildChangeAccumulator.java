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

package com.google.firebase.database.core.view.filter;

import static com.google.firebase.database.core.utilities.Utilities.hardAssert;

import com.google.firebase.database.core.view.Change;
import com.google.firebase.database.core.view.Event;
import com.google.firebase.database.snapshot.ChildKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChildChangeAccumulator {

  private final Map<ChildKey, Change> changeMap;

  public ChildChangeAccumulator() {
    this.changeMap = new HashMap<ChildKey, Change>();
  }

  public void trackChildChange(Change change) {
    Event.EventType type = change.getEventType();
    ChildKey childKey = change.getChildKey();
    hardAssert(
        type == Event.EventType.CHILD_ADDED
            || type == Event.EventType.CHILD_CHANGED
            || type == Event.EventType.CHILD_REMOVED,
        "Only child changes supported for tracking");
    hardAssert(!change.getChildKey().isPriorityChildName());
    if (changeMap.containsKey(childKey)) {
      Change oldChange = changeMap.get(childKey);
      Event.EventType oldType = oldChange.getEventType();
      if (type == Event.EventType.CHILD_ADDED && oldType == Event.EventType.CHILD_REMOVED) {
        changeMap.put(
            change.getChildKey(),
            Change.childChangedChange(
                childKey, change.getIndexedNode(), oldChange.getIndexedNode()));
      } else if (type == Event.EventType.CHILD_REMOVED && oldType == Event.EventType.CHILD_ADDED) {
        changeMap.remove(childKey);
      } else if (type == Event.EventType.CHILD_REMOVED
          && oldType == Event.EventType.CHILD_CHANGED) {
        changeMap.put(childKey, Change.childRemovedChange(childKey, oldChange.getOldIndexedNode()));
      } else if (type == Event.EventType.CHILD_CHANGED && oldType == Event.EventType.CHILD_ADDED) {
        changeMap.put(childKey, Change.childAddedChange(childKey, change.getIndexedNode()));
      } else if (type == Event.EventType.CHILD_CHANGED
          && oldType == Event.EventType.CHILD_CHANGED) {
        changeMap.put(
            childKey,
            Change.childChangedChange(
                childKey, change.getIndexedNode(), oldChange.getOldIndexedNode()));
      } else {
        throw new IllegalStateException(
            "Illegal combination of changes: " + change + " occurred after " + oldChange);
      }
    } else {
      changeMap.put(change.getChildKey(), change);
    }
  }

  public List<Change> getChanges() {
    return new ArrayList<Change>(this.changeMap.values());
  }
}
