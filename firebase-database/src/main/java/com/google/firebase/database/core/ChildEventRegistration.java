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

package com.google.firebase.database.core;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.InternalHelpers;
import com.google.firebase.database.annotations.NotNull;
import com.google.firebase.database.core.view.Change;
import com.google.firebase.database.core.view.DataEvent;
import com.google.firebase.database.core.view.Event;
import com.google.firebase.database.core.view.QuerySpec;

public class ChildEventRegistration extends EventRegistration {

  private final Repo repo;
  private final ChildEventListener eventListener;
  private final QuerySpec spec;

  public ChildEventRegistration(
      @NotNull Repo repo, @NotNull ChildEventListener eventListener, @NotNull QuerySpec spec) {
    this.repo = repo;
    this.eventListener = eventListener;
    this.spec = spec;
  }

  @Override
  public boolean respondsTo(Event.EventType eventType) {
    return eventType != Event.EventType.VALUE;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof ChildEventRegistration
        && ((ChildEventRegistration) other).eventListener.equals(eventListener)
        && ((ChildEventRegistration) other).repo.equals(repo)
        && ((ChildEventRegistration) other).spec.equals(spec);
  }

  @Override
  public int hashCode() {
    int result = this.eventListener.hashCode();
    result = 31 * result + this.repo.hashCode();
    result = 31 * result + this.spec.hashCode();
    return result;
  }

  @Override
  public DataEvent createEvent(Change change, QuerySpec query) {
    DatabaseReference ref =
        InternalHelpers.createReference(repo, query.getPath().child(change.getChildKey()));

    DataSnapshot snapshot = InternalHelpers.createDataSnapshot(ref, change.getIndexedNode());
    String prevName = change.getPrevName() != null ? change.getPrevName().asString() : null;
    return new DataEvent(change.getEventType(), this, snapshot, prevName);
  }

  @Override
  public void fireEvent(final DataEvent eventData) {
    if (isZombied()) {
      return;
    }
    switch (eventData.getEventType()) {
      case CHILD_ADDED:
        eventListener.onChildAdded(eventData.getSnapshot(), eventData.getPreviousName());
        break;
      case CHILD_CHANGED:
        eventListener.onChildChanged(eventData.getSnapshot(), eventData.getPreviousName());
        break;
      case CHILD_MOVED:
        eventListener.onChildMoved(eventData.getSnapshot(), eventData.getPreviousName());
        break;
      case CHILD_REMOVED:
        eventListener.onChildRemoved(eventData.getSnapshot());
        break;
      default:
        // Shouldn't ever happen. No-op
    }
  }

  @Override
  public void fireCancelEvent(final DatabaseError error) {
    eventListener.onCancelled(error);
  }

  @Override
  public EventRegistration clone(QuerySpec newQuery) {
    return new ChildEventRegistration(this.repo, this.eventListener, newQuery);
  }

  @Override
  public boolean isSameListener(EventRegistration other) {
    return (other instanceof ChildEventRegistration)
        && ((ChildEventRegistration) other).eventListener.equals(eventListener);
  }

  @NotNull
  @Override
  public QuerySpec getQuerySpec() {
    return spec;
  }

  @Override
  public String toString() {
    return "ChildEventRegistration";
  }

  @Override
  Repo getRepo() {
    return repo;
  }
}
