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

import static android.os.Looper.getMainLooper;
import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.appdistribution.impl.ScreenshotTaker.SCREENSHOT_FILE_NAME;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.robolectric.Shadows.shadowOf;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.annotations.concurrent.Background;
import com.google.firebase.concurrent.TestOnlyExecutors;
import java.io.File;
import java.util.concurrent.Executor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class ScreenshotTakerTest {
  private static final String TEST_API_KEY = "AIzaSyabcdefghijklmnopqrstuvwxyz1234567";
  private static final String TEST_APP_ID_1 = "1:123456789:android:abcdef";
  private static final String TEST_PROJECT_ID = "project-id";
  private static final String TEST_PROJECT_NUMBER = "123456789";
  private static final Bitmap TEST_SCREENSHOT = Bitmap.createBitmap(400, 400, Config.RGB_565);

  @Background private static final Executor backgroundExecutor = TestOnlyExecutors.background();

  private FirebaseApp firebaseApp;
  @Mock private FirebaseAppDistributionLifecycleNotifier mockLifecycleNotifier;
  private ScreenshotTaker screenshotTaker;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    FirebaseApp.clearInstancesForTest();

    firebaseApp =
        FirebaseApp.initializeApp(
            ApplicationProvider.getApplicationContext(),
            new FirebaseOptions.Builder()
                .setApplicationId(TEST_APP_ID_1)
                .setProjectId(TEST_PROJECT_ID)
                .setGcmSenderId(TEST_PROJECT_NUMBER)
                .setApiKey(TEST_API_KEY)
                .build());

    screenshotTaker =
        spy(
            new ScreenshotTaker(
                firebaseApp.getApplicationContext(), mockLifecycleNotifier, backgroundExecutor));

    // Taking a screenshot of an actual activity would require an instrumentation test
    doReturn(Tasks.forResult(TEST_SCREENSHOT)).when(screenshotTaker).captureScreenshot();
  }

  @After
  public void tearDown() {
    ApplicationProvider.getApplicationContext().deleteFile(SCREENSHOT_FILE_NAME);
  }

  @Test
  public void takeAndDeleteScreenshot_success() throws Exception {
    // Take a screenshot
    Task<Uri> task = screenshotTaker.takeScreenshot();
    shadowOf(getMainLooper()).idle();
    Uri screenshotUri = TestUtils.awaitTask(task);

    File expectedFile =
        ApplicationProvider.getApplicationContext().getFileStreamPath(SCREENSHOT_FILE_NAME);
    assertThat(screenshotUri).isEqualTo(Uri.fromFile(expectedFile));
    assertThat(expectedFile.length()).isGreaterThan(0);

    // Delete the screenshot
    screenshotTaker.deleteScreenshot();

    assertThat(
            ApplicationProvider.getApplicationContext()
                .getFileStreamPath(SCREENSHOT_FILE_NAME)
                .exists())
        .isFalse();
  }
}
