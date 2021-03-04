// Copyright 2021 Google LLC
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
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.concurrent.Semaphore;
import org.junit.After;
import org.junit.Test;

@org.junit.runner.RunWith(AndroidJUnit4.class)
public class DatabaseTransactionTest {

  @After
  public void tearDown() {
    IntegrationTestHelpers.failOnFirstUncaughtException();
  }

  @Test
  public void testNoOpTransactionRuns() throws InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();
    FirebaseDatabase db = ref.getDatabase();

    Semaphore semaphore = new Semaphore(0);

    db.runTransaction(
        new DatabaseTransaction.Function<Void>() {
          @Override
          public Void apply(@NonNull DatabaseTransaction.DatabaseTransactionContext transaction)
              throws FirebaseDatabaseException {
            semaphore.release();
            return null;
          }
        });

    IntegrationTestHelpers.waitFor(semaphore);
  }
}
