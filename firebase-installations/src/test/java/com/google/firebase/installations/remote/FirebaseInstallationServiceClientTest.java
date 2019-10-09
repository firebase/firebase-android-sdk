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

package com.google.firebase.installations.remote;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Tests for {@link FirebaseInstallationServiceClient}. */
@RunWith(RobolectricTestRunner.class)
public class FirebaseInstallationServiceClientTest {

  private final String TEST_EXPIRATION_TIMESTAMP = "604800s";
  private final long TEST_EXPIRATION_IN_SECS = 604800;
  private final String INCORRECT_EXPIRATION_TIMESTAMP = "2345";

  @Test
  public void parseTokenExpirationTimestamp_successful() {
    long actual =
        FirebaseInstallationServiceClient.parseTokenExpirationTimestamp(TEST_EXPIRATION_TIMESTAMP);

    assertWithMessage("Exception status doesn't match")
        .that(actual)
        .isEqualTo(TEST_EXPIRATION_IN_SECS);
  }

  @Test
  public void parseTokenExpirationTimestamp_failed() {
    try {
      FirebaseInstallationServiceClient.parseTokenExpirationTimestamp(
          INCORRECT_EXPIRATION_TIMESTAMP);
      fail("Parsing token expiration timestamp failed.");
    } catch (IllegalArgumentException expected) {
      assertThat(expected)
          .hasMessageThat()
          .isEqualTo(FirebaseInstallationServiceClient.PARSING_EXPIRATION_TIME_ERROR_MESSAGE);
    }
  }
}
