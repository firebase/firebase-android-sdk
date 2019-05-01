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
import android.os.Build.VERSION;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Deletes the given file if it exists. Unlike {@link java.io.File#delete}, deleting a file that
 * does not exist is not considered an error.
 *
 * @throws IOException if the deletion fails. On devices at API level 26 or greater, details about
 *     the cause of the failure will be in the error message. On older devices, the error message
 *     will be generic.
 */
public class FileUtil {
  public static void delete(File file) throws IOException {
    if (VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      DefaultFileDeleter.delete(file);
    } else {
      LegacyFileDeleter.delete(file);
    }
  }

  /** Deletes a file if it exists. Only used on API levels >= 26. */
  @TargetApi(Build.VERSION_CODES.O)
  private static class DefaultFileDeleter {
    public static void delete(File file) throws IOException {
      try {
        Files.deleteIfExists(file.toPath());
      } catch (IOException e) {
        throw new IOException("Failed to delete file " + file + ": " + e);
      }
    }
  }

  /** Deletes a file if it exists. Only used on API levels < 26. */
  private static class LegacyFileDeleter {
    public static void delete(File file) throws IOException {
      boolean fileDeleted = file.delete();
      if (!fileDeleted && file.exists()) {
        throw new IOException("Failed to delete file " + file);
      }
    }
  }
}
