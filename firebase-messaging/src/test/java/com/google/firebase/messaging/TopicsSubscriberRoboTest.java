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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.installations.InstallationTokenResult;
import com.google.firebase.messaging.shadows.ShadowPreconditions;
import com.google.firebase.messaging.testing.FakeScheduledExecutorService;
import com.google.firebase.messaging.testing.MessagingTestHelper;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * TopicsSubscriberRoboTest
 *
 * <p>Use ShadowPreconditions to override the Tasks.await()'s expectation for task to run on
 * background threads.
 */
@RunWith(RobolectricTestRunner.class)
@Config(shadows = ShadowPreconditions.class)
public class TopicsSubscriberRoboTest {

  private static final String TEST_TOKEN = "test_token";
  private static final String TEST_TOPIC = "test_topic";

  private TopicsStore store;
  private TopicsSubscriber topicsSubscriber;
  private FakeScheduledExecutorService fakeExecutor;

  @Mock private Metadata mockMetadata;
  @Mock private FirebaseInstallationsApi mockInstallationsApi;
  @Mock private HttpURLConnection mockConnection;
  @Mock private TopicSubscriptionClient mockTopicSubscriptionClient;
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Before
  public void setUp() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    FirebaseApp.clearInstancesForTest();
    FirebaseApp.initializeApp(
        context,
        new FirebaseOptions.Builder()
            .setApplicationId(MessagingTestHelper.GOOGLE_APP_ID)
            .setProjectId(MessagingTestHelper.PROJECT_ID)
            .setApiKey(MessagingTestHelper.KEY)
            .build());

    // Inject fakeExecutor so that we can pause task executions for inspecting the state of the
    // topic queues
    fakeExecutor = new FakeScheduledExecutorService();
    store = TopicsStore.getInstance(context, fakeExecutor);
    store.clearTopicOperations();

    // We use TopicsSubscriber directly now as we mock the client
    topicsSubscriber =
        new TopicsSubscriber(
            mockMetadata, store, mockTopicSubscriptionClient, context, fakeExecutor);

    // Initial syncCheck
    fakeExecutor.simulateNormalOperationFor(0, SECONDS);
    store = topicsSubscriber.getStore();

    doReturn(true).when(mockMetadata).isGmscorePresent();
    InstallationTokenResult tokenResult =
        InstallationTokenResult.builder()
            .setToken(TEST_TOKEN)
            .setTokenExpirationTimestamp(0)
            .setTokenCreationTimestamp(0)
            .build();
    doReturn(Tasks.forResult(tokenResult)).when(mockInstallationsApi).getToken(false);
    doReturn(Tasks.forResult("fid")).when(mockInstallationsApi).getId();

    // Default mock behavior for connection
    doReturn(200).when(mockConnection).getResponseCode();
    doReturn(null).when(mockConnection).getOutputStream();
    doReturn(null).when(mockConnection).getInputStream();
  }

  @Test
  public void testScheduleTopicOperation_subscribeWhenEmptyQueue() {
    TopicOperation topicOperation = TopicOperation.subscribe(TEST_TOPIC);
    topicsSubscriber.scheduleTopicOperation(topicOperation);
    assertThat(store.getNextTopicOperation()).isEqualTo(topicOperation);
  }

  @Test
  public void testScheduleTopicOperation_appendsToQueue() {
    TopicOperation subscribeOperation = TopicOperation.subscribe(TEST_TOPIC);
    TopicOperation unSubscribeOperation = TopicOperation.unsubscribe(TEST_TOPIC);
    topicsSubscriber.scheduleTopicOperation(subscribeOperation);
    topicsSubscriber.scheduleTopicOperation(unSubscribeOperation);

    assertThat(store.pollTopicOperation()).isEqualTo(subscribeOperation);
    assertThat(store.pollTopicOperation()).isEqualTo(unSubscribeOperation);
  }

  @Test
  public void testHasPendingOperation_false() {
    assertThat(topicsSubscriber.hasPendingOperation()).isFalse();
  }

  @Test
  public void testHasPendingOperation_true() {
    topicsSubscriber.scheduleTopicOperation(TopicOperation.subscribe(TEST_TOPIC));
    assertThat(topicsSubscriber.hasPendingOperation()).isTrue();
  }

  @Test
  public void testSingleSubscribe() {
    Task<Void> task = topicsSubscriber.subscribeToTopic(TEST_TOPIC);
    // fakeExecutor hasn't executed thus the queue is non-empty
    assertThat(store.getNextTopicOperation()).isEqualTo(TopicOperation.subscribe(TEST_TOPIC));

    // execute immediately
    fakeExecutor.simulateNormalOperationFor(/* timeout= */ 0, SECONDS);

    assertThat(task.isSuccessful()).isTrue();
    assertThat(topicsSubscriber.hasPendingOperation()).isFalse();
    assertThat(store.getNextTopicOperation()).isNull();
  }

  @Test
  public void testSingleUnsubscribe() throws Exception {
    Task<Void> task = topicsSubscriber.unsubscribeFromTopic(TEST_TOPIC);
    assertThat(store.getNextTopicOperation()).isEqualTo(TopicOperation.unsubscribe(TEST_TOPIC));

    // execute immediately
    fakeExecutor.simulateNormalOperationFor(/* timeout= */ 0, SECONDS);

    assertThat(task.isSuccessful()).isTrue();
    assertThat(topicsSubscriber.hasPendingOperation()).isFalse();
    assertThat(store.getNextTopicOperation()).isNull();
    verify(mockTopicSubscriptionClient).unsubscribe(TEST_TOPIC);
  }

  @Test
  public void testSingleSubscribe_failure() throws Exception {
    doThrow(new IOException(GmsRpc.ERROR_INTERNAL_SERVER_ERROR))
        .when(mockTopicSubscriptionClient)
        .subscribe(anyString());

    Task<Void> task = topicsSubscriber.subscribeToTopic(TEST_TOPIC);
    assertThat(store.getNextTopicOperation()).isEqualTo(TopicOperation.subscribe(TEST_TOPIC));

    // execute immediately
    fakeExecutor.simulateNormalOperationFor(/* timeout= */ 0, SECONDS);

    assertThat(task.isComplete()).isFalse();
    assertThat(topicsSubscriber.hasPendingOperation()).isTrue();
    assertThat(store.getNextTopicOperation()).isNotNull();
    assertThat(store.getNextTopicOperation()).isEqualTo(TopicOperation.subscribe(TEST_TOPIC));
  }

  @Test
  public void testSingleUnsubscribe_failure() throws Exception {
    doThrow(new IOException(GmsRpc.ERROR_INTERNAL_SERVER_ERROR))
        .when(mockTopicSubscriptionClient)
        .unsubscribe(anyString());

    Task<Void> task = topicsSubscriber.unsubscribeFromTopic(TEST_TOPIC);
    // fakeExecutor hasn't executed thus the queue is non-empty
    assertThat(store.getNextTopicOperation()).isEqualTo(TopicOperation.unsubscribe(TEST_TOPIC));

    // execute immediately
    fakeExecutor.simulateNormalOperationFor(/* timeout= */ 0, SECONDS);

    assertThat(task.isComplete()).isFalse();
    assertThat(topicsSubscriber.hasPendingOperation()).isTrue();
    assertThat(store.getNextTopicOperation()).isNotNull();
    assertThat(store.getNextTopicOperation()).isEqualTo(TopicOperation.unsubscribe(TEST_TOPIC));
  }

  @Test
  public void testMultipleOperations() {
    Task<Void> task1 = topicsSubscriber.subscribeToTopic("topic1");
    Task<Void> task2 = topicsSubscriber.subscribeToTopic("topic2");
    Task<Void> task3 = topicsSubscriber.unsubscribeFromTopic("topic1");
    Task<Void> task4 = topicsSubscriber.subscribeToTopic("topic3");

    List<TopicOperation> operations = topicsSubscriber.getStore().getOperations();
    assertThat(operations.get(0)).isEqualTo(TopicOperation.subscribe("topic1"));
    assertThat(operations.get(1)).isEqualTo(TopicOperation.subscribe("topic2"));
    assertThat(operations.get(2)).isEqualTo(TopicOperation.unsubscribe("topic1"));
    assertThat(operations.get(3)).isEqualTo(TopicOperation.subscribe("topic3"));

    // execute immediately
    fakeExecutor.simulateNormalOperationFor(/* timeout= */ 0, SECONDS);

    for (Task<Void> task : Arrays.asList(task1, task2, task3, task4)) {
      assertThat(task.isSuccessful()).isTrue();
    }
    assertThat(topicsSubscriber.hasPendingOperation()).isFalse();
    assertThat(store.getNextTopicOperation()).isNull();
  }

  @Test
  public void testMultipleOperations_withFailure() throws Exception {
    // Mock client behavior
    doNothing()
        .doThrow(new IOException(GmsRpc.ERROR_INTERNAL_SERVER_ERROR))
        .when(mockTopicSubscriptionClient)
        .subscribe(anyString());

    Task<Void> task1 = topicsSubscriber.subscribeToTopic("topic1");
    Task<Void> task2 = topicsSubscriber.subscribeToTopic("topic2");
    List<TopicOperation> operations = topicsSubscriber.getStore().getOperations();
    assertThat(operations.get(0)).isEqualTo(TopicOperation.subscribe("topic1"));
    assertThat(operations.get(1)).isEqualTo(TopicOperation.subscribe("topic2"));

    // execute immediately
    fakeExecutor.simulateNormalOperationFor(/* timeout= */ 0, SECONDS);

    InOrder inOrder = inOrder(mockTopicSubscriptionClient);
    inOrder.verify(mockTopicSubscriptionClient).subscribe("topic1");
    doNothing()
        .doThrow(new IOException(GmsRpc.ERROR_INTERNAL_SERVER_ERROR)) // Fail first attempt (task2)
        .doNothing() // Succeed second attempt (retry of task2)
        .when(mockTopicSubscriptionClient)
        .subscribe(anyString());

    assertThat(task1.isSuccessful()).isTrue();
    assertThat(task2.isComplete()).isFalse();
    assertThat(topicsSubscriber.hasPendingOperation()).isTrue();
    assertThat(store.getNextTopicOperation()).isEqualTo(TopicOperation.subscribe("topic2"));

    // Now make it succeed and run it again
    topicsSubscriber.syncTopics();
    // execute immediately
    fakeExecutor.simulateNormalOperationFor(30, SECONDS);

    assertThat(task2.isSuccessful()).isTrue();
    assertThat(topicsSubscriber.hasPendingOperation()).isFalse();
    assertThat(store.getNextTopicOperation()).isNull();
  }

  @Test
  public void testMultipleOperationsOnSameTopic_withFailure() throws Exception {
    // Pass the first subscription but fail the second subscription operation
    doNothing() // task1 (subscribe) - success
        .doThrow(new IOException(GmsRpc.ERROR_INTERNAL_SERVER_ERROR)) // task3 (subscribe) - fail
        .doNothing() // task3 retry - success
        .when(mockTopicSubscriptionClient)
        .subscribe(eq(TEST_TOPIC));

    doNothing() // task2 (unsubscribe) - success
        .when(mockTopicSubscriptionClient)
        .unsubscribe(anyString());

    Task<Void> task1 = topicsSubscriber.subscribeToTopic(TEST_TOPIC);
    Task<Void> task2 = topicsSubscriber.unsubscribeFromTopic(TEST_TOPIC);
    Task<Void> task3 = topicsSubscriber.subscribeToTopic(TEST_TOPIC);
    List<TopicOperation> operations = topicsSubscriber.getStore().getOperations();
    assertThat(operations)
        .containsExactly(
            TopicOperation.subscribe(TEST_TOPIC),
            TopicOperation.unsubscribe(TEST_TOPIC),
            TopicOperation.subscribe(TEST_TOPIC))
        .inOrder();

    // execute immediately
    fakeExecutor.simulateNormalOperationFor(/* timeout= */ 0, SECONDS);

    // First 2 tasks should be successful, third not complete yet
    assertThat(task1.isSuccessful()).isTrue();
    assertThat(task2.isSuccessful()).isTrue();
    assertThat(task3.isComplete()).isFalse();

    // Remaining queue should be the single subscribe operation
    assertThat(topicsSubscriber.hasPendingOperation()).isTrue();
    assertThat(store.getNextTopicOperation()).isEqualTo(TopicOperation.subscribe(TEST_TOPIC));

    topicsSubscriber.syncTopics();
    // execute immediately
    fakeExecutor.simulateNormalOperationFor(300, SECONDS);
    assertThat(task3.isSuccessful()).isTrue();
    assertThat(topicsSubscriber.hasPendingOperation()).isFalse();
    assertThat(store.getNextTopicOperation()).isNull();
  }

  /** Test existing operations in the queue at startup */
  @Test
  public void testOperationsAlreadyInQueue() {
    // Add a couple of operations, then create the TopicsSubscriber again
    topicsSubscriber.scheduleTopicOperation(TopicOperation.subscribe("topic1"));
    topicsSubscriber.scheduleTopicOperation(TopicOperation.subscribe("topic2"));

    // We need to re-create topicsSubscriber
    topicsSubscriber =
        new TopicsSubscriber(
            mockMetadata,
            store,
            mockTopicSubscriptionClient,
            ApplicationProvider.getApplicationContext(),
            fakeExecutor);

    // Initial sync
    fakeExecutor.simulateNormalOperationFor(0, SECONDS);

    Task<Void> task = topicsSubscriber.subscribeToTopic("topic3");

    List<TopicOperation> operations = store.getOperations();
    assertThat(operations)
        .containsExactly(
            TopicOperation.subscribe("topic1"),
            TopicOperation.subscribe("topic2"),
            TopicOperation.subscribe("topic3"))
        .inOrder();

    // execute immediately
    fakeExecutor.simulateNormalOperationFor(/* timeout= */ 0, SECONDS);

    assertThat(task.isSuccessful()).isTrue();
    assertThat(topicsSubscriber.hasPendingOperation()).isFalse();
    assertThat(store.getNextTopicOperation()).isNull();
  }
}
