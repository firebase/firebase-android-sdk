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

import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Semaphore;

public class ValueExpectationHelper {

  private static class QueryAndListener {
    public Query query;
    public ValueEventListener listener;

    public QueryAndListener(Query query, ValueEventListener listener) {
      this.query = query;
      this.listener = listener;
    }
  }

  private Semaphore semaphore = new Semaphore(0);
  private int count = 0;
  private List<QueryAndListener> expectations = new ArrayList<QueryAndListener>();

  public void add(final Query query, final Object expected) {
    count++;
    ValueEventListener listener =
        query.addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                Object result = snapshot.getValue();
                // Hack to handle race condition in initial data
                if (DeepEquals.deepEquals(expected, result)) {
                  // We may pass through intermediate states, but we should end up with the correct
                  // state
                  semaphore.release(1);
                }
              }

              @Override
              public void onCancelled(DatabaseError error) {
                fail("Listen cancelled");
              }
            });
    expectations.add(new QueryAndListener(query, listener));
  }

  public void waitForEvents() throws InterruptedException {
    IntegrationTestHelpers.waitFor(semaphore, count);
    Iterator<QueryAndListener> iter = expectations.iterator();
    while (iter.hasNext()) {
      QueryAndListener pair = iter.next();
      pair.query.removeEventListener(pair.listener);
    }
    expectations.clear();
  }
}
