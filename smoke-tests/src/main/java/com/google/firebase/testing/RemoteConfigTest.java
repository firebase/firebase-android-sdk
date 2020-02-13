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
import static com.google.firebase.testing.common.Tasks2.waitForSuccess;

import android.app.Activity;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class RemoteConfigTest {

  private static final String MODEL_NAME = "Acura/Honda+Mercedes-Benz";
  private static final boolean COLOR_IS_RED = true;

  @Rule public final ActivityTestRule<Activity> activity = new ActivityTestRule<>(Activity.class);

  @Before
  public void prepareRemoteConfig() throws Exception {
    FirebaseRemoteConfig frc = FirebaseRemoteConfig.getInstance();

    waitForSuccess(frc.fetch());
    waitForSuccess(frc.activate());
  }

  @Test
  public void getBooleanConvertsValueSetInConsole() {
    FirebaseRemoteConfig frc = FirebaseRemoteConfig.getInstance();

    assertThat(frc.getBoolean("COLOR_IS_RED")).isEqualTo(COLOR_IS_RED);
  }

  @Test
  public void getStringReturnsValueSetInConsole() {
    FirebaseRemoteConfig frc = FirebaseRemoteConfig.getInstance();

    assertThat(frc.getString("MODEL_NAME")).isEqualTo(MODEL_NAME);
  }

  @Test
  public void getValueNotSetInConsoleYieldsStaticSource() {
    FirebaseRemoteConfig frc = FirebaseRemoteConfig.getInstance();
    FirebaseRemoteConfigValue value = frc.getValue("GOOBER_GOOBER");

    assertThat(value.getSource()).isEqualTo(FirebaseRemoteConfig.VALUE_SOURCE_STATIC);
  }
}
