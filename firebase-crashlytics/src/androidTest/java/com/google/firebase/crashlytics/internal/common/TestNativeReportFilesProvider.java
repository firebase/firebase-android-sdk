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

import android.content.Context;
import com.google.firebase.crashlytics.internal.report.ReportUploader;
import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class TestNativeReportFilesProvider implements ReportUploader.ReportFilesProvider {
  private final File validFiles;

  public TestNativeReportFilesProvider(Context context) {
    final File filesDir = context.getFilesDir();
    recursiveDelete(filesDir);

    File rootFolder = new File(context.getFilesDir(), UUID.randomUUID().toString());
    rootFolder.mkdirs();

    validFiles = new File(rootFolder, UUID.randomUUID().toString());
    validFiles.mkdirs();
  }

  @Override
  public File[] getCompleteSessionFiles() {
    return new File[0];
  }

  @Override
  public File[] getNativeReportFiles() {
    return validFiles.listFiles();
  }

  private static String createRandomSessionId() {
    return UUID.randomUUID().toString().substring(0, 35);
  }

  private static void recursiveDelete(File file) {
    if (file.isDirectory()) {
      for (File f : file.listFiles()) {
        recursiveDelete(f);
      }
    }
    file.delete();
  }

  public void createTestCrashDirectory() throws IOException {
    new File(validFiles, createRandomSessionId()).mkdirs();
  }
}
