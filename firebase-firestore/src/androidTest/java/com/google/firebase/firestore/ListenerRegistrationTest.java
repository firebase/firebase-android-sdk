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

package com.google.firebase.firestore;

import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testCollection;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitFor;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;

import android.support.test.runner.AndroidJUnit4;
import com.google.firebase.firestore.testutil.IntegrationTestUtil;
import java.util.concurrent.Semaphore;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ListenerRegistrationTest {

  @After
  public void tearDown() {
    IntegrationTestUtil.tearDown();
  }

  @Test
  public void canBeRemoved() {
    CollectionReference collectionReference = testCollection();
    DocumentReference documentReference = collectionReference.document();

    Semaphore events = new Semaphore(0);
    ListenerRegistration one =
        collectionReference.addSnapshotListener(
            (value, error) -> {
              assertNull(error);
              events.release();
            });
    ListenerRegistration two =
        documentReference.addSnapshotListener(
            (value, error) -> {
              assertNull(error);
              events.release();
            });

    // Initial events
    waitFor(events, 2);

    // Trigger new events
    waitFor(documentReference.set(map("foo", "bar")));

    // Write events should have triggered
    waitFor(events, 2);

    // No more events should occur
    one.remove();
    two.remove();

    waitFor(documentReference.set(map("foo", "new-bar")));

    // Assert no events actually occurred
    assertEquals(0, events.availablePermits());
  }

  @Test
  public void canBeRemovedTwice() {
    CollectionReference reference = testCollection();
    ListenerRegistration one = reference.addSnapshotListener((value, error) -> {});
    ListenerRegistration two = reference.document().addSnapshotListener((value, error) -> {});

    one.remove();
    one.remove();

    two.remove();
    two.remove();
  }

  @Test
  public void canBeRemovedIndependently() {
    CollectionReference collectionReference = testCollection();

    Semaphore eventsOne = new Semaphore(0);
    Semaphore eventsTwo = new Semaphore(0);
    ListenerRegistration one =
        collectionReference.addSnapshotListener(
            (value, error) -> {
              assertNull(error);
              eventsOne.release();
            });
    ListenerRegistration two =
        collectionReference.addSnapshotListener(
            (value, error) -> {
              assertNull(error);
              eventsTwo.release();
            });

    // Initial events
    waitFor(eventsOne);
    waitFor(eventsTwo);

    // Trigger new events
    waitFor(collectionReference.add(map("foo", "bar")));

    waitFor(eventsOne);
    waitFor(eventsTwo);

    // Should leave "two" unaffected
    one.remove();

    waitFor(collectionReference.add(map("foo", "new-bar")));

    // Assert only events for "two" actually occurred
    assertEquals(0, eventsOne.availablePermits());
    assertEquals(1, eventsTwo.availablePermits());

    // No more events should occur
    two.remove();
  }
}
