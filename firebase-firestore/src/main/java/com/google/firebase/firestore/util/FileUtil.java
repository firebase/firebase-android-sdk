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

package com.google.firebase.firestore.util;

import android.annotation.TargetApi;
import android.os.Build;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/** Contains utility methods for files in different API levels. */
public class FileUtil {

  /** Deletes a file if it exists. Only used on API levels >= 26. */
  @TargetApi(Build.VERSION_CODES.O)
  public static class DefaultFileDeleter {
    public static void delete(File file) throws IOException {
      try {
        Files.deleteIfExists(file.toPath());
      } catch (IOException e) {
        throw e;
      }
    }
  }

  /** Deletes a file if it exists. Only used on API levels < 16. */
  public static class FileDeleter {
    public static void delete(File file) throws SecurityException {
      try {
        file.delete();
      } catch (SecurityException e) {
        throw e;
      }
    }
  }
}
