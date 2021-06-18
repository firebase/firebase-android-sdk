// Copyright 2021 Google LLC
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

package com.google.firebase.dynamiclinks;

import static junit.framework.Assert.assertNotNull;

import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class FirebaseDynamicLinksTest {

  @Before
  public void setUp() {
    FirebaseOptions.Builder firebaseOptionsBuilder =
        new FirebaseOptions.Builder().setApplicationId("application_id").setApiKey("api_key");
    FirebaseApp.initializeApp(
        ApplicationProvider.getApplicationContext(), firebaseOptionsBuilder.build());
  }

  @Test
  @Ignore
  public void testFirebaseDynamicLinks_GetInstance() {
    FirebaseDynamicLinks firebaseDynamicLinks = FirebaseDynamicLinks.getInstance();
    assertNotNull(firebaseDynamicLinks);
  }
}
