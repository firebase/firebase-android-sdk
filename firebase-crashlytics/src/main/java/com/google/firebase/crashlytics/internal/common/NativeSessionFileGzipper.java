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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/** Copies {@link NativeSessionFile} into gzipped files within a provided path. */
class NativeSessionFileGzipper {

  static void processNativeSessions(File nativeSessionDirectory, List<NativeSessionFile> streams) {

    for (NativeSessionFile stream : streams) {
      InputStream inputStream = null;
      try {
        inputStream = stream.getStream();
        if (inputStream == null) {
          continue;
        }
        gzipInputStream(inputStream, new File(nativeSessionDirectory, stream.getName()));
      } catch (Exception e) {
        // Skip invalid files.
      } finally {
        CommonUtils.closeQuietly(inputStream);
      }
    }
  }

  private static void gzipInputStream(@Nullable InputStream input, @NonNull File output)
      throws IOException {
    if (input == null) {
      return;
    }
    byte[] buffer = new byte[1024];
    GZIPOutputStream gos = null;
    try {
      gos = new GZIPOutputStream(new FileOutputStream(output));

      int read;

      while ((read = input.read(buffer)) > 0) {
        gos.write(buffer, 0, read);
      }

      gos.finish();
    } finally {
      CommonUtils.closeQuietly(gos);
    }
  }
}
