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
import static com.google.firebase.messaging.TopicsStore.KEY_TOPIC_OPERATIONS_QUEUE;
import static com.google.firebase.messaging.TopicsStore.PREFERENCES;
import static java.util.concurrent.TimeUnit.SECONDS;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.messaging.shadows.ShadowPreconditions;
import com.google.firebase.messaging.testing.FakeScheduledExecutorService;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(shadows = ShadowPreconditions.class)
public class TopicsStoreRoboTest {

  private TopicsStore store;
  private static final String TEST_TOPIC = "Test_Topic";
  private final FakeScheduledExecutorService executor = new FakeScheduledExecutorService();

  @Before
  public void setUp() {
    Context context = ApplicationProvider.getApplicationContext();
    store = TopicsStore.getInstance(context, executor);
    store.clearTopicOperations();
    // To make sure all the pending operations are done.
    executePendingOperations();
  }

  private void executePendingOperations() {
    executor.simulateNormalOperationFor(0, SECONDS);
  }

  @Test
  public void testGetTopicOperationQueue_emptyStore() {
    assertThat(store.getNextTopicOperation()).isNull();
  }

  @Test
  public void testRemoveTopicOperationQueue_emptyStore() {
    assertThat(store.removeTopicOperation(TopicOperation.subscribe(TEST_TOPIC))).isFalse();
  }

  @Test
  public void testPollOperationQueue_emptyStore() {
    assertThat(store.pollTopicOperation()).isNull();
  }

  @Test
  public void testAddTopicOperation_retainsValue() {
    TopicOperation topicOperation = TopicOperation.subscribe(TEST_TOPIC);
    store.addTopicOperation(topicOperation);
    assertThat(store.getNextTopicOperation()).isEqualTo(topicOperation);
  }

  @Test
  public void testRemoveTopicOperation_removesValue() {
    TopicOperation topicOperation = TopicOperation.subscribe(TEST_TOPIC);
    store.addTopicOperation(topicOperation);
    assertThat(store.getNextTopicOperation()).isEqualTo(topicOperation);
    store.removeTopicOperation(topicOperation);
    assertThat(store.getNextTopicOperation()).isNull();
  }

  @Test
  public void testMultipleAddRemoveOperations_worksAsExpected() {
    store.addTopicOperation(TopicOperation.subscribe("Test_Topic1"));
    store.addTopicOperation(TopicOperation.subscribe("Test_Topic2"));
    store.addTopicOperation(TopicOperation.subscribe("Test_Topic3"));
    assertThat(store.pollTopicOperation()).isEqualTo(TopicOperation.subscribe("Test_Topic1"));
    assertThat(store.pollTopicOperation()).isEqualTo(TopicOperation.subscribe("Test_Topic2"));
    assertThat(store.pollTopicOperation()).isEqualTo(TopicOperation.subscribe("Test_Topic3"));
    assertThat(store.getNextTopicOperation()).isNull();
  }

  @Test
  public void testBackwardCompatible() {
    // Old topic store implementation saves the queue as a ',' separated string which starts with
    // ','. The new topic store implementation saves the queue without a starting ','.
    // So this test aims to make sure the new implementation works seamlessly with values stored in
    // old format.
    Context context = ApplicationProvider.getApplicationContext();
    List<TopicOperation> operations =
        Arrays.asList(
            TopicOperation.subscribe("T1"),
            TopicOperation.unsubscribe("T2"),
            TopicOperation.subscribe("T3"));
    SharedPreferences sharedPrefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    // Old queue starts with "," prefixed!
    sharedPrefs
        .edit()
        .putString(
            KEY_TOPIC_OPERATIONS_QUEUE,
            ","
                + operations.get(0).serialize()
                + ","
                + operations.get(1).serialize()
                + ","
                + operations.get(2).serialize())
        .commit();
    TopicsStore.clearCaches();
    store = TopicsStore.getInstance(context, executor);

    assertThat(store.getOperations()).containsExactlyElementsIn(operations).inOrder();
  }
}
