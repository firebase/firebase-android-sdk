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

package com.google.firebase.perf.util;

import static com.google.common.truth.Truth.assertThat;

import android.os.Bundle;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for {@link ImmutableBundle}. */
@RunWith(RobolectricTestRunner.class)
public final class ImmutableBundleTest {

  @Test
  public void testImmutability() {
    Bundle testBundle = new Bundle();
    testBundle.putBoolean("testKey", true);
    ImmutableBundle testImmutableBundle = new ImmutableBundle(testBundle);

    testBundle.putBoolean("testKey", false);
    testBundle.putBoolean("testKey2", true);

    assertThat(testImmutableBundle.getBoolean("testKey").get()).isTrue();
    assertThat(testImmutableBundle.containsKey("testKey2")).isFalse();
  }

  @Test
  public void containsKey_returnsKeyIfPresent() {
    Bundle testBundle = new Bundle();
    testBundle.putBoolean("testKey", true);

    ImmutableBundle testImmutableBundle = new ImmutableBundle(testBundle);
    assertThat(testImmutableBundle.containsKey("testKey")).isTrue();
  }

  @Test
  public void containsKey_doesNotReturnKeyIfNotPresent() {
    Bundle testBundle = new Bundle();
    testBundle.putBoolean("testKey", true);

    ImmutableBundle testImmutableBundle = new ImmutableBundle(testBundle);
    assertThat(testImmutableBundle.containsKey("notPresentKey")).isFalse();
  }

  @Test
  public void getBooleanOptional_noValueFound_returnsEmpty() {
    Bundle testBundle = new Bundle();
    testBundle.putBoolean("testKey", true);

    ImmutableBundle testImmutableBundle = new ImmutableBundle(testBundle);
    assertThat(testImmutableBundle.getBoolean("notPresentKey").isAvailable()).isFalse();
  }

  @Test
  public void getBooleanOptional_valueFound_returnsValue() {
    Bundle testBundle = new Bundle();
    testBundle.putBoolean("testKey", true);

    ImmutableBundle testImmutableBundle = new ImmutableBundle(testBundle);
    assertThat(testImmutableBundle.getBoolean("testKey").get()).isTrue();
  }

  @Test
  public void getBooleanOptional_keyIsNull_returnsEmpty() {
    Bundle testBundle = new Bundle();
    testBundle.putBoolean("testKey", true);

    ImmutableBundle testImmutableBundle = new ImmutableBundle(testBundle);
    assertThat(testImmutableBundle.getBoolean(null).isAvailable()).isFalse();
  }

  @Test
  public void getBooleanOptional_valueTypeNotMatch_returnsEmpty() {
    Bundle testBundle = new Bundle();
    testBundle.putFloat("testKey", 123.456f);

    ImmutableBundle testImmutableBundle = new ImmutableBundle(testBundle);
    assertThat(testImmutableBundle.getBoolean("testKey").isAvailable()).isFalse();
  }

  @Test
  public void getDoubleOptional_noValueFound_returnsEmpty() {
    Bundle testBundle = new Bundle();
    testBundle.putFloat("testKey", 25.0f);

    ImmutableBundle testImmutableBundle = new ImmutableBundle(testBundle);
    assertThat(testImmutableBundle.getDouble("notPresentKey").isAvailable()).isFalse();
  }

  @Test
  public void getDoubleOptional_valueFound_returnsValue() {
    Bundle testBundle = new Bundle();
    testBundle.putFloat("testKey", 25.0f);

    ImmutableBundle testImmutableBundle = new ImmutableBundle(testBundle);
    assertThat(testImmutableBundle.getDouble("testKey").get()).isEqualTo(25.0);
  }

  @Test
  public void getDoubleOptional_keyIsNull_returnsEmpty() {
    Bundle testBundle = new Bundle();
    testBundle.putFloat("testKey", 25.0f);

    ImmutableBundle testImmutableBundle = new ImmutableBundle(testBundle);
    assertThat(testImmutableBundle.getDouble(null).isAvailable()).isFalse();
  }

  @Test
  public void getDoubleOptional_valueTypeNotMatch_returnsEmpty() {
    Bundle testBundle = new Bundle();
    testBundle.putBoolean("testKey", true);

    ImmutableBundle testImmutableBundle = new ImmutableBundle(testBundle);
    assertThat(testImmutableBundle.getDouble("testKey").isAvailable()).isFalse();
  }

  @Test
  public void getDoubleOptional_valueIsNull_returnsEmpty() {
    Bundle testBundle = new Bundle();
    testBundle.putString("testKey", null);

    ImmutableBundle testImmutableBundle = new ImmutableBundle(testBundle);
    assertThat(testImmutableBundle.getDouble("testKey").isAvailable()).isFalse();
  }

  @Test
  public void getLongOptional_noValueFound_returnsEmpty() {
    Bundle testBundle = new Bundle();
    testBundle.putInt("testKey", 25);

    ImmutableBundle testImmutableBundle = new ImmutableBundle(testBundle);
    assertThat(testImmutableBundle.getLong("notPresentKey").isAvailable()).isFalse();
  }

  @Test
  public void getLongOptional_valueFound_returnsValue() {
    Bundle testBundle = new Bundle();
    testBundle.putInt("testKey", 25);

    ImmutableBundle testImmutableBundle = new ImmutableBundle(testBundle);
    assertThat(testImmutableBundle.getLong("testKey").get()).isEqualTo(25L);
  }

  @Test
  public void getLongOptional_extremeIntValue_returnsValue() {
    Bundle testBundle = new Bundle();
    testBundle.putInt("maxIntKey", Integer.MAX_VALUE);
    testBundle.putInt("minIntKey", Integer.MIN_VALUE);

    ImmutableBundle testImmutableBundle = new ImmutableBundle(testBundle);
    assertThat(testImmutableBundle.getLong("maxIntKey").get()).isEqualTo(Integer.MAX_VALUE);
    assertThat(testImmutableBundle.getLong("minIntKey").get()).isEqualTo(Integer.MIN_VALUE);
  }

  @Test
  public void getLongOptional_valueIsZero_returnsValue() {
    Bundle testBundle = new Bundle();
    testBundle.putInt("testKey", 0);

    ImmutableBundle testImmutableBundle = new ImmutableBundle(testBundle);
    assertThat(testImmutableBundle.getLong("testKey").get()).isEqualTo(0);
  }

  @Test
  public void getLongOptional_keyIsNull_returnsEmpty() {
    Bundle testBundle = new Bundle();
    testBundle.putInt("testKey", 0);

    ImmutableBundle testImmutableBundle = new ImmutableBundle(testBundle);
    assertThat(testImmutableBundle.getLong(null).isAvailable()).isFalse();
  }

  @Test
  public void getLongOptional_valueTypeNotMatch_returnsEmpty() {
    Bundle testBundle = new Bundle();
    testBundle.putBoolean("booleanKey", true);

    ImmutableBundle testImmutableBundle = new ImmutableBundle(testBundle);
    assertThat(testImmutableBundle.getLong("booleanKey").isAvailable()).isFalse();
  }

  @Test
  public void getLongOptional_valueIsNull_returnsEmpty() {
    Bundle testBundle = new Bundle();
    testBundle.putString("testKey", null);

    ImmutableBundle testImmutableBundle = new ImmutableBundle(testBundle);
    assertThat(testImmutableBundle.getLong("testKey").isAvailable()).isFalse();
  }
}
