// Copyright 2018 Google LLC
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

package com.google.firebase.remoteconfig.internal;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.DEFAULT_VALUE_FOR_BOOLEAN;
import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.DEFAULT_VALUE_FOR_DOUBLE;
import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.DEFAULT_VALUE_FOR_LONG;
import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.DEFAULT_VALUE_FOR_STRING;
import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.VALUE_SOURCE_REMOTE;
import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.VALUE_SOURCE_STATIC;
import static com.google.firebase.remoteconfig.testutil.Assert.assertThrows;

import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Unit tests for the {@link FirebaseRemoteConfigValueImpl}.
 *
 * @author Miraziz Yusupov
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class FirebaseRemoteConfigValueImplTest {
  @Before
  public void setUp() throws Exception {}

  @Test
  public void asString_isStaticValue_returnsStaticString() throws Exception {
    FirebaseRemoteConfigValue value =
        new FirebaseRemoteConfigValueImpl(DEFAULT_VALUE_FOR_STRING, VALUE_SOURCE_STATIC);

    assertThat(value.asString()).isEqualTo(DEFAULT_VALUE_FOR_STRING);
  }

  @Test
  public void asString_isNonStaticValue_returnsString() throws Exception {
    String stringValue = "string value";
    FirebaseRemoteConfigValue value =
        new FirebaseRemoteConfigValueImpl(stringValue, VALUE_SOURCE_REMOTE);

    assertThat(value.asString()).isEqualTo(stringValue);
  }

  @Test
  public void asLong_isStaticValue_returnsStaticLong() throws Exception {
    FirebaseRemoteConfigValue value =
        new FirebaseRemoteConfigValueImpl(
            Long.toString(DEFAULT_VALUE_FOR_LONG), VALUE_SOURCE_STATIC);

    assertThat(value.asLong()).isEqualTo(DEFAULT_VALUE_FOR_LONG);
  }

  @Test
  public void asLong_isNotLongValue_throwsException() throws Exception {
    FirebaseRemoteConfigValue value =
        new FirebaseRemoteConfigValueImpl("not a long", VALUE_SOURCE_REMOTE);

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, value::asLong);

    assertThat(exception).hasMessageThat().contains("cannot be converted to a long");
  }

  @Test
  public void asLong_isLongValue_returnsLong() throws Exception {
    long longValue = 555L;
    FirebaseRemoteConfigValue value =
        new FirebaseRemoteConfigValueImpl(Long.toString(longValue), VALUE_SOURCE_REMOTE);

    assertThat(value.asLong()).isEqualTo(longValue);
  }

  @Test
  public void asDouble_isStaticValue_returnsStaticDouble() throws Exception {
    FirebaseRemoteConfigValue value =
        new FirebaseRemoteConfigValueImpl(
            Double.toString(DEFAULT_VALUE_FOR_DOUBLE), VALUE_SOURCE_STATIC);

    assertThat(value.asDouble()).isEqualTo(DEFAULT_VALUE_FOR_DOUBLE);
  }

  @Test
  public void asDouble_isNotDoubleValue_throwsException() throws Exception {
    FirebaseRemoteConfigValue value =
        new FirebaseRemoteConfigValueImpl("not a double", VALUE_SOURCE_REMOTE);

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, value::asDouble);

    assertThat(exception).hasMessageThat().contains("cannot be converted to a double");
  }

  @Test
  public void asDouble_isDoubleValue_returnsDouble() throws Exception {
    double doubleValue = 555.5D;
    FirebaseRemoteConfigValue value =
        new FirebaseRemoteConfigValueImpl(Double.toString(doubleValue), VALUE_SOURCE_REMOTE);

    assertThat(value.asDouble()).isEqualTo(doubleValue);
  }

  @Test
  public void asBoolean_isStaticValue_returnsStaticBoolean() throws Exception {
    FirebaseRemoteConfigValue value =
        new FirebaseRemoteConfigValueImpl(
            Boolean.toString(DEFAULT_VALUE_FOR_BOOLEAN), VALUE_SOURCE_STATIC);

    assertThat(value.asBoolean()).isEqualTo(DEFAULT_VALUE_FOR_BOOLEAN);
  }

  @Test
  public void asBoolean_isNotBooleanValue_throwsException() throws Exception {
    FirebaseRemoteConfigValue value = new FirebaseRemoteConfigValueImpl("si", VALUE_SOURCE_REMOTE);

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, value::asBoolean);

    assertThat(exception).hasMessageThat().contains("cannot be converted to a boolean");
  }

  @Test
  public void asBoolean_isBooleanValue_returnsBoolean() throws Exception {
    FirebaseRemoteConfigValue value = new FirebaseRemoteConfigValueImpl("yes", VALUE_SOURCE_REMOTE);

    assertThat(value.asBoolean()).isTrue();
  }
}
