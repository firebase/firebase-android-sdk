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

import static com.google.firebase.crashlytics.internal.common.CrashlyticsController.SESSION_APP_TAG;
import static com.google.firebase.crashlytics.internal.common.CrashlyticsController.SESSION_BEGIN_TAG;
import static com.google.firebase.crashlytics.internal.common.CrashlyticsController.SESSION_DEVICE_TAG;
import static com.google.firebase.crashlytics.internal.common.CrashlyticsController.SESSION_EVENT_MISSING_BINARY_IMGS_TAG;
import static com.google.firebase.crashlytics.internal.proto.ClsFileOutputStream.IN_PROGRESS_SESSION_FILE_EXTENSION;
import static com.google.firebase.crashlytics.internal.proto.ClsFileOutputStream.SESSION_FILE_EXTENSION;

import android.content.Context;
import com.google.firebase.crashlytics.internal.report.ReportUploader;
import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class TestReportFilesProvider implements ReportUploader.ReportFilesProvider {
  private final File validFiles;
  private final File invalidFiles;

  public TestReportFilesProvider(Context context) {
    final File filesDir = context.getFilesDir();
    recursiveDelete(filesDir);

    File rootFolder = new File(context.getFilesDir(), UUID.randomUUID().toString());
    rootFolder.mkdirs();

    validFiles = new File(rootFolder, UUID.randomUUID().toString());
    validFiles.mkdirs();

    invalidFiles = new File(rootFolder, UUID.randomUUID().toString());
    invalidFiles.mkdirs();
  }

  @Override
  public File[] getCompleteSessionFiles() {
    return validFiles.listFiles();
  }

  @Override
  public File[] getNativeReportFiles() {
    return new File[0];
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

  public void createTestCrashFile() throws IOException {
    new File(validFiles, createRandomSessionId() + SESSION_FILE_EXTENSION).createNewFile();
  }

  public void createInvalidCrashFilesInProgress() throws Exception {
    final String invalidSessionId = createRandomSessionId();
    new File(invalidFiles, invalidSessionId + SESSION_BEGIN_TAG + SESSION_FILE_EXTENSION)
        .createNewFile();
    new File(invalidFiles, invalidSessionId + SESSION_APP_TAG + IN_PROGRESS_SESSION_FILE_EXTENSION)
        .createNewFile();
    new File(invalidFiles, invalidSessionId + SESSION_DEVICE_TAG + SESSION_FILE_EXTENSION)
        .createNewFile();
  }

  public void createInvalidCrashFilesNoBinaryImages() throws Exception {
    final String invalidSessionId = createRandomSessionId();

    new File(invalidFiles, invalidSessionId + SESSION_BEGIN_TAG + SESSION_FILE_EXTENSION)
        .createNewFile();
    new File(
            invalidFiles,
            invalidSessionId + SESSION_EVENT_MISSING_BINARY_IMGS_TAG + SESSION_FILE_EXTENSION)
        .createNewFile();
    new File(invalidFiles, invalidSessionId + SESSION_APP_TAG + SESSION_FILE_EXTENSION)
        .createNewFile();
    new File(invalidFiles, invalidSessionId + SESSION_DEVICE_TAG + SESSION_FILE_EXTENSION)
        .createNewFile();
  }
}
