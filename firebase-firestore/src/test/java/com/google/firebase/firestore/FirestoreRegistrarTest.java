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

import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.platforminfo.UserAgentPublisher;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class FirestoreRegistrarTest {
  // TODO(rgowman:b/123870630): Enable test.
  @Ignore
  @Test
  public void storageRegistrar_getComponents_publishesLibVersionComponent() {
    FirebaseApp app =
        FirebaseApp.initializeApp(
            ApplicationProvider.getApplicationContext(),
            new FirebaseOptions.Builder()
                .setProjectId("projectId")
                .setApplicationId("1:196403931065:android:60949756fbe381ea")
                .build());
    UserAgentPublisher userAgentPublisher = app.get(UserAgentPublisher.class);
    String actualUserAgent = userAgentPublisher.getUserAgent();

    assertThat(actualUserAgent).contains("fire-fst");
  }
}
