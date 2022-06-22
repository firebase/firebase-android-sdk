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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.appdistribution.FirebaseAppDistributionException;
import com.google.firebase.appdistribution.FirebaseAppDistributionException.Status;
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
  private static final Bitmap TEST_SCREENSHOT = Bitmap.createBitmap(400, 400, Config.RGB_565);

  @Mock private FirebaseAppDistributionTesterApiClient mockTesterApiClient;

  private FeedbackSender feedbackSender;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    feedbackSender = new FeedbackSender(mockTesterApiClient);
  }

  @Test
  public void sendFeedback_success() throws Exception {
    when(mockTesterApiClient.createFeedback(TEST_RELEASE_NAME, TEST_FEEDBACK_TEXT))
        .thenReturn(Tasks.forResult(TEST_FEEDBACK_NAME));
    when(mockTesterApiClient.attachScreenshot(TEST_FEEDBACK_NAME, TEST_SCREENSHOT))
        .thenReturn(Tasks.forResult(TEST_FEEDBACK_NAME));
    when(mockTesterApiClient.commitFeedback(TEST_FEEDBACK_NAME)).thenReturn(Tasks.forResult(null));

    Task<Void> task =
        feedbackSender.sendFeedback(TEST_RELEASE_NAME, TEST_FEEDBACK_TEXT, TEST_SCREENSHOT);
    TestUtils.awaitTask(task);

    verify(mockTesterApiClient).createFeedback(TEST_RELEASE_NAME, TEST_FEEDBACK_TEXT);
    verify(mockTesterApiClient).attachScreenshot(TEST_FEEDBACK_NAME, TEST_SCREENSHOT);
    verify(mockTesterApiClient).commitFeedback(TEST_FEEDBACK_NAME);
  }

  @Test
  public void sendFeedback_createFeedbackFails_failsTask() {
    FirebaseAppDistributionException cause =
        new FirebaseAppDistributionException("test ex", Status.AUTHENTICATION_FAILURE);
    when(mockTesterApiClient.createFeedback(TEST_RELEASE_NAME, TEST_FEEDBACK_TEXT))
        .thenReturn(Tasks.forException(cause));

    Task<Void> task =
        feedbackSender.sendFeedback(TEST_RELEASE_NAME, TEST_FEEDBACK_TEXT, TEST_SCREENSHOT);

    TestUtils.awaitTaskFailure(task, Status.AUTHENTICATION_FAILURE, "test ex");
  }

  @Test
  public void sendFeedback_attachScreenshotFails_failsTask() {
    when(mockTesterApiClient.createFeedback(TEST_RELEASE_NAME, TEST_FEEDBACK_TEXT))
        .thenReturn(Tasks.forResult(TEST_FEEDBACK_NAME));
    FirebaseAppDistributionException cause =
        new FirebaseAppDistributionException("test ex", Status.AUTHENTICATION_FAILURE);
    when(mockTesterApiClient.attachScreenshot(TEST_FEEDBACK_NAME, TEST_SCREENSHOT))
        .thenReturn(Tasks.forException(cause));

    Task<Void> task =
        feedbackSender.sendFeedback(TEST_RELEASE_NAME, TEST_FEEDBACK_TEXT, TEST_SCREENSHOT);

    TestUtils.awaitTaskFailure(task, Status.AUTHENTICATION_FAILURE, "test ex");
  }

  @Test
  public void sendFeedback_commitFeedbackFails_failsTask() {
    when(mockTesterApiClient.createFeedback(TEST_RELEASE_NAME, TEST_FEEDBACK_TEXT))
        .thenReturn(Tasks.forResult(TEST_FEEDBACK_NAME));
    when(mockTesterApiClient.attachScreenshot(TEST_FEEDBACK_NAME, TEST_SCREENSHOT))
        .thenReturn(Tasks.forResult(TEST_FEEDBACK_NAME));
    FirebaseAppDistributionException cause =
        new FirebaseAppDistributionException("test ex", Status.AUTHENTICATION_FAILURE);
    when(mockTesterApiClient.commitFeedback(TEST_FEEDBACK_NAME))
        .thenReturn(Tasks.forException(cause));

    Task<Void> task =
        feedbackSender.sendFeedback(TEST_RELEASE_NAME, TEST_FEEDBACK_TEXT, TEST_SCREENSHOT);

    TestUtils.awaitTaskFailure(task, Status.AUTHENTICATION_FAILURE, "test ex");
  }
}
