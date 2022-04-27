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

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.zip.GZIPInputStream;

final class TestUtils {
  private TestUtils() {}

  public static void writeStringToFile(String s, File f) throws IOException {
    BufferedWriter writer = null;
    try {
      writer = new BufferedWriter(new FileWriter(f));
      writer.write(s);
    } finally {
      if (writer != null) {
        writer.close();
      }
    }
  }

  public static byte[] inflateGzipToRawBytes(byte[] compressed) {
    try (ByteArrayInputStream bis = new ByteArrayInputStream(compressed);
        GZIPInputStream gis = new GZIPInputStream(bis);
        ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
      int value = gis.read();
      while (value != -1) {
        bos.write(value);
        value = gis.read();
      }
      return bos.toByteArray();
    } catch (Exception e) {
      return null;
    }
  }
}
