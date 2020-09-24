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

package com.google.firebase.database.core.utilities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.firebase.database.DatabaseException;
import com.google.firebase.emulators.EmulatedServiceSettings;
import org.junit.Test;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@org.junit.runner.RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class ParseUrlTest {

  @Test
  public void testUrlParsing() throws DatabaseException {
    ParsedUrl parsed = Utilities.parseUrl("http://gsoltis.fblocal.com:9000");
    assertEquals("/", parsed.path.toString());
    assertEquals("gsoltis.fblocal.com:9000", parsed.repoInfo.host);
    assertEquals("gsoltis.fblocal.com:9000", parsed.repoInfo.internalHost);

    parsed = Utilities.parseUrl("http://gsoltis.firebaseio.com/foo/bar");
    assertEquals("gsoltis.firebaseio.com", parsed.repoInfo.host);
    assertEquals("gsoltis.firebaseio.com", parsed.repoInfo.internalHost);

    parsed = Utilities.parseUrl("http://gsoltis.firebaseio.com/foo/empty space");
    assertEquals("/foo/empty space", parsed.path.toString());
    assertEquals("gsoltis.firebaseio.com", parsed.repoInfo.host);
    assertEquals("gsoltis.firebaseio.com", parsed.repoInfo.internalHost);

    parsed = Utilities.parseUrl("http://gsoltis.firebaseio.com/foo/\\;:@\uD83D\uDE00");
    assertEquals("/foo/\\;:@\uD83D\uDE00", parsed.path.toString());
    assertEquals("gsoltis.firebaseio.com", parsed.repoInfo.host);
    assertEquals("gsoltis.firebaseio.com", parsed.repoInfo.internalHost);
  }

  @Test
  public void testUrlParsingTurnsPlusIntoSpace() throws DatabaseException {
    ParsedUrl parsed = Utilities.parseUrl("http://gsoltis.firebaseio.com/+");
    assertEquals("/ ", parsed.path.toString());
  }

  @Test
  public void testUrlParsingSpecialCharacters() throws DatabaseException {
    ParsedUrl parsed =
        Utilities.parseUrl("http://gsoltis.firebaseio.com/a%b&c@d/+space: /non-ascii:ø");
    assertEquals("/a%b&c@d/ space: /non-ascii:ø", parsed.path.toString());
  }

  @Test
  public void testUrlParsingIgnoresTrailingSlash() throws DatabaseException {
    ParsedUrl parsed1 = Utilities.parseUrl("http://gsoltis.firebaseio.com/");
    ParsedUrl parsed2 = Utilities.parseUrl("http://gsoltis.firebaseio.com");
    assertEquals(parsed1, parsed2);
  }

  @Test
  public void testUrlParsingWithNamespace() throws DatabaseException {
    ParsedUrl parsed = Utilities.parseUrl("http://localhost/foo/bar?ns=mrschmidt");
    assertEquals("mrschmidt", parsed.repoInfo.namespace);

    parsed = Utilities.parseUrl("http://10.0.2.2:9000/foo/bar?ns=mrschmidt");
    assertEquals(parsed.path.toString(), "/foo/bar");
    assertEquals("mrschmidt", parsed.repoInfo.namespace);
  }

  @Test
  public void testUrlParsingSslDetection() throws DatabaseException {
    // Hosts with custom ports are considered non-secure
    ParsedUrl parsed = Utilities.parseUrl("http://gsoltis.fblocal.com:9000");
    assertFalse(parsed.repoInfo.secure);

    // Hosts with the default ports are considered secure
    parsed = Utilities.parseUrl("http://gsoltis.firebaseio.com");
    assertTrue(parsed.repoInfo.secure);
  }

  @Test
  public void testUrlParsingWithEmulator() {
    ParsedUrl parsedUrl = Utilities.parseUrl("https://myns.firebaseio.com");
    parsedUrl.repoInfo.applyEmulatorSettings(new EmulatedServiceSettings("10.0.2.2", 9000));

    assertFalse(parsedUrl.repoInfo.secure);
    assertEquals(parsedUrl.repoInfo.host, "10.0.2.2:9000");
    assertEquals(parsedUrl.repoInfo.internalHost, "10.0.2.2:9000");
    assertEquals(parsedUrl.repoInfo.namespace, "myns");
  }
}
