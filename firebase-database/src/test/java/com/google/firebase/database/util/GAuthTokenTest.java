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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.google.firebase.database.MapBuilder;
import java.util.Map;
import org.junit.Test;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@org.junit.runner.RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class GAuthTokenTest {

  private static final Map<String, Object> exampleAuth =
      new MapBuilder().put("a", "a-val").put("b", 42).build();

  @Test
  public void construction() {
    GAuthToken token = new GAuthToken("token", exampleAuth);
    assertEquals("token", token.getToken());
    assertEquals(exampleAuth, token.getAuth());
  }

  @Test
  public void parseNonToken() {
    GAuthToken parsed = GAuthToken.tryParseFromString("notgauth|foo");
    assertNull(parsed);
  }

  @Test
  public void serializeDeserialize() {
    testRoundTrip(null, null);
    testRoundTrip("token", null);
    testRoundTrip(null, exampleAuth);
    testRoundTrip("token", exampleAuth);
  }

  private void testRoundTrip(String token, Map<String, Object> auth) {
    GAuthToken origToken = new GAuthToken(token, auth);
    GAuthToken restoredToken = GAuthToken.tryParseFromString(origToken.serializeToString());
    assertNotNull(restoredToken);
    assertEquals(token, restoredToken.getToken());
    assertEquals(auth, restoredToken.getAuth());
  }
}
