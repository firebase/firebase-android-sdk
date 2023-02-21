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

package com.google.firebase.appdistribution.impl;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.annotations.concurrent.Blocking;
import com.google.firebase.annotations.concurrent.Lightweight;
import com.google.firebase.appdistribution.FirebaseAppDistributionException;
import com.google.firebase.appdistribution.FirebaseAppDistributionException.Status;
import com.google.firebase.concurrent.TestOnlyExecutors;
import java.util.concurrent.Executor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class FeedbackSenderTest {
  private static final String TEST_RELEASE_NAME = "release-name";
  private static final String TEST_FEEDBACK_NAME = "feedback-name";
  private static final String TEST_FEEDBACK_TEXT = "Feedback text";
  private static final Uri TEST_SCREENSHOT_URI =
      Uri.fromFile(ApplicationProvider.getApplicationContext().getFileStreamPath("test.png"));

  @Blocking private final Executor blockingExecutor = TestOnlyExecutors.blocking();
  @Lightweight private final Executor lightweightExecutor = TestOnlyExecutors.lite();

  @Mock private FirebaseAppDistributionTesterApiClient mockTesterApiClient;

  private FeedbackSender feedbackSender;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    feedbackSender = new FeedbackSender(mockTesterApiClient, lightweightExecutor);
  }

  @Test
  public void sendFeedback_success() throws Exception {
    when(mockTesterApiClient.createFeedback(TEST_RELEASE_NAME, TEST_FEEDBACK_TEXT))
        .thenReturn(Tasks.forResult(TEST_FEEDBACK_NAME));
    when(mockTesterApiClient.attachScreenshot(TEST_FEEDBACK_NAME, TEST_SCREENSHOT_URI))
        .thenReturn(Tasks.forResult(TEST_FEEDBACK_NAME));
    when(mockTesterApiClient.commitFeedback(TEST_FEEDBACK_NAME, FeedbackTrigger.CUSTOM))
        .thenReturn(Tasks.forResult(null));

    Task<Void> task =
        feedbackSender.sendFeedback(
            TEST_RELEASE_NAME, TEST_FEEDBACK_TEXT, TEST_SCREENSHOT_URI, FeedbackTrigger.CUSTOM);
    TestUtils.awaitTask(task);

    verify(mockTesterApiClient).createFeedback(TEST_RELEASE_NAME, TEST_FEEDBACK_TEXT);
    verify(mockTesterApiClient).attachScreenshot(TEST_FEEDBACK_NAME, TEST_SCREENSHOT_URI);
    verify(mockTesterApiClient).commitFeedback(TEST_FEEDBACK_NAME, FeedbackTrigger.CUSTOM);
  }

  @Test
  public void sendFeedback_withoutScreenshot_success() throws Exception {
    when(mockTesterApiClient.createFeedback(TEST_RELEASE_NAME, TEST_FEEDBACK_TEXT))
        .thenReturn(Tasks.forResult(TEST_FEEDBACK_NAME));
    when(mockTesterApiClient.commitFeedback(TEST_FEEDBACK_NAME, FeedbackTrigger.CUSTOM))
        .thenReturn(Tasks.forResult(null));

    Task<Void> task =
        feedbackSender.sendFeedback(
            TEST_RELEASE_NAME, TEST_FEEDBACK_TEXT, /* screenshot= */ null, FeedbackTrigger.CUSTOM);
    TestUtils.awaitTask(task);

    verify(mockTesterApiClient).createFeedback(TEST_RELEASE_NAME, TEST_FEEDBACK_TEXT);
    verify(mockTesterApiClient).commitFeedback(TEST_FEEDBACK_NAME, FeedbackTrigger.CUSTOM);
    verify(mockTesterApiClient, never()).attachScreenshot(any(), any());
  }

  @Test
  public void sendFeedback_createFeedbackFails_failsTask() {
    FirebaseAppDistributionException cause =
        new FirebaseAppDistributionException("test ex", Status.AUTHENTICATION_FAILURE);
    when(mockTesterApiClient.createFeedback(TEST_RELEASE_NAME, TEST_FEEDBACK_TEXT))
        .thenReturn(Tasks.forException(cause));

    Task<Void> task =
        feedbackSender.sendFeedback(
            TEST_RELEASE_NAME, TEST_FEEDBACK_TEXT, TEST_SCREENSHOT_URI, FeedbackTrigger.CUSTOM);

    TestUtils.awaitTaskFailure(task, Status.AUTHENTICATION_FAILURE, "test ex");
  }

  @Test
  public void sendFeedback_attachScreenshotFails_failsTask() {
    when(mockTesterApiClient.createFeedback(TEST_RELEASE_NAME, TEST_FEEDBACK_TEXT))
        .thenReturn(Tasks.forResult(TEST_FEEDBACK_NAME));
    FirebaseAppDistributionException cause =
        new FirebaseAppDistributionException("test ex", Status.AUTHENTICATION_FAILURE);
    when(mockTesterApiClient.attachScreenshot(TEST_FEEDBACK_NAME, TEST_SCREENSHOT_URI))
        .thenReturn(Tasks.forException(cause));

    Task<Void> task =
        feedbackSender.sendFeedback(
            TEST_RELEASE_NAME, TEST_FEEDBACK_TEXT, TEST_SCREENSHOT_URI, FeedbackTrigger.CUSTOM);

    TestUtils.awaitTaskFailure(task, Status.AUTHENTICATION_FAILURE, "test ex");
  }

  @Test
  public void sendFeedback_commitFeedbackFails_failsTask() {
    when(mockTesterApiClient.createFeedback(TEST_RELEASE_NAME, TEST_FEEDBACK_TEXT))
        .thenReturn(Tasks.forResult(TEST_FEEDBACK_NAME));
    when(mockTesterApiClient.attachScreenshot(TEST_FEEDBACK_NAME, TEST_SCREENSHOT_URI))
        .thenReturn(Tasks.forResult(TEST_FEEDBACK_NAME));
    FirebaseAppDistributionException cause =
        new FirebaseAppDistributionException("test ex", Status.AUTHENTICATION_FAILURE);
    when(mockTesterApiClient.commitFeedback(TEST_FEEDBACK_NAME, FeedbackTrigger.CUSTOM))
        .thenReturn(Tasks.forException(cause));

    Task<Void> task =
        feedbackSender.sendFeedback(
            TEST_RELEASE_NAME, TEST_FEEDBACK_TEXT, TEST_SCREENSHOT_URI, FeedbackTrigger.CUSTOM);

    TestUtils.awaitTaskFailure(task, Status.AUTHENTICATION_FAILURE, "test ex");
  }
}
