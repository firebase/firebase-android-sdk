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

package com.google.firebase.perf.util;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.perf.util.Utils.stripSensitiveInfo;
import static com.google.firebase.perf.util.Utils.truncateURL;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for {@link Utils}. */
@RunWith(RobolectricTestRunner.class)
public class UtilsTest {

  @Test
  public void testStripQueryParams() {
    assertThat(stripSensitiveInfo("https://www.youtube.com/watch?v=Rub-JsjMhWY"))
        .isEqualTo("https://www.youtube.com/watch");
    assertThat(stripSensitiveInfo("https://www.youtube.com/watch"))
        .isEqualTo("https://www.youtube.com/watch");
    assertThat(
            stripSensitiveInfo(
                "https://www.google.com/webhp"
                    + "?sourceid=chrome-instant&ion=1&espv=2&ie=UTF-8#q=costco"
                    + "&rflfq=1&rlha=0&rllag=37396746,-122045385,5195&tbm=lcl"
                    + "&tbs=lf_od:-1,lf_oh:-1,lf_pqs:EAE,lf:1,lf_ui:4&*"))
        .isEqualTo("https://www.google.com/webhp");
    assertThat(
            stripSensitiveInfo(
                "https://www.youtube.com/#/?channel=mv" + "&site_id=abcd1234&no_app=1"))
        .isEqualTo("https://www.youtube.com/");
    assertThat(
            stripSensitiveInfo(
                "https://youtube.com/stream/"
                    + "D%C2%AB!%05%C2%A3(%C2%8CV%C2%90X%C2%99%EF%BF%B2%C2%B6%C3%98%C2%B1%EF%BE%BC%"
                    + "C3%A1%C3%B4H%10YG%0BVg%C3%A7%EF%BE%95%C2%A1w%C3%AFG%C3%81%C2%AF%20)i%C2%93)"
                    + "0%C3%8D%01%C2%91#?mime=true"))
        .isEqualTo(
            "https://youtube.com/stream/"
                + "D%C2%AB!%05%C2%A3(%C2%8CV%C2%90X%C2%99%EF%BF%B2%C2%B6%C3%98%C2%B1%EF%BE%BC%C3%A1"
                + "%C3%B4H%10YG%0BVg%C3%A7%EF%BE%95%C2%A1w%C3%AFG%C3%81%C2%AF%20)i%C2%93)0%C3%8D"
                + "%01%C2%91");
    assertThat(stripSensitiveInfo("sdfsds")).isEqualTo("sdfsds");
  }

  @Test
  public void testStripUserNamePassword() {
    assertThat(stripSensitiveInfo("https://username:passw0rd@www.youtube.com"))
        .isEqualTo("https://www.youtube.com/");
    assertThat(stripSensitiveInfo("https://www.youtube.com/watch"))
        .isEqualTo("https://www.youtube.com/watch");
    assertThat(stripSensitiveInfo("sdfsds")).isEqualTo("sdfsds");
  }

  @Test
  public void testStripQueryParamsAndUserInfo() {
    assertThat(stripSensitiveInfo("https://username:passw0rd@www.youtube.com/watch?v=Rub-JsjMhW"))
        .isEqualTo("https://www.youtube.com/watch");
    assertThat(stripSensitiveInfo("https://www.youtube.com/watch"))
        .isEqualTo("https://www.youtube.com/watch");
    assertThat(
            stripSensitiveInfo(
                "https://username:passw0rd@www.google.com/webhp"
                    + "?sourceid=chrome-instant&ion=1&espv=2&ie=UTF-8#q=costco"
                    + "&rflfq=1&rlha=0&rllag=37396746,-122045385,5195&tbm=lcl"
                    + "&tbs=lf_od:-1,lf_oh:-1,lf_pqs:EAE,lf:1,lf_ui:4&*"))
        .isEqualTo("https://www.google.com/webhp");
    assertThat(stripSensitiveInfo("sdfsds")).isEqualTo("sdfsds");
  }

  @Test
  public void testTruncateURL() {
    assertThat(truncateURL("http://www.a.com/", 20)).isEqualTo("http://www.a.com/");
    assertThat(truncateURL("http://www.a.com", 20)).isEqualTo("http://www.a.com");
    assertThat(truncateURL("http://www.a.com/123", 20)).isEqualTo("http://www.a.com/123");
    assertThat(truncateURL("http://www.a.com/12", 20)).isEqualTo("http://www.a.com/12");
    assertThat(truncateURL("http://www.a.com/123/", 20)).isEqualTo("http://www.a.com/123");
    assertThat(truncateURL("http://www.a.com/1234", 20)).isEqualTo("http://www.a.com");
    assertThat(truncateURL("http://www.a.com/1/234", 20)).isEqualTo("http://www.a.com/1");
  }

  @Test
  public void testBufferToInt() {
    byte[] buffer = new byte[] {(byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04};
    assertThat(Utils.bufferToInt(buffer)).isEqualTo(0x04030201);

    buffer = new byte[] {(byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04, (byte) 0x05};
    assertThat(Utils.bufferToInt(buffer)).isEqualTo(0x04030201);

    buffer = new byte[] {(byte) 0x01, (byte) 0x02};
    assertThat(Utils.bufferToInt(buffer)).isEqualTo(0x0201);

    buffer = new byte[] {};
    assertThat(Utils.bufferToInt(buffer)).isEqualTo(0);

    buffer = new byte[] {(byte) 0xFF, (byte) 0xFE, (byte) 0x03, (byte) 0x04};
    assertThat(Utils.bufferToInt(buffer)).isEqualTo(0x0403FEFF);
  }
}
