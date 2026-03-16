/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.crashlytics.buildtools.api;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WebApiTest {
  @Test
  public void mergeStrings_even() {
    String part1 = "abcd";
    String part2 = "1234";

    assertEquals(/* expected= */ "a1b2c3d4", /* actual= */ WebApi.mergeStrings(part1, part2));
  }

  @Test
  public void mergeStrings_odd1() {
    String part1 = "abcde";
    String part2 = "1234";

    assertEquals(/* expected= */ "a1b2c3d4e", /* actual= */ WebApi.mergeStrings(part1, part2));
  }

  @Test
  public void mergeStrings_odd2() {
    String part1 = "abcd";
    String part2 = "12345";

    assertEquals(/* expected= */ "a1b2c3d45", /* actual= */ WebApi.mergeStrings(part1, part2));
  }
}
