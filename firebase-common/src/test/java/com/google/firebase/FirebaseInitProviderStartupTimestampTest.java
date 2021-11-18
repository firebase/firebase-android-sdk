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

package com.google.firebase;

import static com.google.common.truth.Truth.assertThat;

import android.content.pm.ProviderInfo;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.firebase.provider.FirebaseInitProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@RunWith(AndroidJUnit4.class)
@Config(qualifiers = "desk")
public class FirebaseInitProviderStartupTimestampTest {
  private final FirebaseInitProvider provider = new FirebaseInitProvider();

  @Test
  public void firebaseOptions_whenLoadedFromInitProvider_shouldIncludeTimestamps() {
    ProviderInfo providerInfo = new ProviderInfo();
    providerInfo.authority = "com.google.android.gms.tests.common.firebaseinitprovider";

    provider.attachInfo(ApplicationProvider.getApplicationContext(), providerInfo);
    FirebaseOptions options = FirebaseApp.getInstance().getOptions();
    assertThat(options.isTracingEnabled()).isTrue();
    assertThat(options.startupTime.isValid()).isTrue();
    assertThat(options.startupTime).isEqualTo(FirebaseInitProvider.STARTUP_TIME);
    assertThat(options.loadStartTime.isValid()).isTrue();
    assertThat(options.loadEndTime.isValid()).isTrue();
  }

  @Test
  public void firebaseOptions_whenLoadedNotFromInitProvider_shouldNotIncludeTimestamps() {
    FirebaseOptions options =
        FirebaseOptions.fromResource(ApplicationProvider.getApplicationContext());
    assertThat(options.isTracingEnabled()).isFalse();
    assertThat(options.startupTime.isValid()).isFalse();
    assertThat(options.startupTime).isNotEqualTo(FirebaseInitProvider.STARTUP_TIME);
    assertThat(options.loadStartTime.isValid()).isFalse();
    assertThat(options.loadEndTime.isValid()).isFalse();
  }

  @Test
  public void firebaseOptions_whenCreatedViaBuilder_shouldNotIncludeTimestamps() {
    FirebaseOptions options =
        new FirebaseOptions.Builder().setApplicationId("123").setApiKey("key").build();
    assertThat(options.isTracingEnabled()).isFalse();
    assertThat(options.startupTime.isValid()).isFalse();
    assertThat(options.startupTime).isNotEqualTo(FirebaseInitProvider.STARTUP_TIME);
    assertThat(options.loadStartTime.isValid()).isFalse();
    assertThat(options.loadEndTime.isValid()).isFalse();
  }
}
