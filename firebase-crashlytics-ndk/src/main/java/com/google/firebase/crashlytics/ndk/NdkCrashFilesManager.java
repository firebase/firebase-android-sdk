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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.File;

class NdkCrashFilesManager implements CrashFilesManager {

  private final File rootPath;

  NdkCrashFilesManager(File rootPath) {
    this.rootPath = rootPath;
  }

  @Override
  public boolean hasSessionFileDirectory(String sessionId) {
    return new File(rootPath, sessionId).exists();
  }

  @Override
  public File getSessionFileDirectory(String sessionId) {
    // TODO: Have this throw IOException if prepare returns null
    return prepareDirectory(new File(rootPath, sessionId));
  }

  @Override
  public void deleteSessionFilesDirectory(String sessionId) {
    recursiveDelete(new File(rootPath, sessionId));
  }

  @Nullable
  private static File prepareDirectory(File file) {
    if (file != null) {
      if (file.exists() || file.mkdirs()) {
        return file;
      }
    }
    return null;
  }

  private static void recursiveDelete(@NonNull File f) {
    if (f.isDirectory()) {
      for (File s : f.listFiles()) {
        recursiveDelete(s);
      }
    }
    f.delete();
  }
}
