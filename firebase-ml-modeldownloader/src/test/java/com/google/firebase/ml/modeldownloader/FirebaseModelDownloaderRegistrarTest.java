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

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class FirebaseModelDownloaderRegistrarTest {

  public static final String TEST_PROJECT_ID = "777777777777";

  @Before
  public void setUp() {
    FirebaseApp.clearInstancesForTest();
  }

  @Test
  public void getModelDownloaderInstance() {
    // default app
    FirebaseApp.initializeApp(
        ApplicationProvider.getApplicationContext(),
        new FirebaseOptions.Builder()
            .setApplicationId("1:123456789:android:abcdef")
            .setProjectId(TEST_PROJECT_ID)
            .build());

    FirebaseApp anotherApp =
        FirebaseApp.initializeApp(
            ApplicationProvider.getApplicationContext(),
            new FirebaseOptions.Builder()
                .setApplicationId("1:987654321:android:abcdef")
                .setProjectId(TEST_PROJECT_ID)
                .build(),
            "firebase_app_1");

    FirebaseModelDownloader defaultDownloader = FirebaseModelDownloader.getInstance();
    assertNotNull(defaultDownloader);

    FirebaseModelDownloader anotherDownloader = FirebaseModelDownloader.getInstance(anotherApp);
    assertNotNull(anotherDownloader);

    assertNotEquals(defaultDownloader.getApplicationId(), anotherDownloader.getApplicationId());
  }
}
