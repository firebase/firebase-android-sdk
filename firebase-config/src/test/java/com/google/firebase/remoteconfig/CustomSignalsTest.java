// Copyright 2024 Google LLC
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

package com.google.firebase.remoteconfig;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Unit tests for the {@link CustomSignals}.*/
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class CustomSignalsTest {
  @Test
  public void testCustomSignals_builderPutString() {
    CustomSignals customSignals =
        new CustomSignals.Builder().put("key1", "value1").put("key2", "value2").build();
    Map<String, String> expectedSignals = ImmutableMap.of("key1", "value1", "key2", "value2");
    assertEquals(expectedSignals, customSignals.customSignals);
  }

  @Test
  public void testCustomSignals_builderPutLong() {
    CustomSignals customSignals =
        new CustomSignals.Builder().put("key1", 123L).put("key2", 456L).build();
    Map<String, String> expectedSignals = ImmutableMap.of("key1", "123", "key2", "456");
    assertEquals(expectedSignals, customSignals.customSignals);
  }

  @Test
  public void testCustomSignals_builderPutDouble() {
    CustomSignals customSignals =
        new CustomSignals.Builder().put("key1", 12.34).put("key2", 56.78).build();
    Map<String, String> expectedSignals = ImmutableMap.of("key1", "12.34", "key2", "56.78");
    assertEquals(expectedSignals, customSignals.customSignals);
  }

  @Test
  public void testCustomSignals_builderPutMixedTypes() {
    CustomSignals customSignals =
        new CustomSignals.Builder()
            .put("key1", "value1")
            .put("key2", 123L)
            .put("key3", 45.67)
            .build();
    Map<String, String> expectedSignals =
        ImmutableMap.of("key1", "value1", "key2", "123", "key3", "45.67");
    assertEquals(expectedSignals, customSignals.customSignals);
  }

  @Test
  public void testCustomSignals_builderPutNullValue() {
    CustomSignals customSignals = new CustomSignals.Builder().put("key1", null).build();
    Map<String, String> expectedSignals = new HashMap<>();
    expectedSignals.put("key1", null);
    assertEquals(expectedSignals, customSignals.customSignals);
  }

  @Test
  public void testCustomSignals_builderEmpty() {
    CustomSignals customSignals = new CustomSignals.Builder().build();
    assertTrue(customSignals.customSignals.isEmpty());
  }
}
