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

package com.google.android.datatransport;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class PriorityTest {
  @Test
  public void testDefault_shouldHaveExpectedValue() {
    assertThat(Priority.forValue(0)).isSameInstanceAs(Priority.DEFAULT);
    assertThat(Priority.DEFAULT.getValue()).isEqualTo(0);
  }

  @Test
  public void testVeryLow_shouldHaveExpectedValue() {
    assertThat(Priority.forValue(1)).isSameInstanceAs(Priority.VERY_LOW);
    assertThat(Priority.VERY_LOW.getValue()).isEqualTo(1);
  }

  @Test
  public void testHighest_shouldHaveExpectedValue() {
    assertThat(Priority.forValue(2)).isSameInstanceAs(Priority.HIGHEST);
    assertThat(Priority.HIGHEST.getValue()).isEqualTo(2);
  }

  @Test
  public void forValue_withInvalidValue_shouldThrow() {
    assertThrows(IllegalArgumentException.class, () -> Priority.forValue(-1));
    assertThrows(IllegalArgumentException.class, () -> Priority.forValue(3));
  }
}
