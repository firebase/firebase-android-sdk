// Copyright 2020 Google LLC
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

package com.google.firebase.perf.network;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.perf.network.NetworkRequestMetricBuilderUtil.isAllowedUserAgent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for {@link NetworkRequestMetricBuilderUtil}. */
@RunWith(RobolectricTestRunner.class)
public class NetworkRequestMetricBuilderUtilTest {

  @Test
  public void isAllowedUserAgent_nullValue_returnsTrue() {
    assertThat(isAllowedUserAgent(null)).isTrue();
  }

  @Test
  public void isAllowedUserAgent_randomString_returnsTrue() {
    assertThat(isAllowedUserAgent("this is a random string")).isTrue();
    assertThat(isAllowedUserAgent("thisIsARandomString")).isTrue();
  }

  @Test
  public void isAllowedUserAgent_popularUserAgents_returnsTrue() {
    // From https://www.networkinghowtos.com/howto/common-user-agent-list/
    // Google Chrome
    assertThat(
            isAllowedUserAgent(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.36"))
        .isTrue();
    // Mozilla Firefox
    assertThat(
            isAllowedUserAgent(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:53.0) Gecko/20100101 Firefox/53.0"))
        .isTrue();
    // Microsoft Edge
    assertThat(
            isAllowedUserAgent(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/51.0.2704.79 Safari/537.36 Edge/14.14393"))
        .isTrue();
    // HTC
    assertThat(
            isAllowedUserAgent(
                "Mozilla/5.0 (Linux; Android 7.0; HTC 10 Build/NRD90M) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.83 Mobile Safari/537.36"))
        .isTrue();

    // From https://developer.chrome.com/multidevice/user-agent
    // Chrome User-Agent string on a Galaxy Nexus
    assertThat(
            isAllowedUserAgent(
                "Mozilla/5.0 (Linux; Android 4.0.4; Galaxy Nexus Build/IMM76B) AppleWebKit/535.19 (KHTML, like Gecko) Chrome/18.0.1025.133 Mobile Safari/535.19"))
        .isTrue();
    // WebView User-Agent in KitKat to Lollipop
    assertThat(
            isAllowedUserAgent(
                "Mozilla/5.0 (Linux; Android 4.4; Nexus 5 Build/_BuildID_) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/30.0.0.0 Mobile Safari/537.36"))
        .isTrue();
    // WebView User-Agent in Lollipop and Above
    assertThat(
            isAllowedUserAgent(
                "Mozilla/5.0 (Linux; Android 5.1.1; Nexus 5 Build/LMY48B; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/43.0.2357.65 Mobile Safari/537.36"))
        .isTrue();

    // From https://developers.whatismybrowser.com/useragents/explore/operating_system_name/android/
    // Android Browser
    assertThat(
            isAllowedUserAgent(
                "Mozilla/5.0 (Linux; U; Android 2.2) AppleWebKit/533.1 (KHTML, like Gecko) Version/4.0 Mobile Safari/533.1"))
        .isTrue();
    assertThat(isAllowedUserAgent("Dalvik/2.1.0 (Linux; U; Android 7.1.2; AFTA Build/NS6264) CTV"))
        .isTrue();
    // Google WebLight Proxy
    assertThat(
            isAllowedUserAgent(
                "Mozilla/5.0 (Linux; Android 4.2.1; en-us; Nexus 5 Build/JOP40D) AppleWebKit/535.19 (KHTML, like Gecko; googleweblight) Chrome/38.0.1025.166 Mobile Safari/535.19"))
        .isTrue();
  }

  @Test
  public void isAllowedUserAgent_exactFlgMatchWithDottedVersion_returnsFalse() {
    assertThat(isAllowedUserAgent("datatransport/1.2.3 android/")).isFalse();
  }

  @Test
  public void isAllowedUserAgent_exactFlgMatchWithoutDottedVersion_returnsFalse() {
    assertThat(isAllowedUserAgent("datatransport/v12 android/")).isFalse();
  }

  @Test
  public void isAllowedUserAgent_flgMatchWithRandomVersion_returnsFalse() {
    assertThat(isAllowedUserAgent("datatransport/1.2-3.4 android/")).isFalse();
    assertThat(isAllowedUserAgent("datatransport/randomVersion android/")).isFalse();
  }

  @Test
  public void isAllowedUserAgent_flgMatchWithSpaceDelimitedRandomVersion_returnsTrue() {
    assertThat(isAllowedUserAgent("datatransport/1.2 3.4 android/")).isTrue();
    assertThat(isAllowedUserAgent("datatransport/ 1.2.3 android/")).isTrue();
    assertThat(isAllowedUserAgent("datatransport/random Version android/")).isTrue();
    assertThat(isAllowedUserAgent("datatransport/ randomVersion android/")).isTrue();
  }

  @Test
  public void isAllowedUserAgent_flgMatchButMissingVersion_returnsTrue() {
    assertThat(isAllowedUserAgent("datatransport/ android/")).isTrue();
  }

  @Test
  public void isAllowedUserAgent_flgMatchButMissingFirstForwardSlash_returnsTrue() {
    assertThat(isAllowedUserAgent("datatransport1.2.3 android/")).isTrue();
    assertThat(isAllowedUserAgent("datatransport 1.2.3 android/")).isTrue();
  }

  @Test
  public void isAllowedUserAgent_flgMatchButMissingLastForwardSlash_returnsTrue() {
    assertThat(isAllowedUserAgent("datatransport/1.2.3 android")).isTrue();
    assertThat(isAllowedUserAgent("datatransport/1.2.3 android ")).isTrue();
  }

  @Test
  public void isAllowedUserAgent_flgSubstringMatchWithoutSpaces_returnsTrue() {
    assertThat(isAllowedUserAgent("datatransport/1.2.3 android/randomString")).isTrue();
    assertThat(isAllowedUserAgent("datatransport/1.2.3 android/a random string")).isTrue();

    assertThat(isAllowedUserAgent("randomStringdatatransport/1.2.3 android/")).isTrue();
    assertThat(isAllowedUserAgent("a random stringdatatransport/1.2.3 android/")).isTrue();
  }

  @Test
  public void isAllowedUserAgent_flgSubstringMatchWithSpaces_returnsFalse() {
    assertThat(isAllowedUserAgent("datatransport/1.2.3 android/ randomString")).isFalse();
    assertThat(isAllowedUserAgent("datatransport/1.2.3 android/ a random string")).isFalse();

    assertThat(isAllowedUserAgent("randomString datatransport/1.2.3 android/")).isFalse();
    assertThat(isAllowedUserAgent("a random string datatransport/1.2.3 android/")).isFalse();

    assertThat(isAllowedUserAgent("randomString datatransport/1.2.3 android/ randomString"))
        .isFalse();
    assertThat(isAllowedUserAgent("a random string datatransport/1.2.3 android/ a random string"))
        .isFalse();
  }
}
