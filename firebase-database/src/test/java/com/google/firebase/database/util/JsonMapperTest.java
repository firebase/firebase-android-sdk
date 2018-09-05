// Copyright 2018 Google LLC
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

package com.google.firebase.database.util;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@org.junit.runner.RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class JsonMapperTest {

  @Test
  public void canConvertLongs() throws IOException {
    List<Long> longs = Arrays.asList(Long.MAX_VALUE, Long.MIN_VALUE);
    for (Long original : longs) {
      String jsonString = JsonMapper.serializeJsonValue(original);
      long converted = (Long) JsonMapper.parseJsonValue(jsonString);
      assertEquals((long) original, converted);
    }
  }

  @Test
  public void canConvertDoubles() throws IOException {
    List<Double> doubles = Arrays.asList(Double.MAX_VALUE, Double.MIN_VALUE, Double.MIN_NORMAL);
    for (Double original : doubles) {
      String jsonString = JsonMapper.serializeJsonValue(original);
      double converted = (Double) JsonMapper.parseJsonValue(jsonString);
      assertEquals(original, converted, 0);
    }
  }

  @Test
  public void canNest33LevelsDeep() throws IOException {
    Map<String, Object> root = new HashMap<>();
    Map<String, Object> currentMap = root;
    for (int i = 0; i < 33 - 1; i++) {
      Map<String, Object> newMap = new HashMap<>();
      currentMap.put("key", newMap);
      currentMap = newMap;
    }
    String jsonString = JsonMapper.serializeJsonValue(root);
    Object value = JsonMapper.parseJsonValue(jsonString);
    assertEquals(root, value);
  }
}
