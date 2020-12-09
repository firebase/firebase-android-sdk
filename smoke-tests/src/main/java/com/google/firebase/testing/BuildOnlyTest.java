// Copyright 2018 Google LLC
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

package com.google.firebase.testing;

import static com.google.common.truth.Truth.assertThat;

import com.google.firebase.appindexing.FirebaseAppIndex;
import com.google.firebase.inappmessaging.FirebaseInAppMessaging;
// import com.google.firebase.messaging.FirebaseMessaging;
// import com.google.firebase.perf.FirebasePerformance;
import com.google.firebase.ml.vision.FirebaseVision;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Contains initialization test cases for build-only libraries. */
@RunWith(JUnit4.class)
public final class BuildOnlyTest {

  @Test
  public void appindexing_IsNotNull() {
    assertThat(FirebaseAppIndex.getInstance()).isNotNull();
  }

  @Test
  public void inappmessaging_IsNotNull() {
    assertThat(FirebaseInAppMessaging.getInstance()).isNotNull();
  }

//  @Test
//  public void messaging_IsNotNull() {
//    assertThat(FirebaseMessaging.getInstance()).isNotNull();
//  }

//  @Test
//  public void performance_IsNotNull() {
//    assertThat(FirebasePerformance.getInstance()).isNotNull();
//  }

  @Test
  public void vision_IsNotNull() {
    assertThat(FirebaseVision.getInstance()).isNotNull();
  }
}
