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

package com.google.firebase.database.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.base.Splitter;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.IntegrationTestHelpers;
import org.junit.Test;

@org.junit.runner.RunWith(AndroidJUnit4.class)
public class AndroidPlatformTest {

  @Test
  public void userAgentHasCorrectParts() {
    Context cfg = IntegrationTestHelpers.getContext(0);
    cfg.freeze();
    String userAgent = cfg.getUserAgent();
    Object[] parts = Splitter.on('/').splitToList(userAgent).toArray();
    assertEquals(5, parts.length);
    assertEquals("Firebase", parts[0]); // Firebase
    assertEquals(Constants.WIRE_PROTOCOL_VERSION, parts[1]); // Wire protocol version
    assertEquals(FirebaseDatabase.getSdkVersion(), parts[2]); // SDK version
    assertTrue(parts[4].toString().contains("Android")); // "OS" => Android
  }
}
