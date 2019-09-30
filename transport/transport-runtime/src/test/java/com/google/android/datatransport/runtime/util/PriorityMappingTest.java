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

package com.google.android.datatransport.runtime.util;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.android.datatransport.Priority;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class PriorityMappingTest {
  @Test
  public void testDefault_shouldHaveExpectedValue() {
    assertThat(PriorityMapping.valueOf(0)).isSameInstanceAs(Priority.DEFAULT);
    assertThat(PriorityMapping.toInt(Priority.DEFAULT)).isEqualTo(0);
  }

  @Test
  public void testVeryLow_shouldHaveExpectedValue() {
    assertThat(PriorityMapping.valueOf(1)).isSameInstanceAs(Priority.VERY_LOW);
    assertThat(PriorityMapping.toInt(Priority.VERY_LOW)).isEqualTo(1);
  }

  @Test
  public void testHighest_shouldHaveExpectedValue() {
    assertThat(PriorityMapping.valueOf(2)).isSameInstanceAs(Priority.HIGHEST);
    assertThat(PriorityMapping.toInt(Priority.HIGHEST)).isEqualTo(2);
  }

  @Test
  public void forValue_withInvalidValue_shouldThrow() {
    assertThrows(IllegalArgumentException.class, () -> PriorityMapping.valueOf(-1));
    assertThrows(IllegalArgumentException.class, () -> PriorityMapping.valueOf(3));
  }
}
