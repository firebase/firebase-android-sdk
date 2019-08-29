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

package com.google.android.datatransport.cct;

import static com.google.android.datatransport.cct.StringMerger.mergeStrings;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class StringMergerTest {
  private static final long INITIAL_WALL_TIME = 200L;
  private static final long INITIAL_UPTIME = 10L;

  @Test
  public void mergeStrings_whenPartsAreUnequalLength() {
    String part1 = "hts/eapecm";
    String part2 = "tp:/xml.o";
    assertThat(mergeStrings(part1, part2)).isEqualTo("https://example.com");
  }

  @Test
  public void mergeStrings_whenPartsAreEqualLength() {
    String part1 = "hts/eape.o";
    String part2 = "tp:/xmlscm";
    assertThat(mergeStrings(part1, part2)).isEqualTo("https://examples.com");
  }

  @Test
  public void mergeStrings_whenPart2IsLongerThanPart1() {
    String part1 = "135";
    String part2 = "2467";
    assertThrows(IllegalArgumentException.class, () -> mergeStrings(part1, part2));
  }
}
