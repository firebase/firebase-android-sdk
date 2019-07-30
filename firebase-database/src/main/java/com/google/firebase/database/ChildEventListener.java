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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Classes implementing this interface can be used to receive events about changes in the child
 * locations of a given {@link DatabaseReference DatabaseReference} ref. Attach the listener to a
 * location using {@link DatabaseReference#addChildEventListener(ChildEventListener)} and the
 * appropriate method will be triggered when changes occur.
 */
public interface ChildEventListener {

  /**
   * This method is triggered when a new child is added to the location to which this listener was
   * added.
   *
   * @param snapshot An immutable snapshot of the data at the new child location
   * @param previousChildName The key name of sibling location ordered before the new child. This
   *     will be null for the first child node of a location.
   */
  public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName);

  /**
   * This method is triggered when the data at a child location has changed.
   *
   * @param snapshot An immutable snapshot of the data at the new data at the child location
   * @param previousChildName The key name of sibling location ordered before the child. This will
   *     be null for the first child node of a location.
   */
  public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName);

  /**
   * This method is triggered when a child is removed from the location to which this listener was
   * added.
   *
   * @param snapshot An immutable snapshot of the data at the child that was removed.
   */
  public void onChildRemoved(@NonNull DataSnapshot snapshot);

  /**
   * This method is triggered when a child location's priority changes. See {@link
   * DatabaseReference#setPriority(Object)} and <a
   * href="https://firebase.google.com/docs/database/android/retrieve-data#data_order"
   * target="_blank">Ordered Data</a> for more information on priorities and ordering data.
   *
   * @param snapshot An immutable snapshot of the data at the location that moved.
   * @param previousChildName The key name of the sibling location ordered before the child
   *     location. This will be null if this location is ordered first.
   */
  public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName);

  /**
   * This method will be triggered in the event that this listener either failed at the server, or
   * is removed as a result of the security and Firebase rules. For more information on securing
   * your data, see: <a href="https://firebase.google.com/docs/database/security/quickstart"
   * target="_blank"> Security Quickstart</a>
   *
   * @param error A description of the error that occurred
   */
  public void onCancelled(@NonNull DatabaseError error);
}
