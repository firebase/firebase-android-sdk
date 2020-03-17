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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.google.firebase.crashlytics.internal.model.CrashlyticsReport;
import java.io.IOException;
import org.junit.Test;

public class BytesBackedNativeSessionFileTest {
  byte[] testBytes = {0, 2, 20, 10};
  byte[] emptyBytes = {};

  @Test
  public void testAsStream_convertsToStream() throws IOException {
    BytesBackedNativeSessionFile nativeSessionFile =
        new BytesBackedNativeSessionFile("file", testBytes);
    byte[] readBytes = new byte[4];
    nativeSessionFile.getStream().read(readBytes);
    assertArrayEquals(testBytes, readBytes);
  }

  @Test
  public void testAsStreamWhenEmpty_returnsNull() {
    BytesBackedNativeSessionFile nativeSessionFile =
        new BytesBackedNativeSessionFile("file", emptyBytes);
    assertNull(nativeSessionFile.getStream());
  }

  @Test
  public void testAsFilePayload_convertsToFilePayload() {
    BytesBackedNativeSessionFile nativeSessionFile =
        new BytesBackedNativeSessionFile("file", testBytes);
    CrashlyticsReport.FilesPayload.File filesPayload = nativeSessionFile.asFilePayload();
    assertNotNull(filesPayload);
    assertArrayEquals(testBytes, filesPayload.getContents());
    assertEquals("file", filesPayload.getFilename());
  }

  @Test
  public void testAsFilePayloadWhenEmpty_convertsToNull() {
    BytesBackedNativeSessionFile nativeSessionFile =
        new BytesBackedNativeSessionFile("file", emptyBytes);
    assertNull(nativeSessionFile.asFilePayload());
  }
}
