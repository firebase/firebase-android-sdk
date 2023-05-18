// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.perf.config;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.firebase.FirebaseApp;
import com.google.firebase.perf.FirebasePerformanceTestBase;
import com.google.firebase.perf.util.Constants;
import com.google.testing.timing.FakeScheduledExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for {@link DeviceCacheManager}. */
@RunWith(RobolectricTestRunner.class)
public final class DeviceCacheManagerTest extends FirebasePerformanceTestBase {

  private DeviceCacheManager deviceCacheManager;
  private FakeScheduledExecutorService fakeScheduledExecutorService;

  @Before
  public void setUp() {
    fakeScheduledExecutorService = new FakeScheduledExecutorService();
    deviceCacheManager = new DeviceCacheManager(fakeScheduledExecutorService);
  }

  @Test
  public void getBoolean_valueIsNotSet_returnsEmpty() {
    deviceCacheManager.setContext(appContext);
    fakeScheduledExecutorService.runAll();

    assertThat(deviceCacheManager.getBoolean("some_key").isAvailable()).isFalse();
  }

  @Test
  public void getBoolean_contextAndValueNotSet_returnsEmpty() {
    assertThat(fakeScheduledExecutorService.isEmpty()).isTrue();
    assertThat(deviceCacheManager.getBoolean("some_key").isAvailable()).isFalse();
  }

  @Test
  public void getBoolean_valueIsSet_returnsSetValue() {
    deviceCacheManager.setContext(appContext);
    fakeScheduledExecutorService.runAll();
    deviceCacheManager.setValue("some_key", true);

    assertThat(deviceCacheManager.getBoolean("some_key").get()).isTrue();
  }

  @Test
  public void clear_setBooleanThenCleared_returnsEmpty() {
    deviceCacheManager.setContext(appContext);
    fakeScheduledExecutorService.runAll();
    deviceCacheManager.setValue("some_key", true);

    assertThat(deviceCacheManager.getBoolean("some_key").get()).isTrue();

    deviceCacheManager.clear("some_key");
    assertThat(deviceCacheManager.getBoolean("some_key").isAvailable()).isFalse();
  }

  @Test
  public void getBoolean_firebaseAppNotExist_returnsEmpty() {
    DeviceCacheManager.clearInstance();
    FirebaseApp.clearInstancesForTest();
    deviceCacheManager = new DeviceCacheManager(fakeScheduledExecutorService);
    deviceCacheManager.setValue("some_key", true);

    assertThat(fakeScheduledExecutorService.isEmpty()).isTrue();
    assertThat(deviceCacheManager.getBoolean("some_key").isAvailable()).isFalse();
  }

  @Test
  public void setValueBoolean_setTwice_canGetLatestValue() {
    deviceCacheManager.setContext(appContext);
    fakeScheduledExecutorService.runAll();
    deviceCacheManager.setValue("some_key", true);
    assertThat(deviceCacheManager.getBoolean("some_key").get()).isTrue();

    deviceCacheManager.setValue("some_key", false);
    assertThat(deviceCacheManager.getBoolean("some_key").get()).isFalse();
  }

  @Test
  public void setValueBoolean_contextNotSet_returnsEmpty() {
    deviceCacheManager.setValue("some_key", true);

    assertThat(deviceCacheManager.getBoolean("some_key").isAvailable()).isFalse();
  }

  @Test
  public void setValueBoolean_keyIsNull_returnsFalse() {
    assertThat(deviceCacheManager.setValue(null, true)).isFalse();
  }

  @Test
  public void getString_valueIsNotSet_returnsEmpty() {
    deviceCacheManager.setContext(appContext);
    fakeScheduledExecutorService.runAll();

    assertThat(deviceCacheManager.getString("some_key").isAvailable()).isFalse();
  }

  @Test
  public void getString_contextAndValueNotSet_returnsEmpty() {
    assertThat(deviceCacheManager.getString("some_key").isAvailable()).isFalse();
  }

  @Test
  public void getString_valueIsSet_returnsSetValue() {
    deviceCacheManager.setContext(appContext);
    fakeScheduledExecutorService.runAll();
    deviceCacheManager.setValue("some_key", "specialValue");

    assertThat(deviceCacheManager.getString("some_key").get()).isEqualTo("specialValue");
  }

  @Test
  public void getString_firebaseAppNotExist_returnsEmpty() {
    DeviceCacheManager.clearInstance();
    FirebaseApp.clearInstancesForTest();
    deviceCacheManager = new DeviceCacheManager(fakeScheduledExecutorService);
    deviceCacheManager.setValue("some_key", "specialValue");

    assertThat(deviceCacheManager.getString("some_key").isAvailable()).isFalse();
  }

  @Test
  public void setValueString_setTwice_canGetLatestValue() {
    deviceCacheManager.setContext(appContext);
    fakeScheduledExecutorService.runAll();
    deviceCacheManager.setValue("some_key", "EarliestValue");
    assertThat(deviceCacheManager.getString("some_key").get()).isEqualTo("EarliestValue");

    deviceCacheManager.setValue("some_key", "latestValue");
    assertThat(deviceCacheManager.getString("some_key").get()).isEqualTo("latestValue");
  }

  @Test
  public void setValueString_contextNotSet_returnsEmpty() {
    deviceCacheManager.setValue("some_key", "newValue");
    assertThat(deviceCacheManager.getString("some_key").isAvailable()).isFalse();
  }

  @Test
  public void setValueString_setNullString_returnsEmpty() {
    deviceCacheManager.setContext(appContext);
    fakeScheduledExecutorService.runAll();
    deviceCacheManager.setValue("some_key", null);
    assertThat(deviceCacheManager.getString("some_key").isAvailable()).isFalse();
  }

  @Test
  public void setValueString_keyIsNull_returnsFalse() {
    deviceCacheManager.setContext(appContext);
    fakeScheduledExecutorService.runAll();
    assertThat(deviceCacheManager.setValue(null, "value")).isFalse();
  }

  @Test
  public void getDouble_valueIsNotSet_returnsEmpty() {
    deviceCacheManager.setContext(appContext);
    fakeScheduledExecutorService.runAll();

    assertThat(deviceCacheManager.getDouble("some_key").isAvailable()).isFalse();
  }

  @Test
  public void getDouble_contextAndValueNotSet_returnsEmpty() {
    DeviceCacheManager.clearInstance();
    deviceCacheManager = new DeviceCacheManager(fakeScheduledExecutorService);

    assertThat(fakeScheduledExecutorService.isEmpty()).isTrue();
    assertThat(deviceCacheManager.getDouble("some_key").isAvailable()).isFalse();
  }

  @Test
  public void getDouble_valueIsSet_returnsSetValue() {
    deviceCacheManager.setContext(appContext);
    fakeScheduledExecutorService.runAll();
    deviceCacheManager.setValue("some_key", 1.2);

    assertThat(deviceCacheManager.getDouble("some_key").get()).isEqualTo(1.2);
  }

  @Test
  public void getDouble_valueIsSetAsFloat_returnsSetValue() {
    deviceCacheManager.setContext(appContext);
    fakeScheduledExecutorService.runAll();

    // Manually setting a Float to simulate it being cached from a previous SDK version.
    SharedPreferences sharedPreferences =
        appContext.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
    sharedPreferences.edit().putFloat("some_key", 1.2f).apply();

    assertThat(deviceCacheManager.getDouble("some_key").get()).isWithin(0.001).of(1.2);
  }

  @Test
  public void getDouble_firebaseAppNotExist_returnsEmpty() {
    DeviceCacheManager.clearInstance();
    FirebaseApp.clearInstancesForTest();
    deviceCacheManager = new DeviceCacheManager(fakeScheduledExecutorService);
    deviceCacheManager.setValue("some_key", 1.2);

    assertThat(fakeScheduledExecutorService.isEmpty()).isTrue();
    assertThat(deviceCacheManager.getDouble("some_key").isAvailable()).isFalse();
  }

  @Test
  public void setValueDouble_setTwice_canGetLatestValue() {
    deviceCacheManager.setContext(appContext);
    fakeScheduledExecutorService.runAll();
    deviceCacheManager.setValue("some_key", 1.01);
    assertThat(deviceCacheManager.getDouble("some_key").get()).isEqualTo(1.01);

    deviceCacheManager.setValue("some_key", 0.01);
    assertThat(deviceCacheManager.getDouble("some_key").get()).isEqualTo(0.01);
  }

  @Test
  public void setValueDouble_wasSetAsFloat_canGetLatestValue() {
    deviceCacheManager.setContext(appContext);
    fakeScheduledExecutorService.runAll();

    // Manually setting a Float to simulate it being cached from a previous SDK version.
    SharedPreferences sharedPreferences =
        appContext.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
    sharedPreferences.edit().putFloat("some_key", 1.2f).apply();

    deviceCacheManager.setValue("some_key", 0.01);
    assertThat(deviceCacheManager.getDouble("some_key").get()).isEqualTo(0.01);
  }

  @Test
  public void setValueDouble_contextNotSet_returnsEmpty() {
    deviceCacheManager.setValue("some_key", 100.0);
    assertThat(deviceCacheManager.getDouble("some_key").isAvailable()).isFalse();
  }

  @Test
  public void setValueDouble_keyIsNull_returnsFalse() {
    deviceCacheManager.setContext(appContext);
    fakeScheduledExecutorService.runAll();
    assertThat(deviceCacheManager.setValue(null, 10.0)).isFalse();
  }

  @Test
  public void getLong_valueIsNotSet_returnsEmpty() {
    DeviceCacheManager.clearInstance();
    deviceCacheManager = new DeviceCacheManager(fakeScheduledExecutorService);
    deviceCacheManager.setContext(appContext);
    fakeScheduledExecutorService.runAll();

    assertThat(fakeScheduledExecutorService.isEmpty()).isTrue();
    assertThat(deviceCacheManager.getLong("some_key").isAvailable()).isFalse();
  }

  @Test
  public void getLong_contextAndValueNotSet_returnsEmpty() {
    DeviceCacheManager.clearInstance();
    deviceCacheManager = new DeviceCacheManager(fakeScheduledExecutorService);

    assertThat(fakeScheduledExecutorService.isEmpty()).isTrue();
    assertThat(deviceCacheManager.getLong("some_key").isAvailable()).isFalse();
  }

  @Test
  public void getLong_valueIsSet_returnsSetValue() {
    deviceCacheManager.setContext(appContext);
    fakeScheduledExecutorService.runAll();
    deviceCacheManager.setValue("some_key", 1L);

    assertThat(deviceCacheManager.getLong("some_key").get()).isEqualTo(1L);
  }

  @Test
  public void getLong_firebaseAppNotExist_returnsEmpty() {
    DeviceCacheManager.clearInstance();
    FirebaseApp.clearInstancesForTest();
    deviceCacheManager = new DeviceCacheManager(fakeScheduledExecutorService);
    deviceCacheManager.setValue("some_key", 1L);

    assertThat(deviceCacheManager.getLong("some_key").isAvailable()).isFalse();
  }

  @Test
  public void setValueLong_setTwice_canGetLatestValue() {
    deviceCacheManager.setContext(appContext);
    fakeScheduledExecutorService.runAll();
    deviceCacheManager.setValue("some_key", 2L);
    assertThat(deviceCacheManager.getLong("some_key").get()).isEqualTo(2L);

    deviceCacheManager.setValue("some_key", 3L);
    assertThat(deviceCacheManager.getLong("some_key").get()).isEqualTo(3L);
  }

  @Test
  public void setValueLong_contextNotSet_returnsEmpty() {
    deviceCacheManager.setValue("some_key", 100L);
    // The key is not set if the shared preference is not fetched and available.
    assertThat(deviceCacheManager.getLong("some_key").isAvailable()).isFalse();
  }

  @Test
  public void setValueLong_keyIsNull_returnsFalse() {
    deviceCacheManager.setContext(appContext);
    fakeScheduledExecutorService.runAll();
    assertThat(deviceCacheManager.setValue(null, 10.0)).isFalse();
  }
}
