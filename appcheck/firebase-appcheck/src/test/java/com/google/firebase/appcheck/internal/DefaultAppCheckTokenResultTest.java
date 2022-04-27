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

package com.google.firebase.appcheck.internal;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.firebase.FirebaseException;
import java.util.Base64;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Tests for {@link DefaultAppCheckTokenResult} */
@RunWith(RobolectricTestRunner.class)
public class DefaultAppCheckTokenResultTest {
  private static final String TOKEN = "token";
  private static final long TIME_TO_LIVE = 60L;
  private static final String ERROR = "error";

  @Test
  public void testDummyToken() throws Exception {
    JSONObject jsonObject = new JSONObject();
    jsonObject.put("error", "UNKNOWN_ERROR");
    assertThat(DefaultAppCheckTokenResult.DUMMY_TOKEN)
        .isEqualTo(Base64.getUrlEncoder().encodeToString(jsonObject.toString().getBytes()));
  }

  @Test
  public void testConstructFromAppCheckToken_success() {
    DefaultAppCheckTokenResult defaultAppCheckTokenResult =
        DefaultAppCheckTokenResult.constructFromAppCheckToken(
            new DefaultAppCheckToken(TOKEN, TIME_TO_LIVE));

    assertThat(defaultAppCheckTokenResult.getToken()).isEqualTo(TOKEN);
    assertThat(defaultAppCheckTokenResult.getError()).isNull();
  }

  @Test
  public void testConstructFromError_success() {
    DefaultAppCheckTokenResult defaultAppCheckTokenResult =
        DefaultAppCheckTokenResult.constructFromError(new FirebaseException(ERROR));

    assertThat(defaultAppCheckTokenResult.getToken())
        .isEqualTo(DefaultAppCheckTokenResult.DUMMY_TOKEN);
    assertThat(defaultAppCheckTokenResult.getError().getMessage()).isEqualTo(ERROR);
  }

  @Test
  public void testConstructFromAppCheckToken_nullToken_throwsNullPointerException() {
    assertThrows(
        NullPointerException.class,
        () -> DefaultAppCheckTokenResult.constructFromAppCheckToken(null));
  }

  @Test
  public void testConstructFromAppCheckToken_emptyRawToken_throwsIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            DefaultAppCheckTokenResult.constructFromAppCheckToken(
                new DefaultAppCheckToken(/* tokenJwt= */ "", TIME_TO_LIVE)));
  }

  @Test
  public void testConstructFromError_nullError_throwsNullPointerException() {
    assertThrows(
        NullPointerException.class, () -> DefaultAppCheckTokenResult.constructFromError(null));
  }
}
