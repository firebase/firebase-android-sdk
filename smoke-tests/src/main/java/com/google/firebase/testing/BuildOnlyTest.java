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

import com.google.firebase.inappmessaging.FirebaseInAppMessaging;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.ml.modeldownloader.FirebaseModelDownloader;
import com.google.firebase.perf.FirebasePerformance;
import com.google.firebase.appdistribution.FirebaseAppDistribution;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

/** Contains initialization test cases for build-only libraries. */
@RunWith(JUnit4.class)
public final class BuildOnlyTest {

  @Test
  public void inappmessaging_IsNotNull() {
    assertThat(FirebaseInAppMessaging.getInstance()).isNotNull();
  }

 @Test
 public void messaging_IsNotNull() {
   assertThat(FirebaseMessaging.getInstance()).isNotNull();
 }

  @Test
  public void modelDownloader_IsNotNull() {
    assertThat(FirebaseModelDownloader.getInstance()).isNotNull();
  }

  @Test
  public void performance_IsNotNull() {
    assertThat(FirebasePerformance.getInstance()).isNotNull();
  }

  @Test
  public void appDistribution_IsNotNull(){
    assertThat(FirebaseAppDistribution.getInstance()).isNotNull();
  }
}
