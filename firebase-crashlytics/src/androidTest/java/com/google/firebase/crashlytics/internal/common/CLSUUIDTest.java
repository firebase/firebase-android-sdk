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

package com.google.firebase.crashlytics.internal.common;

import static com.google.common.truth.Truth.assertThat;

import java.util.ArrayList;
import org.junit.Test;

public class CLSUUIDTest {

  /** Basic test of the CLSUUID string value. */
  @Test
  public void getSessionId() {
    String sessionId = new CLSUUID().getSessionId();
    assertThat(sessionId).isNotNull(); // The session id should not be null
    assertThat(sessionId).hasLength(32); // The session id should be 32 chars long
  }

  /** Test that we don't get duplicate CLSUUID string values in a set of 100 uuid generated. */
  @Test
  public void sessionIdsInOrder() {
    ArrayList<String> sessionIds = new ArrayList<>();

    // Put 100 CLSUUID string values into a list.
    for (int i = 0; i < 100; i++) {
      sessionIds.add(new CLSUUID().getSessionId());
    }

    assertThat(sessionIds).isInOrder();
    assertThat(sessionIds).containsNoDuplicates();
  }

  /** Test that we pad session ids properly. */
  @Test
  public void sessionIdsArePadded() {
    String sessionId1 = new CLSUUID().getSessionId();
    String sessionId2 = new CLSUUID().getSessionId();

    assertThat(sessionId1.substring(20)).isEqualTo(sessionId2.substring(20));
  }
}
