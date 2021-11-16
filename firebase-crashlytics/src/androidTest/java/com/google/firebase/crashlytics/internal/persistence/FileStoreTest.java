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

import com.google.firebase.crashlytics.internal.CrashlyticsTestCase;
import java.io.File;

public class FileStoreTest extends CrashlyticsTestCase {
  FileStore fileStore;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    fileStore = new FileStore(getContext());
  }

  public void testGetFilesDir() {
    verifyFile(fileStore.getCrashlyticsRootDir());
  }

  public void testPrepare() {
    verifyFile(fileStore.prepare(new File(getContext().getFilesDir(), "FileStoreTest/")));
  }

  private void verifyFile(File file) {
    assertNotNull(file);
    assertTrue(file.exists());
  }
}
