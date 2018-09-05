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

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.core.EventRegistration;
import com.google.firebase.database.core.Path;

public class DataEvent implements Event {

  private final EventType eventType;
  private final EventRegistration eventRegistration;
  private final DataSnapshot snapshot;
  private final String prevName;

  public DataEvent(
      EventType eventType,
      EventRegistration eventRegistration,
      DataSnapshot snapshot,
      String prevName) {
    this.eventType = eventType;
    this.eventRegistration = eventRegistration;
    this.snapshot = snapshot;
    this.prevName = prevName;
  }

  @Override
  public Path getPath() {
    Path path = this.snapshot.getRef().getPath();
    if (this.eventType == EventType.VALUE) {
      return path;
    } else {
      return path.getParent();
    }
  }

  public DataSnapshot getSnapshot() {
    return this.snapshot;
  }

  public String getPreviousName() {
    return this.prevName;
  }

  public EventType getEventType() {
    return this.eventType;
  }

  @Override
  public void fire() {
    this.eventRegistration.fireEvent(this);
  }

  @Override
  public String toString() {
    if (this.eventType == EventType.VALUE) {
      return this.getPath() + ": " + this.eventType + ": " + this.snapshot.getValue(true);
    } else {
      return this.getPath()
          + ": "
          + this.eventType
          + ": { "
          + this.snapshot.getKey()
          + ": "
          + this.snapshot.getValue(true)
          + " }";
    }
  }
}
