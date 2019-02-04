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

import org.robolectric.RobolectricTestRunner;

@org.junit.runner.RunWith(RobolectricTestRunner.class) public class FirestoreRegistrarTest {
  //TODO(rgowman:b/123870630): Enable test.
  //@Test
  //public void storageRegistrar_getComponents_publishesLibVersionComponent() {
  //  FirebaseApp app =
  //      FirebaseApp.initializeApp(
  //          RuntimeEnvironment.application.getApplicationContext(),
  //          new FirebaseOptions.Builder()
  //              .setProjectId("projectId")
  //              .setApplicationId("1:196403931065:android:60949756fbe381ea")
  //              .build());
  //  TestUserAgentDependentComponent userAgentDependant =
  //      FirebaseFirestore.getInstance(app).getApp().get(TestUserAgentDependentComponent.class);
  //
  //  UserAgentPublisher userAgentPublisher = userAgentDependant.getUserAgentPublisher();
  //  String actualUserAgent = userAgentPublisher.getUserAgent();
  //
  //  assertThat(actualUserAgent).contains("firebase-firestore");
  //}
}
