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

/**
 * Classes implementing this interface can be used to receive events about data changes at a
 * location. Attach the listener to a location user {@link
 * DatabaseReference#addValueEventListener(ValueEventListener)}.
 */
public interface ValueEventListener {

  /**
   * This method will be called with a snapshot of the data at this location. It will also be called
   * each time that data changes.
   *
   * @param snapshot The current data at the location
   */
  public void onDataChange(@NonNull DataSnapshot snapshot);

  /**
   * This method will be triggered in the event that this listener either failed at the server, or
   * is removed as a result of the security and Firebase Database rules. For more information on
   * securing your data, see: <a
   * href="https://firebase.google.com/docs/database/security/quickstart" target="_blank"> Security
   * Quickstart</a>
   *
   * @param error A description of the error that occurred
   */
  public void onCancelled(@NonNull DatabaseError error);
}
