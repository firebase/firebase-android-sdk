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

package com.google.firebase.ml.modeldownloader;

import static org.junit.Assert.assertThrows;

import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class FirebaseModelDownloaderTest {

  public static final String TEST_PROJECT_ID = "777777777777";
  public static final String MODEL_NAME = "MODEL_NAME_1";
  public static final CustomModelDownloadConditions DEFAULT_DOWNLOAD_CONDITIONS =
      new CustomModelDownloadConditions.Builder().build();

  @Before
  public void setUp() {
    FirebaseApp.clearInstancesForTest();
    // default app
    FirebaseApp.initializeApp(
        ApplicationProvider.getApplicationContext(),
        new FirebaseOptions.Builder()
            .setApplicationId("1:123456789:android:abcdef")
            .setProjectId(TEST_PROJECT_ID)
            .build());
  }

  @Test
  public void getModel_unimplemented() {
    assertThrows(
        UnsupportedOperationException.class,
        () ->
            FirebaseModelDownloader.getInstance()
                .getModel(MODEL_NAME, DownloadType.LOCAL_MODEL, DEFAULT_DOWNLOAD_CONDITIONS));
  }

  @Test
  public void listDownloadedModels_unimplemented() {
    assertThrows(
        UnsupportedOperationException.class,
        () -> FirebaseModelDownloader.getInstance().listDownloadedModels());
  }

  @Test
  public void deleteDownloadedModel_unimplemented() {
    assertThrows(
        UnsupportedOperationException.class,
        () -> FirebaseModelDownloader.getInstance().deleteDownloadedModel(MODEL_NAME));
  }
}
