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

package com.google.firebase.crashlytics.ndk;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import junit.framework.TestCase;

public class FirebaseCrashlyticsNdkTest extends TestCase {

  private FirebaseCrashlyticsNdk nativeComponent;

  private NativeComponentController mockController;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    mockController = mock(NativeComponentController.class);
    nativeComponent = new FirebaseCrashlyticsNdk(mockController);
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  public void testHasCrashDataForSession() {
    final String sessionId = "test";
    when(mockController.hasCrashDataForSession(sessionId)).thenReturn(true);
    assertTrue(nativeComponent.hasCrashDataForSession("test"));
  }

  public void testHasCrashDataForSession_noCrashDataReturnsFalse() {
    final String sessionId = "test";
    when(mockController.hasCrashDataForSession(sessionId)).thenReturn(false);
    assertFalse(nativeComponent.hasCrashDataForSession("test"));
  }

  public void testHasCrashDataForSession_nullSessionIdReturnsFalse() {
    assertFalse(nativeComponent.hasCrashDataForSession(null));
  }
}
