// Copyright 2020 Google LLC
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
package com.google.firebase.messaging;

import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.SECONDS;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.messaging.testing.FakeScheduledExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class SharedPreferencesQueueRoboTest {
  private static final String ITEM_SEPARATOR = ",";
  private static final String TEST_TOPIC = "Test_Topic";
  private final FakeScheduledExecutorService executor = new FakeScheduledExecutorService();
  private SharedPreferencesQueue queue;

  @Before
  public void setUp() {
    queue = initQueue(true);
  }

  private SharedPreferencesQueue initQueue(boolean clearQueue) {
    return initQueue(clearQueue, ITEM_SEPARATOR);
  }

  private SharedPreferencesQueue initQueue(boolean clearQueue, String itemSeparator) {
    SharedPreferencesQueue sharedPreferencesQueue =
        SharedPreferencesQueue.createInstance(
            ApplicationProvider.getApplicationContext()
                .getSharedPreferences("TestPreferences", Context.MODE_PRIVATE),
            "Queue",
            itemSeparator,
            executor);

    if (clearQueue) {
      sharedPreferencesQueue.clear();
    }

    // To make sure all pending sync operations are done.
    executePendingOperations();

    return sharedPreferencesQueue;
  }

  private void executePendingOperations() {
    executor.simulateNormalOperationFor(0, SECONDS);
  }

  @Test
  public void testAdd_validItem() {
    assertThat(queue.size()).isEqualTo(0);
    assertThat(queue.add(TEST_TOPIC)).isTrue();
    assertThat(queue.size()).isEqualTo(1);
    assertThat(queue.peek()).isEqualTo(TEST_TOPIC);
  }

  @Test
  public void testAdd_invalidItem() {
    assertThat(queue.size()).isEqualTo(0);
    assertThat(queue.add(TEST_TOPIC + ITEM_SEPARATOR)).isFalse();
    assertThat(queue.size()).isEqualTo(0);
  }

  @Test
  public void testRemove() {
    queue.add(TEST_TOPIC);
    assertThat(queue.remove()).isEqualTo(TEST_TOPIC);
    assertThat(queue.size()).isEqualTo(0);
  }

  @Test
  public void testRemoveSpecificItem() {
    queue.add(TEST_TOPIC + "1");
    queue.add(TEST_TOPIC + "2");
    queue.add(TEST_TOPIC + "3");
    assertThat(queue.size()).isEqualTo(3);
    assertThat(queue.remove(TEST_TOPIC + "2")).isTrue();
    assertThat(queue.size()).isEqualTo(2);
    assertThat(queue.remove("Unknown item")).isFalse();
    assertThat(queue.size()).isEqualTo(2);
  }

  @Test
  public void testPeek() {
    queue.add(TEST_TOPIC);
    assertThat(queue.peek()).isEqualTo(TEST_TOPIC);
  }

  @Test
  public void testQueue_maintainsItemsInorder() {
    queue.add(TEST_TOPIC + "1");
    queue.add(TEST_TOPIC + "3");
    queue.add(TEST_TOPIC + "2");
    assertThat(queue.toList())
        .containsExactly(TEST_TOPIC + "1", TEST_TOPIC + "3", TEST_TOPIC + "2")
        .inOrder();
  }

  @Test
  @SuppressWarnings("GuardedBy")
  public void testSerialize() {
    queue.add(TEST_TOPIC + "1");
    queue.add(TEST_TOPIC + "3");
    queue.add(TEST_TOPIC + "2");
    assertThat(queue.serialize())
        .isEqualTo(
            TEST_TOPIC
                + "1"
                + ITEM_SEPARATOR
                + TEST_TOPIC
                + "3"
                + ITEM_SEPARATOR
                + TEST_TOPIC
                + "2"
                + ITEM_SEPARATOR);
  }

  @Test
  public void testQueue_retainsItems() {
    queue.add(TEST_TOPIC + "1");
    queue.add(TEST_TOPIC + "3");
    queue.add(TEST_TOPIC + "2");
    executePendingOperations();
    queue = initQueue(false);
    assertThat(queue.size()).isEqualTo(3);
    assertThat(queue.toList())
        .containsExactly(TEST_TOPIC + "1", TEST_TOPIC + "3", TEST_TOPIC + "2")
        .inOrder();
  }

  @Test
  @SuppressWarnings("GuardedBy")
  public void testMultiTransactions() {
    queue.beginTransaction();
    queue.add(TEST_TOPIC);

    // transaction should not be visible from other queue instances yet
    assertThat(initQueue(false).toList()).isEmpty();

    queue.commitTransaction();
    executePendingOperations();

    assertThat(initQueue(false).toList()).containsExactly(TEST_TOPIC);
  }

  @Test
  public void testCorruptedQueue_wontCrashButStartsFresh() {
    queue = initQueue(true, ",");
    queue.add("T1");
    executePendingOperations();
    assertThat(queue.size()).isEqualTo(1);

    // Corrupting queue by using a different separator
    queue = initQueue(false, ";");

    // the old value will not be restored since corrupted.
    assertThat(queue.size()).isEqualTo(0);

    // Checking queue continue to operate as fresh after corruption
    queue.add("T2");
    executePendingOperations();
    assertThat(queue.size()).isEqualTo(1);

    // Making sure the fresh queue values are retained.
    queue = initQueue(false, ";");
    assertThat(queue.size()).isEqualTo(1);
    assertThat(queue.peek()).isEqualTo("T2");
  }
}
