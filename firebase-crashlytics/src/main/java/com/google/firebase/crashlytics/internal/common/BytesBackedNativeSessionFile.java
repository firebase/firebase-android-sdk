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
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPOutputStream;

/** A {@link NativeSessionFile} backed by a byte array. */
class BytesBackedNativeSessionFile implements NativeSessionFile {
  @Nullable private final byte[] bytes;
  @NonNull private final String dataTransportFilename;
  @NonNull private final String reportsEndpointFilename;

  BytesBackedNativeSessionFile(
      @NonNull String dataTransportFilename,
      @NonNull String reportsEndpointFilename,
      @Nullable byte[] bytes) {
    this.dataTransportFilename = dataTransportFilename;
    this.reportsEndpointFilename = reportsEndpointFilename;
    this.bytes = bytes;
  }

  @Override
  @NonNull
  public String getReportsEndpointFilename() {
    return this.reportsEndpointFilename;
  }

  @Override
  @Nullable
  public InputStream getStream() {
    return isEmpty() ? null : new ByteArrayInputStream(bytes);
  }

  @Override
  @Nullable
  public CrashlyticsReport.FilesPayload.File asFilePayload() {
    byte[] gzippedBytes = asGzippedBytes();
    return gzippedBytes == null
        ? null
        : CrashlyticsReport.FilesPayload.File.builder()
            .setContents(gzippedBytes)
            .setFilename(dataTransportFilename)
            .build();
  }

  private boolean isEmpty() {
    return bytes == null || bytes.length == 0;
  }

  @Nullable
  private byte[] asGzippedBytes() {
    if (isEmpty()) {
      return null;
    }

    try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
        GZIPOutputStream gos = new GZIPOutputStream(bos)) {
      gos.write(bytes);
      gos.finish();
      return bos.toByteArray();
    } catch (IOException e) {
      return null;
    }
  }
}
