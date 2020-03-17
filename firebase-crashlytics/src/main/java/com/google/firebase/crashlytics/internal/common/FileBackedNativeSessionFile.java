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

import com.google.firebase.crashlytics.internal.model.CrashlyticsReport;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

/** A {@link NativeSessionFile} backed by a {@link File} currently on disk. */
class FileBackedNativeSessionFile implements NativeSessionFile {

  private final File f;
  private final String name;

  FileBackedNativeSessionFile(String name, File f) {
    this.name = name;
    this.f = f;
  }

  public String getName() {
    return this.name;
  }

  @Override
  public InputStream getStream() {
    try {
      return new FileInputStream(f);
    } catch (FileNotFoundException f) {
      return null;
    }
  }

  @Override
  public CrashlyticsReport.FilesPayload.File asFilePayload() {
    byte[] bytes = asBytes();
    return bytes != null
        ? CrashlyticsReport.FilesPayload.File.builder()
            .setContents(asBytes())
            .setFilename(name)
            .build()
        : null;
  }

  private byte[] asBytes() {
    final byte[] readBuffer = new byte[8192];
    final ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try (InputStream stream = new FileInputStream(f)) {
      int read;
      while ((read = stream.read(readBuffer)) > 0) {
        bos.write(readBuffer, 0, read);
      }
      return bos.toByteArray();
    } catch (FileNotFoundException e) {
      return null;
    } catch (IOException e) {
      return null;
    }
  }
}
