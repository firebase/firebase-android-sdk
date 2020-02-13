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

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import java.io.File;
import java.io.IOException;
import java.util.UUID;
import junit.framework.TestCase;

public class BreakpadControllerTest extends TestCase {

  private BreakpadController controller;

  private Context context;
  private NativeApi mockNativeApi;
  private CrashFilesManager mockFilesManager;

  private File testFilesDirectory;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    context = ApplicationProvider.getApplicationContext();
    mockNativeApi = mock(NativeApi.class);
    mockFilesManager = mock(CrashFilesManager.class);
    controller = new BreakpadController(context, mockNativeApi, mockFilesManager);
  }

  @Override
  protected void tearDown() throws Exception {
    recursiveDelete(testFilesDirectory);
    super.tearDown();
  }

  private static void recursiveDelete(File file) {
    if (file == null) {
      return;
    }
    if (file.isDirectory()) {
      for (File f : file.listFiles()) {
        recursiveDelete(f);
      }
    }
    file.delete();
  }

  private boolean setupTestFilesDirectory() {
    // For each test case, create a new, random subdirectory to guarantee a clean slate for file
    // manipulation.
    testFilesDirectory = new File(context.getFilesDir(), UUID.randomUUID().toString());
    return testFilesDirectory.mkdirs();
  }

  public void testHasCrashDataForSession() throws IOException {
    assertTrue(setupTestFilesDirectory());
    assertTrue(new File(testFilesDirectory, "crash.dmp").createNewFile());
    final String sessionId = "test";
    when(mockFilesManager.hasSessionFileDirectory(sessionId)).thenReturn(true);
    when(mockFilesManager.getSessionFileDirectory(sessionId)).thenReturn(testFilesDirectory);
    assertTrue(controller.hasCrashDataForSession("test"));
  }

  public void testHasCrashDataForSession_noCrashDataReturnsFalse() {
    final String sessionId = "test";
    when(mockFilesManager.hasSessionFileDirectory(sessionId)).thenReturn(false);
    assertFalse(controller.hasCrashDataForSession("test"));
  }
}
