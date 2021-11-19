// Copyright 2019 Google LLC
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

package com.google.firebase.internal;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ObfuscationUtilsTest {
  @Test
  public void isEqual_whenEqualStrings_shouldReturnTrue() {
    assertThat(ObfuscationUtils.isEqual("equal", "equal")).isTrue();
  }

  @Test
  public void isEqual_whenSameLengthButDifferentStrings_shouldReturnFalse() {
    assertThat(ObfuscationUtils.isEqual("hello", "equal")).isFalse();
  }

  @Test
  public void isEqual_whenOneIsShorterThanOther_shouldReturnFalse() {
    assertThat(ObfuscationUtils.isEqual("eq", "equal")).isFalse();
  }

  @Test
  public void isEqual_whenOneIsLongerThanOther_shouldReturnFalse() {
    assertThat(ObfuscationUtils.isEqual("equal", "eq")).isFalse();
  }

  @Test
  public void isAppObfuscated_isFalseByDefault() {
    assertThat(ObfuscationUtils.isAppObfuscated()).isFalse();
  }
}
