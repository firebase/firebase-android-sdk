// Copyright 2019 Google LLC
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

package com.google.firebase.segmentation;

import static org.junit.Assert.assertNotNull;

import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class FirebaseSegmentationRegistrarTest {

  @Before
  public void setUp() {
    FirebaseApp.clearInstancesForTest();
  }

  // TODO(rgowman:b/123870630): Enable test.
  @Ignore
  @Test
  public void getFirebaseInstallationsInstance() {
    FirebaseApp defaultApp =
        FirebaseApp.initializeApp(
            ApplicationProvider.getApplicationContext(),
            new FirebaseOptions.Builder().setApplicationId("1:123456789:android:abcdef").build());

    FirebaseApp anotherApp =
        FirebaseApp.initializeApp(
            ApplicationProvider.getApplicationContext(),
            new FirebaseOptions.Builder().setApplicationId("1:987654321:android:abcdef").build(),
            "firebase_app_1");

    FirebaseSegmentation defaultSegmentation = FirebaseSegmentation.getInstance();
    assertNotNull(defaultSegmentation);

    FirebaseSegmentation anotherSegmentation = FirebaseSegmentation.getInstance(anotherApp);
    assertNotNull(anotherSegmentation);
  }
}
