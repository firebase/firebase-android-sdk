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

import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class FirebaseSegmentationInstrumentedTest {

  private FirebaseApp firebaseApp;

  @Before
  public void setUp() {
    FirebaseApp.clearInstancesForTest();
    firebaseApp =
        FirebaseApp.initializeApp(
            InstrumentationRegistry.getContext(),
            new FirebaseOptions.Builder().setApplicationId("1:123456789:android:abcdef").build());
  }

  @Test
  public void useAppContext() {
    assertNotNull(FirebaseSegmentation.getInstance().setCustomInstallationId("123123").getResult());
  }
}
