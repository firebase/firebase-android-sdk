// Copyright 2022 Google LLC
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

package com.google.firebase.firestore;

import static com.google.firebase.firestore.AggregateField.count;

import android.app.Activity;
import androidx.annotation.NonNull;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

final class AggregateDemo {

  private final FirebaseFirestore db;

  AggregateDemo(@NonNull FirebaseFirestore db) {
    this.db = db;
  }

  void countBasicQuery() {
    AggregateQuery query = db.collection("users").count();
    AggregateQuerySnapshot snapshot = query.get(AggregateSource.SERVER_DIRECT).getResult();
    assertEqual(snapshot.get(count()), 50);
  }

  void countLimitNumRowsScanned() {
    AggregateQuery query = db.collection("users").limit(11).count();
    AggregateQuerySnapshot snapshot = query.get(AggregateSource.SERVER_DIRECT).getResult();
    assertEqual(snapshot.get(count()), 11);
  }

  void countUpTo() {
    AggregateQuery query = db.collection("users").aggregate(count().upTo(11));
    AggregateQuerySnapshot snapshot = query.get(AggregateSource.SERVER_DIRECT).getResult();
    assertEqual(snapshot.get(count()), 11);
  }

  void countRealTimeUpdates() throws InterruptedException {
    AggregateQuery query = db.collection("users").aggregate(count());

    Lock lock = new ReentrantLock();
    Condition condition = lock.newCondition();

    ListenerRegistration registration =
        query
            .listen()
            .startDirectFromServer(
                (snapshot, error) -> {
                  assertNull(error);
                  assertNotNull(snapshot);
                  assertEqual(snapshot.get(count()), 50);
                  assertFalse(snapshot.getMetadata().isFromCache());
                  assertFalse(snapshot.getMetadata().hasPendingWrites());
                  lock.lock();
                  try {
                    condition.signalAll();
                  } finally {
                    lock.unlock();
                  }
                });

    lock.lock();
    try {
      condition.await();
    } finally {
      lock.unlock();
    }

    registration.remove();
  }

  private static void assertEqual(Object o1, Object o2) {}

  private static void assertNull(Object o) {}

  private static void assertFalse(Object o) {}

  private static void assertNotNull(Object o) {
    if (o == null) {
      throw new NullPointerException();
    }
  }
}
