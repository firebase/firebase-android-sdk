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

import static com.google.firebase.appdistribution.impl.FeedbackSender.CONTENT_TYPE_JPEG;
import static com.google.firebase.appdistribution.impl.FeedbackSender.CONTENT_TYPE_PNG;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
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
  private static final String TEST_FILENAME_PNG = "test.png";
  private static final Uri TEST_SCREENSHOT_URI_PNG = uriForFilename(TEST_FILENAME_PNG);

  @Lightweight private final Executor lightweightExecutor = TestOnlyExecutors.lite();

  @Mock private FirebaseAppDistributionTesterApiClient mockTesterApiClient;
  @Mock private ContentResolver mockContentResolver;
  @Mock private Cursor mockCursor;

  private FeedbackSender feedbackSender;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    feedbackSender =
        new FeedbackSender(mockContentResolver, mockTesterApiClient, lightweightExecutor);

    when(mockTesterApiClient.createFeedback(TEST_RELEASE_NAME, TEST_FEEDBACK_TEXT))
        .thenReturn(Tasks.forResult(TEST_FEEDBACK_NAME));
    when(mockTesterApiClient.attachScreenshot(eq(TEST_FEEDBACK_NAME), any(), any(), any()))
        .thenReturn(Tasks.forResult(TEST_FEEDBACK_NAME));
    when(mockTesterApiClient.commitFeedback(TEST_FEEDBACK_NAME, FeedbackTrigger.CUSTOM))
        .thenReturn(Tasks.forResult(null));
  }

  @Test
  public void sendFeedbackPng() throws Exception {
    Task<Void> task =
        feedbackSender.sendFeedback(
            TEST_RELEASE_NAME, TEST_FEEDBACK_TEXT, TEST_SCREENSHOT_URI_PNG, FeedbackTrigger.CUSTOM);
    TestUtils.awaitTask(task);

    verifyTesterApiCalls(TEST_SCREENSHOT_URI_PNG, TEST_FILENAME_PNG, CONTENT_TYPE_PNG);
  }

  @Test
  public void sendFeedbackJpg() throws Exception {
    String jpegFilename = "test.jpg"; // jpg, not jpeg
    Uri jpegUri = uriForFilename(jpegFilename);

    Task<Void> task =
        feedbackSender.sendFeedback(
            TEST_RELEASE_NAME, TEST_FEEDBACK_TEXT, jpegUri, FeedbackTrigger.CUSTOM);
    TestUtils.awaitTask(task);

    verifyTesterApiCalls(jpegUri, jpegFilename, CONTENT_TYPE_JPEG);
  }

  @Test
  public void sendFeedbackJpeg() throws Exception {
    String jpegFilename = "test.jpeg"; // jpeg, not jpg
    Uri jpegUri = uriForFilename(jpegFilename);

    Task<Void> task =
        feedbackSender.sendFeedback(
            TEST_RELEASE_NAME, TEST_FEEDBACK_TEXT, jpegUri, FeedbackTrigger.CUSTOM);
    TestUtils.awaitTask(task);

    verifyTesterApiCalls(jpegUri, jpegFilename, CONTENT_TYPE_JPEG);
  }

  @Test
  public void sendFeedbackContentPng() throws Exception {
    Uri contentUri = Uri.parse("content://some/path");
    String filename = "test.png";
    when(mockContentResolver.query(eq(contentUri), any(), any(), any(), any()))
        .thenReturn(mockCursor);
    when(mockContentResolver.getType(contentUri)).thenReturn(CONTENT_TYPE_PNG);
    when(mockCursor.getString(anyInt())).thenReturn(filename);

    Task<Void> task =
        feedbackSender.sendFeedback(
            TEST_RELEASE_NAME, TEST_FEEDBACK_TEXT, contentUri, FeedbackTrigger.CUSTOM);
    TestUtils.awaitTask(task);

    verifyTesterApiCalls(contentUri, filename, CONTENT_TYPE_PNG);
  }

  @Test
  public void sendFeedbackContentJpeg() throws Exception {
    Uri contentUri = Uri.parse("content://some/path");
    String filename = "test.jpeg";
    when(mockContentResolver.query(eq(contentUri), any(), any(), any(), any()))
        .thenReturn(mockCursor);
    when(mockContentResolver.getType(contentUri)).thenReturn(CONTENT_TYPE_JPEG);
    when(mockCursor.getString(anyInt())).thenReturn(filename);

    Task<Void> task =
        feedbackSender.sendFeedback(
            TEST_RELEASE_NAME, TEST_FEEDBACK_TEXT, contentUri, FeedbackTrigger.CUSTOM);
    TestUtils.awaitTask(task);

    verifyTesterApiCalls(contentUri, filename, CONTENT_TYPE_JPEG);
  }

  @Test
  public void sendFeedbackContentPngNoFilename() throws Exception {
    Uri contentUri = Uri.parse("content://some/path");
    when(mockContentResolver.query(eq(contentUri), any(), any(), any(), any())).thenReturn(null);
    when(mockContentResolver.getType(contentUri)).thenReturn(CONTENT_TYPE_PNG);

    Task<Void> task =
        feedbackSender.sendFeedback(
            TEST_RELEASE_NAME, TEST_FEEDBACK_TEXT, contentUri, FeedbackTrigger.CUSTOM);
    TestUtils.awaitTask(task);

    verifyTesterApiCalls(contentUri, "screenshot.png", CONTENT_TYPE_PNG);
  }

  @Test
  public void sendFeedbackContentJpgNoFilename() throws Exception {
    Uri contentUri = Uri.parse("content://some/path");
    when(mockContentResolver.query(eq(contentUri), any(), any(), any(), any())).thenReturn(null);
    when(mockContentResolver.getType(contentUri)).thenReturn(CONTENT_TYPE_JPEG);

    Task<Void> task =
        feedbackSender.sendFeedback(
            TEST_RELEASE_NAME, TEST_FEEDBACK_TEXT, contentUri, FeedbackTrigger.CUSTOM);
    TestUtils.awaitTask(task);

    verifyTesterApiCalls(contentUri, "screenshot.jpg", CONTENT_TYPE_JPEG);
  }

  @Test
  public void sendFeedbackContentNullContentType_fallsBackToPng() throws Exception {
    Uri contentUri = Uri.parse("content://some/path");
    String filename = "test.foo";
    when(mockContentResolver.query(eq(contentUri), any(), any(), any(), any()))
        .thenReturn(mockCursor);
    when(mockContentResolver.getType(contentUri)).thenReturn(null);
    when(mockCursor.getString(anyInt())).thenReturn(filename);

    Task<Void> task =
        feedbackSender.sendFeedback(
            TEST_RELEASE_NAME, TEST_FEEDBACK_TEXT, contentUri, FeedbackTrigger.CUSTOM);
    TestUtils.awaitTask(task);

    verifyTesterApiCalls(contentUri, filename, CONTENT_TYPE_PNG);
  }

  @Test
  public void sendFeedbackContentUnknown_fallsBackToPng() throws Exception {
    Uri contentUri = Uri.parse("content://some/path");
    String filename = "test.foo";
    when(mockContentResolver.query(eq(contentUri), any(), any(), any(), any()))
        .thenReturn(mockCursor);
    when(mockContentResolver.getType(contentUri)).thenReturn(null);
    when(mockCursor.getString(anyInt())).thenReturn(filename);

    Task<Void> task =
        feedbackSender.sendFeedback(
            TEST_RELEASE_NAME, TEST_FEEDBACK_TEXT, contentUri, FeedbackTrigger.CUSTOM);
    TestUtils.awaitTask(task);

    verifyTesterApiCalls(contentUri, filename, CONTENT_TYPE_PNG);
  }

  @Test
  public void sendFeedback_withoutScreenshot_success() throws Exception {
    Task<Void> task =
        feedbackSender.sendFeedback(
            TEST_RELEASE_NAME, TEST_FEEDBACK_TEXT, /* screenshot= */ null, FeedbackTrigger.CUSTOM);
    TestUtils.awaitTask(task);

    verify(mockTesterApiClient).createFeedback(TEST_RELEASE_NAME, TEST_FEEDBACK_TEXT);
    verify(mockTesterApiClient).commitFeedback(TEST_FEEDBACK_NAME, FeedbackTrigger.CUSTOM);
    verify(mockTesterApiClient, never()).attachScreenshot(any(), any(), any(), any());
  }

  @Test
  public void sendFeedback_createFeedbackFails_failsTask() {
    FirebaseAppDistributionException cause =
        new FirebaseAppDistributionException("test ex", Status.AUTHENTICATION_FAILURE);
    when(mockTesterApiClient.createFeedback(TEST_RELEASE_NAME, TEST_FEEDBACK_TEXT))
        .thenReturn(Tasks.forException(cause));

    Task<Void> task =
        feedbackSender.sendFeedback(
            TEST_RELEASE_NAME, TEST_FEEDBACK_TEXT, TEST_SCREENSHOT_URI_PNG, FeedbackTrigger.CUSTOM);

    TestUtils.awaitTaskFailure(task, Status.AUTHENTICATION_FAILURE, "test ex");
  }

  @Test
  public void sendFeedback_attachScreenshotFails_failsTask() {
    FirebaseAppDistributionException cause =
        new FirebaseAppDistributionException("test ex", Status.AUTHENTICATION_FAILURE);
    when(mockTesterApiClient.attachScreenshot(
            TEST_FEEDBACK_NAME, TEST_SCREENSHOT_URI_PNG, TEST_FILENAME_PNG, CONTENT_TYPE_PNG))
        .thenReturn(Tasks.forException(cause));

    Task<Void> task =
        feedbackSender.sendFeedback(
            TEST_RELEASE_NAME, TEST_FEEDBACK_TEXT, TEST_SCREENSHOT_URI_PNG, FeedbackTrigger.CUSTOM);

    TestUtils.awaitTaskFailure(task, Status.AUTHENTICATION_FAILURE, "test ex");
  }

  @Test
  public void sendFeedback_commitFeedbackFails_failsTask() {
    FirebaseAppDistributionException cause =
        new FirebaseAppDistributionException("test ex", Status.AUTHENTICATION_FAILURE);
    when(mockTesterApiClient.commitFeedback(TEST_FEEDBACK_NAME, FeedbackTrigger.CUSTOM))
        .thenReturn(Tasks.forException(cause));

    Task<Void> task =
        feedbackSender.sendFeedback(
            TEST_RELEASE_NAME, TEST_FEEDBACK_TEXT, TEST_SCREENSHOT_URI_PNG, FeedbackTrigger.CUSTOM);

    TestUtils.awaitTaskFailure(task, Status.AUTHENTICATION_FAILURE, "test ex");
  }

  private void verifyTesterApiCalls(Uri uri, String filename, String contentType) {
    verify(mockTesterApiClient).createFeedback(TEST_RELEASE_NAME, TEST_FEEDBACK_TEXT);
    verify(mockTesterApiClient).attachScreenshot(TEST_FEEDBACK_NAME, uri, filename, contentType);
    verify(mockTesterApiClient).commitFeedback(TEST_FEEDBACK_NAME, FeedbackTrigger.CUSTOM);
  }

  private static Uri uriForFilename(String filename) {
    return Uri.fromFile(ApplicationProvider.getApplicationContext().getFileStreamPath(filename));
  }
}
