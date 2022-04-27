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

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Tests of {@link CustomModelDownloadConditions} */
@RunWith(RobolectricTestRunner.class)
public class CustomModelDownloadConditionsTest {

  @Test
  public void testDefaultBuilder() {
    CustomModelDownloadConditions conditions = new CustomModelDownloadConditions.Builder().build();
    assertThat(conditions.isChargingRequired()).isFalse();
    assertThat(conditions.isWifiRequired()).isFalse();
    assertThat(conditions.isDeviceIdleRequired()).isFalse();
  }

  @Test
  public void testConfigConditions() {
    CustomModelDownloadConditions conditions =
        new CustomModelDownloadConditions.Builder().requireCharging().requireWifi().build();
    assertThat(conditions.isChargingRequired()).isTrue();
    assertThat(conditions.isWifiRequired()).isTrue();
    assertThat(conditions.isDeviceIdleRequired()).isFalse();
  }

  @Test
  public void testTwoDefaultConditionsSame() {
    CustomModelDownloadConditions conditions1 = new CustomModelDownloadConditions.Builder().build();
    CustomModelDownloadConditions conditions2 = new CustomModelDownloadConditions.Builder().build();
    assertThat(conditions1).isEqualTo(conditions2);
  }

  @Test
  public void testTwoConfiguredConditionsSame() {
    CustomModelDownloadConditions conditions1 =
        new CustomModelDownloadConditions.Builder().requireDeviceIdle().requireCharging().build();
    CustomModelDownloadConditions conditions2 =
        new CustomModelDownloadConditions.Builder().requireCharging().requireDeviceIdle().build();
    assertThat(conditions1).isEqualTo(conditions2);
  }

  @Test
  public void testTwoConfiguredConditionsDifferent() {
    CustomModelDownloadConditions conditions1 =
        new CustomModelDownloadConditions.Builder().requireCharging().build();
    CustomModelDownloadConditions conditions2 =
        new CustomModelDownloadConditions.Builder().requireCharging().requireDeviceIdle().build();
    assertThat(conditions1).isNotEqualTo(conditions2);
  }
}
