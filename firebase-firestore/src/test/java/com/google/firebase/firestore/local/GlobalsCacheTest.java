// Copyright 2024 Google LLC
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

package com.google.firebase.firestore.local;

import static org.junit.Assert.assertEquals;

import com.google.protobuf.ByteString;
import java.nio.charset.StandardCharsets;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public abstract class GlobalsCacheTest {

  private Persistence persistence;
  private GlobalsCache globalsCache;

  @Before
  public void setUp() {
    persistence = getPersistence();
    globalsCache = persistence.getGlobalsCache();
  }

  @After
  public void tearDown() {
    persistence.shutdown();
  }

  abstract Persistence getPersistence();

  @Test
  public void setAndGetDbToken() {
    ByteString value = ByteString.copyFrom("TestData", StandardCharsets.UTF_8);
    globalsCache.setSessionToken(value);
    assertEquals(value, globalsCache.getSessionsToken());
  }

  @Test
  public void setAndGetEmptyDbToken() {
    globalsCache.setSessionToken(ByteString.EMPTY);
    assertEquals(ByteString.EMPTY, globalsCache.getSessionsToken());
  }
}
