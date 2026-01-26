/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.crashlytics.buildtools;

import static org.junit.Assert.assertThrows;

import org.junit.Test;

public final class CommandLineHelperTest {

  @Test
  public void validateGoogleAppId_validAppId_doesNotThrow() {
    CommandLineHelper.validateGoogleAppId("1:123:android:123abc");
    // Does now throw, test is successful.
  }

  @Test
  public void validateGoogleAppId_malformedAppId_throwsIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class,
        () -> CommandLineHelper.validateGoogleAppId("malformedAppId"));
  }
}
