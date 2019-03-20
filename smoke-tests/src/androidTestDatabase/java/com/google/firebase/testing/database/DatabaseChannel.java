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

package com.google.firebase.testing.database;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.testing.common.TaskChannel;

/**
 * A specialized channel for sending listen results to the testing thread.
 *
 * <p>This channel adds specializations for database. The methods in this class are intended to be
 * executed from the main thread, but they are safe to execute from any thread. This class extends
 * {@link TaskChannel}, so it can also used with any of the task-producing methods of database.
 */
public class DatabaseChannel extends TaskChannel<DataSnapshot> {

  /**
   * Adds a listener to the query that sends the results back to the testing thread.
   *
   * <p>The listener is only active for a single event. This method performs no mutations, so it
   * should most likely be followed by asequence of the form: {@code
   * channel.trapFailure(mutation).andIgnoreResult()}.
   */
  public void sendNextValueEvent(Query query) {
    query.addListenerForSingleValueEvent(
        new ValueEventListener() {
          @Override
          public void onCancelled(DatabaseError error) {
            fail(error.toException());
          }

          @Override
          public void onDataChange(DataSnapshot snapshot) {
            succeed(snapshot);
          }
        });
  }
}
