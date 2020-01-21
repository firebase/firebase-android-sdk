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

package com.google.firebase.crashlytics.internal.persistence;

import android.os.Environment;
import com.google.firebase.crashlytics.internal.CrashlyticsTestCase;
import java.io.File;

public class FileStoreImplTest extends CrashlyticsTestCase {
  FileStoreImpl fileStore;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    fileStore = new FileStoreImpl(getContext());
  }

  public void testGetFilesDir() {
    verifyFile(fileStore.getFilesDir());
  }

  public void testPrepare() {
    verifyFile(fileStore.prepare(new File(getContext().getFilesDir(), "FileStoreImplTest/")));
  }

  public void testisExternalStorageAvailable() {
    final String state = Environment.getExternalStorageState();
    assertEquals(Environment.MEDIA_MOUNTED.equals(state), fileStore.isExternalStorageAvailable());
  }

  private void verifyFile(File file) {
    assertNotNull(file);
    assertTrue(file.exists());
  }
}
