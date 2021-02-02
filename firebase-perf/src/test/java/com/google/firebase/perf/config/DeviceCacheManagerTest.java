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

import com.google.firebase.FirebaseApp;
import com.google.firebase.perf.FirebasePerformanceTestBase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for {@link DeviceCacheManager}. */
@RunWith(RobolectricTestRunner.class)
public final class DeviceCacheManagerTest extends FirebasePerformanceTestBase {

  private DeviceCacheManager deviceCacheManager;

  @Before
  public void setUp() {
    deviceCacheManager = DeviceCacheManager.getInstance();
  }

  @Test
  public void getBoolean_valueIsNotSet_returnsEmpty() {
    deviceCacheManager.setContext(context);

    assertThat(deviceCacheManager.getBoolean("some_key").isAvailable()).isFalse();
  }

  @Test
  public void getBoolean_contextAndValueNotSet_returnsEmpty() {
    assertThat(deviceCacheManager.getBoolean("some_key").isAvailable()).isFalse();
  }

  @Test
  public void getBoolean_valueIsSet_returnsSetValue() {
    deviceCacheManager.setContext(context);
    deviceCacheManager.setValue("some_key", true);

    assertThat(deviceCacheManager.getBoolean("some_key").get()).isTrue();
  }

  @Test
  public void clear_setBooleanThenCleared_returnsEmpty() {
    deviceCacheManager.setContext(context);

    deviceCacheManager.setValue("some_key", true);

    assertThat(deviceCacheManager.getBoolean("some_key").get()).isTrue();

    deviceCacheManager.clear("some_key");
    assertThat(deviceCacheManager.getBoolean("some_key").isAvailable()).isFalse();
  }

  @Test
  public void getBoolean_firebaseAppNotExist_returnsEmpty() {
    DeviceCacheManager.clearInstance();
    FirebaseApp.clearInstancesForTest();
    deviceCacheManager = DeviceCacheManager.getInstance();
    deviceCacheManager.setValue("some_key", true);

    assertThat(deviceCacheManager.getBoolean("some_key").isAvailable()).isFalse();
  }

  @Test
  public void setValueBoolean_setTwice_canGetLatestValue() {
    deviceCacheManager.setValue("some_key", true);
    assertThat(deviceCacheManager.getBoolean("some_key").get()).isTrue();

    deviceCacheManager.setValue("some_key", false);
    assertThat(deviceCacheManager.getBoolean("some_key").get()).isFalse();
  }

  @Test
  public void setValueBoolean_contextNotSet_canGetValue() {
    deviceCacheManager.setValue("some_key", true);

    assertThat(deviceCacheManager.getBoolean("some_key").get()).isTrue();
  }

  @Test
  public void setValueBoolean_keyIsNull_returnsFalse() {
    assertThat(deviceCacheManager.setValue(null, true)).isFalse();
  }

  @Test
  public void getString_valueIsNotSet_returnsEmpty() {
    deviceCacheManager.setContext(context);

    assertThat(deviceCacheManager.getString("some_key").isAvailable()).isFalse();
  }

  @Test
  public void getString_contextAndValueNotSet_returnsEmpty() {
    assertThat(deviceCacheManager.getString("some_key").isAvailable()).isFalse();
  }

  @Test
  public void getString_valueIsSet_returnsSetValue() {
    deviceCacheManager.setContext(context);
    deviceCacheManager.setValue("some_key", "speicalValue");

    assertThat(deviceCacheManager.getString("some_key").get()).isEqualTo("speicalValue");
  }

  @Test
  public void getString_firebaseAppNotExist_returnsEmpty() {
    DeviceCacheManager.clearInstance();
    FirebaseApp.clearInstancesForTest();
    deviceCacheManager = DeviceCacheManager.getInstance();
    deviceCacheManager.setValue("some_key", "speicalValue");

    assertThat(deviceCacheManager.getString("some_key").isAvailable()).isFalse();
  }

  @Test
  public void setValueString_setTwice_canGetLatestValue() {
    deviceCacheManager.setValue("some_key", "EarliestValue");
    assertThat(deviceCacheManager.getString("some_key").get()).isEqualTo("EarliestValue");

    deviceCacheManager.setValue("some_key", "latestValue");
    assertThat(deviceCacheManager.getString("some_key").get()).isEqualTo("latestValue");
  }

  @Test
  public void setValueString_contextNotSet_canGetValue() {
    deviceCacheManager.setValue("some_key", "newValue");
    assertThat(deviceCacheManager.getString("some_key").get()).isEqualTo("newValue");
  }

  @Test
  public void setValueString_setNullString_returnsEmpty() {
    deviceCacheManager.setValue("some_key", null);
    assertThat(deviceCacheManager.getString("some_key").isAvailable()).isFalse();
  }

  @Test
  public void setValueString_keyIsNull_returnsFalse() {
    assertThat(deviceCacheManager.setValue(null, "value")).isFalse();
  }

  @Test
  public void getFloat_valueIsNotSet_returnsEmpty() {
    deviceCacheManager.setContext(context);

    assertThat(deviceCacheManager.getFloat("some_key").isAvailable()).isFalse();
  }

  @Test
  public void getFloat_contextAndValueNotSet_returnsEmpty() {
    DeviceCacheManager.clearInstance();
    deviceCacheManager = DeviceCacheManager.getInstance();

    assertThat(deviceCacheManager.getFloat("some_key").isAvailable()).isFalse();
  }

  @Test
  public void getFloat_valueIsSet_returnsSetValue() {
    deviceCacheManager.setContext(context);
    deviceCacheManager.setValue("some_key", 1.2f);

    assertThat(deviceCacheManager.getFloat("some_key").get()).isEqualTo(1.2f);
  }

  @Test
  public void getFloat_firebaseAppNotExist_returnsEmpty() {
    DeviceCacheManager.clearInstance();
    FirebaseApp.clearInstancesForTest();
    deviceCacheManager = DeviceCacheManager.getInstance();
    deviceCacheManager.setValue("some_key", 1.2f);

    assertThat(deviceCacheManager.getFloat("some_key").isAvailable()).isFalse();
  }

  @Test
  public void setValueFloat_setTwice_canGetLatestValue() {
    deviceCacheManager.setValue("some_key", 1.01f);
    assertThat(deviceCacheManager.getFloat("some_key").get()).isEqualTo(1.01f);

    deviceCacheManager.setValue("some_key", 0.01f);
    assertThat(deviceCacheManager.getFloat("some_key").get()).isEqualTo(0.01f);
  }

  @Test
  public void setValueFloat_contextNotSet_canGetValue() {
    deviceCacheManager.setValue("some_key", 100.0f);
    assertThat(deviceCacheManager.getFloat("some_key").get()).isEqualTo(100.0f);
  }

  @Test
  public void setValueFloat_keyIsNull_returnsFalse() {
    assertThat(deviceCacheManager.setValue(null, 10.0f)).isFalse();
  }

  @Test
  public void getLong_valueIsNotSet_returnsEmpty() {
    DeviceCacheManager.clearInstance();
    deviceCacheManager = DeviceCacheManager.getInstance();
    deviceCacheManager.setContext(context);

    assertThat(deviceCacheManager.getLong("some_key").isAvailable()).isFalse();
  }

  @Test
  public void getLong_contextAndValueNotSet_returnsEmpty() {
    DeviceCacheManager.clearInstance();
    deviceCacheManager = DeviceCacheManager.getInstance();

    assertThat(deviceCacheManager.getLong("some_key").isAvailable()).isFalse();
  }

  @Test
  public void getLong_valueIsSet_returnsSetValue() {
    deviceCacheManager.setContext(context);
    deviceCacheManager.setValue("some_key", 1L);

    assertThat(deviceCacheManager.getLong("some_key").get()).isEqualTo(1L);
  }

  @Test
  public void getLong_firebaseAppNotExist_returnsEmpty() {
    DeviceCacheManager.clearInstance();
    FirebaseApp.clearInstancesForTest();
    deviceCacheManager = DeviceCacheManager.getInstance();
    deviceCacheManager.setValue("some_key", 1L);

    assertThat(deviceCacheManager.getLong("some_key").isAvailable()).isFalse();
  }

  @Test
  public void setValueLong_setTwice_canGetLatestValue() {
    deviceCacheManager.setValue("some_key", 2L);
    assertThat(deviceCacheManager.getLong("some_key").get()).isEqualTo(2L);

    deviceCacheManager.setValue("some_key", 3L);
    assertThat(deviceCacheManager.getLong("some_key").get()).isEqualTo(3L);
  }

  @Test
  public void setValueLong_contextNotSet_canGetValue() {
    deviceCacheManager.setValue("some_key", 100L);
    assertThat(deviceCacheManager.getLong("some_key").get()).isEqualTo(100L);
  }

  @Test
  public void setValueLong_keyIsNull_returnsFalse() {
    assertThat(deviceCacheManager.setValue(null, 10.0f)).isFalse();
  }
}
