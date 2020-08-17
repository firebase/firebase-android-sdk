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

import static android.os.Looper.getMainLooper;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.shadows.ShadowPreconditions;
import com.google.firebase.messaging.testing.FakeScheduledExecutorService;
import com.google.firebase.messaging.testing.FirebaseIidRoboTestHelper;
import com.google.firebase.platforminfo.UserAgentPublisher;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.LooperMode.Mode;

/**
 * Firebase Messaging tests.
 *
 * <p>Use ShadowPreconditions to override the Tasks.await()'s expectation for task to run on
 * background threads.
 */
@RunWith(RobolectricTestRunner.class)
@Config(shadows = ShadowPreconditions.class)
@LooperMode(Mode.PAUSED)
public final class FirebaseMessagingRoboTest {

  private static final String INVALID_TOPIC = "@invalid";
  private static final String TOPIC_NAME = "name";
  private static final String VALID_TOPIC = "/topics/" + TOPIC_NAME;
  private static final String APPLICATION_ID = FirebaseIidRoboTestHelper.APP_ID;
  private static final String PROJECT_ID = FirebaseIidRoboTestHelper.PROJECT_ID;
  private static final String API_KEY = FirebaseIidRoboTestHelper.API_KEY;

  private Application context;
  private FirebaseMessaging firebaseMessaging;
  private final FakeScheduledExecutorService fakeScheduledExecutorService =
      new FakeScheduledExecutorService();
  private TopicsSubscriber topicSubscriber;

  @Before
  public void setUp() throws InterruptedException, ExecutionException, TimeoutException {
    FirebaseIidRoboTestHelper.addGmsCorePackageInfo();
    FirebaseApp.clearInstancesForTest();
    context = ApplicationProvider.getApplicationContext();
    FirebaseApp.initializeApp(
        context,
        new FirebaseOptions.Builder()
            .setApplicationId(APPLICATION_ID)
            .setProjectId(PROJECT_ID)
            .setApiKey(API_KEY)
            .build());
    firebaseMessaging = FirebaseMessaging.getInstance();
    // Making sure Topic subscriber task succeeds before proceeding with tests.
    topicSubscriber = Tasks.await(firebaseMessaging.getTopicsSubscriberTask(), 5, SECONDS);
    clearTopicOperations();
  }

  private void clearTopicOperations() {
    Context context = ApplicationProvider.getApplicationContext();
    TopicsStore store = TopicsStore.getInstance(context, fakeScheduledExecutorService);
    store.clearTopicOperations();

    // To make sure store pending operations are executed.
    fakeScheduledExecutorService.simulateNormalOperationFor(0, SECONDS);
  }

  /** Test that setting auto-init enabled at runtime overrides the manifest setting. */
  @Test
  public void testSetAutoInitEnabled() {
    FirebaseMessaging.getInstance().setAutoInitEnabled(true);
    assertThat(FirebaseMessaging.getInstance().isAutoInitEnabled()).isTrue();
  }

  @Test
  public void testSubscribeToTopic_withPrefix() {
    firebaseMessaging.subscribeToTopic(VALID_TOPIC);
    shadowOf(getMainLooper()).idle();
    assertThat(topicSubscriber.getStore().getNextTopicOperation())
        .isEqualTo(TopicOperation.subscribe(VALID_TOPIC));
  }

  @Test
  public void testUnsubscribeFromTopic_withPrefix() {
    firebaseMessaging.unsubscribeFromTopic(VALID_TOPIC);
    shadowOf(getMainLooper()).idle();
    assertThat(topicSubscriber.getStore().getNextTopicOperation())
        .isEqualTo(TopicOperation.unsubscribe(VALID_TOPIC));
  }

  @Test
  public void testSubscribeToTopic_invalid() {
    Task<Void> task = FirebaseMessaging.getInstance().subscribeToTopic(INVALID_TOPIC);
    shadowOf(getMainLooper()).idle();
    assertThat(task.getException()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void testUnsubscribeFromTopic_invalid() {
    Task<Void> task = FirebaseMessaging.getInstance().unsubscribeFromTopic(INVALID_TOPIC);
    shadowOf(getMainLooper()).idle();
    assertThat(task.getException()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void testLibraryVersionRegistration_valid() {
    assertThat(FirebaseApp.getInstance().get(UserAgentPublisher.class).getUserAgent())
        .contains("fire-fcm/" + BuildConfig.VERSION_NAME);
  }
}
