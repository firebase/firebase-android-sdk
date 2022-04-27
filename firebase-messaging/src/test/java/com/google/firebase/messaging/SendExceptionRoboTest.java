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
package com.google.firebase.messaging;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class SendExceptionRoboTest {

  @Test
  public void testNull() {
    SendException e = new SendException(null);
    assertEquals(SendException.ERROR_UNKNOWN, e.getErrorCode());
    assertNull(e.getMessage());
  }

  @Test
  public void testInvalidParameters() {
    SendException e = new SendException("INVALID_PARAMETERS");
    assertEquals(SendException.ERROR_INVALID_PARAMETERS, e.getErrorCode());
    assertEquals("INVALID_PARAMETERS", e.getMessage());
  }

  @Test
  public void testMissingTo() {
    SendException e = new SendException("missing_to");
    assertEquals(SendException.ERROR_INVALID_PARAMETERS, e.getErrorCode());
    assertEquals("missing_to", e.getMessage());
  }

  @Test
  public void testSize() {
    SendException e = new SendException("MessageTooBig");
    assertEquals(SendException.ERROR_SIZE, e.getErrorCode());
    assertEquals("MessageTooBig", e.getMessage());
  }

  @Test
  public void testTtlExceeded() {
    SendException e = new SendException("SERVICE_NOT_AVAILABLE");
    assertEquals(SendException.ERROR_TTL_EXCEEDED, e.getErrorCode());
    assertEquals("SERVICE_NOT_AVAILABLE", e.getMessage());
  }

  @Test
  public void testTooManyMessages() {
    SendException e = new SendException("TooManyMessages");
    assertEquals(SendException.ERROR_TOO_MANY_MESSAGES, e.getErrorCode());
    assertEquals("TooManyMessages", e.getMessage());
  }
}
