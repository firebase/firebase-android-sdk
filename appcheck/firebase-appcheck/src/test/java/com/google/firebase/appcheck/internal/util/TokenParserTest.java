// Copyright 2020 Google LLC
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

package com.google.firebase.appcheck.internal.util;

import static com.google.common.truth.Truth.assertThat;

import com.google.android.gms.common.util.Base64Utils;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Tests for {@link TokenParser} */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class TokenParserTest {

  private static final String INT_KEY = "intKey";
  private static final int INT_VALUE = 0;
  private static final String BOOL_KEY = "boolKey";
  private static final boolean BOOL_VALUE = true;
  private static final String STR_KEY = "stringKey";
  private static final String STR_VALUE = "stringValue";
  private static final String LIST_KEY = "listKey";
  private static final int LIST_VALUE_1 = 1;
  private static final int LIST_VALUE_2 = 2;
  private static final int LIST_VALUE_3 = 3;
  private static final String MAP_KEY = "mapKey";
  private static final String SUBMAP_KEY_1 = "submapKey1";
  private static final int SUBMAP_VALUE_1 = -1;
  private static final String SUBMAP_KEY_2 = "submapKey2";
  private static final int SUBMAP_VALUE_2 = -2;
  private static final String NULL_KEY = "nullKey";
  private static final String HEADER = "header.";

  private static String tokenBody;

  @BeforeClass
  public static void setup() throws Exception {
    JSONObject jsonObject = new JSONObject();
    jsonObject.put(STR_KEY, STR_VALUE);
    jsonObject.put(INT_KEY, INT_VALUE);
    jsonObject.put(BOOL_KEY, BOOL_VALUE);
    List<Integer> list = new ArrayList<>();
    list.add(LIST_VALUE_1);
    list.add(LIST_VALUE_2);
    list.add(LIST_VALUE_3);
    jsonObject.put(LIST_KEY, new JSONArray(list));
    Map<String, Object> subMap = new HashMap<>();
    subMap.put(SUBMAP_KEY_1, SUBMAP_VALUE_1);
    subMap.put(SUBMAP_KEY_2, SUBMAP_VALUE_2);
    jsonObject.put(MAP_KEY, new JSONObject(subMap));
    jsonObject.put(NULL_KEY, null);

    tokenBody =
        Base64Utils.encodeUrlSafeNoPadding(jsonObject.toString().getBytes(StandardCharsets.UTF_8));
  }

  @Test
  public void testParseTokenClaims_noHeader_returnsEmptyMap() {
    assertThat(TokenParser.parseTokenClaims(tokenBody)).isEqualTo(Collections.EMPTY_MAP);
  }

  @Test
  public void testParseTokenClaims_withHeader_returnsExpectedMap() {
    Map<String, Object> resultMap = TokenParser.parseTokenClaims(HEADER + tokenBody);
    assertThat(resultMap).isNotEmpty();
    assertThat((String) resultMap.get(STR_KEY)).isEqualTo(STR_VALUE);
    assertThat((Integer) resultMap.get(INT_KEY)).isEqualTo(INT_VALUE);
    assertThat((Boolean) resultMap.get(BOOL_KEY)).isEqualTo(BOOL_VALUE);
    @SuppressWarnings("unchecked")
    List<Number> list = (List<Number>) resultMap.get(LIST_KEY);
    assertThat(list).isNotNull();
    assertThat(list).containsExactly(LIST_VALUE_1, LIST_VALUE_2, LIST_VALUE_3).inOrder();
    @SuppressWarnings("unchecked")
    Map<String, Object> subMap = (Map<String, Object>) resultMap.get(MAP_KEY);
    assertThat(subMap).isNotNull();
    assertThat(subMap.get(SUBMAP_KEY_1)).isEqualTo(SUBMAP_VALUE_1);
    assertThat(subMap.get(SUBMAP_KEY_2)).isEqualTo(SUBMAP_VALUE_2);
    assertThat(resultMap.get(NULL_KEY)).isNull();
  }
}
