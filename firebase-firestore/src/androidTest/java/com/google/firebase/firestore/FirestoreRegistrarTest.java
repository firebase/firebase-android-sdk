/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.firestore;

import static com.google.common.truth.Truth.assertThat;

import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.platforminfo.UserAgentPublisher;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class FirestoreRegistrarTest {
  @Test
  public void getComponents_publishesLibVersionComponent() {
    FirebaseApp app = FirebaseFirestore.getInstance().getApp();
    TestUserAgentDependentComponent userAgentDependant =
        FirebaseFirestore.getInstance(app).getApp().get(TestUserAgentDependentComponent.class);

    UserAgentPublisher userAgentPublisher = userAgentDependant.getUserAgentPublisher();
    String actualUserAgent = userAgentPublisher.getUserAgent();

    assertThat(actualUserAgent).contains("firebase-firestore");
  }
}
