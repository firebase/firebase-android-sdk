// Copyright 2026 Google LLC
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

package com.google.firebase.database.connection;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.firebase.database.logging.LogWrapper;
import com.google.firebase.database.logging.Logger;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class HostInfoTest {

  private static final String CONFIGURED_HOST_URL = "wss://test.firebaseio.com/.ws?ns=test&v=5";
  private static final String SERVER_HOST = "s-usc1b-nss-2073.firebaseio.com";
  private static final String SERVER_HOST_URL =
      "wss://s-usc1b-nss-2073.firebaseio.com/.ws?ns=test&v=5";

  /** Hosts that the server or the emulator can send. */
  private static final List<String> VALID_HOSTS =
      Arrays.asList(
          "s-usc1b-nss-2073.firebaseio.com",
          "x.europe-west1.firebasedatabase.app",
          "localhost:9000",
          "10.0.2.2:9000",
          "[::1]:9000");

  /**
   * Hosts that can't be used in a connection URL. With the first ones, the URL isn't a valid URI.
   * With the last ones, it is, but it doesn't name a host.
   */
  private static final List<String> INVALID_HOSTS =
      Arrays.asList(
          "bad host",
          "a|b.firebaseio.com",
          "a^b.firebaseio.com",
          "{a}.firebaseio.com",
          "a\"b.firebaseio.com",
          "a\\b.firebaseio.com",
          "a%zz.firebaseio.com",
          "",
          "test.firebaseio.com:port");

  /** Session IDs that make the connection URL an invalid URI. */
  private static final List<String> INVALID_SESSION_IDS =
      Arrays.asList("a b", "a|b", "a\"b", "{a}", "%");

  private final Logger logger = mock(Logger.class);
  private final LogWrapper logWrapper = new LogWrapper(logger, "HostInfoTest");
  private final HostInfo hostInfo = new HostInfo("test.firebaseio.com", "test", /* secure= */ true);
  private final HostInfo emulatorHostInfo =
      new HostInfo("10.0.2.2:9000", "test", /* secure= */ false);

  @Test
  public void isUsableHost_acceptsValidHosts() {
    for (String host : VALID_HOSTS) {
      assertWithMessage("host: %s", host).that(hostInfo.isUsableHost(host)).isTrue();
      assertWithMessage("emulator, host: %s", host)
          .that(emulatorHostInfo.isUsableHost(host))
          .isTrue();
    }
  }

  @Test
  public void isUsableHost_rejectsHostsThatCannotBeUsedInUrl() {
    for (String host : INVALID_HOSTS) {
      assertWithMessage("host: %s", host).that(hostInfo.isUsableHost(host)).isFalse();
    }
  }

  @Test
  public void getConnectionUrl_withoutHostOrSessionIdFromServer_usesConfiguredHost() {
    assertThat(getConnectionUrl(/* cachedHost= */ null, /* lastSessionId= */ null))
        .isEqualTo(CONFIGURED_HOST_URL);
    assertNoWarnings();
  }

  @Test
  public void getConnectionUrl_withValidHostAndSessionId_usesBoth() {
    assertThat(getConnectionUrl(SERVER_HOST, "session-id"))
        .isEqualTo(SERVER_HOST_URL + "&ls=session-id");
    assertNoWarnings();
  }

  @Test
  public void getConnectionUrl_withValidHosts_usesThem() {
    for (String host : VALID_HOSTS) {
      assertThat(getConnectionUrl(host, /* lastSessionId= */ null))
          .isEqualTo("wss://" + host + "/.ws?ns=test&v=5");
    }
    assertNoWarnings();
  }

  @Test
  public void getConnectionUrl_forEmulator_usesInsecureScheme() {
    assertThat(emulatorHostInfo.getConnectionUrl(null, null, logWrapper).toString())
        .isEqualTo("ws://10.0.2.2:9000/.ws?ns=test&v=5");
    assertThat(
            emulatorHostInfo
                .getConnectionUrl("localhost:9000", "session-id", logWrapper)
                .toString())
        .isEqualTo("ws://localhost:9000/.ws?ns=test&v=5&ls=session-id");
    assertNoWarnings();
  }

  @Test
  public void getConnectionUrl_withInvalidHost_usesConfiguredHost() {
    for (String host : INVALID_HOSTS) {
      assertWithMessage("host: %s", host)
          .that(getConnectionUrl(host, /* lastSessionId= */ null))
          .isEqualTo(CONFIGURED_HOST_URL);
    }
  }

  @Test
  public void getConnectionUrl_withInvalidHost_keepsSessionId() {
    assertThat(getConnectionUrl("bad host", "session-id"))
        .isEqualTo(CONFIGURED_HOST_URL + "&ls=session-id");
  }

  @Test
  public void getConnectionUrl_withInvalidSessionId_omitsSessionId() {
    for (String sessionId : INVALID_SESSION_IDS) {
      assertWithMessage("session ID: %s", sessionId)
          .that(getConnectionUrl(/* cachedHost= */ null, sessionId))
          .isEqualTo(CONFIGURED_HOST_URL);
    }
  }

  @Test
  public void getConnectionUrl_withInvalidSessionId_keepsHost() {
    // If this used the configured host instead, and the server there reset the connection to the
    // same host again, the client would reconnect in a tight loop.
    assertThat(getConnectionUrl(SERVER_HOST, "a b")).isEqualTo(SERVER_HOST_URL);
  }

  @Test
  public void getConnectionUrl_withInvalidHostAndSessionId_usesConfiguredHostWithoutSessionId() {
    assertThat(getConnectionUrl("a|b.firebaseio.com", "a b")).isEqualTo(CONFIGURED_HOST_URL);
  }

  @Test
  public void getConnectionUrl_withInvalidValues_logsWarningsWithoutTheValues() {
    getConnectionUrl("bad host", "bad session");

    ArgumentCaptor<String> warnings = ArgumentCaptor.forClass(String.class);
    verify(logger, times(2))
        .onLogMessage(eq(Logger.Level.WARN), anyString(), warnings.capture(), anyLong());
    for (String warning : warnings.getAllValues()) {
      assertThat(warning).doesNotContain("bad host");
      assertThat(warning).doesNotContain("bad session");
    }
  }

  private String getConnectionUrl(String cachedHost, String lastSessionId) {
    return hostInfo.getConnectionUrl(cachedHost, lastSessionId, logWrapper).toString();
  }

  private void assertNoWarnings() {
    verify(logger, never())
        .onLogMessage(eq(Logger.Level.WARN), anyString(), anyString(), anyLong());
  }
}
