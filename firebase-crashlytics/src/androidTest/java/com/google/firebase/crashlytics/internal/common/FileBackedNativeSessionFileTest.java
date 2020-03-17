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

import android.content.Context;
import com.google.firebase.crashlytics.internal.CrashlyticsTestCase;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import org.junit.Test;

public class FileBackedNativeSessionFileTest extends CrashlyticsTestCase {
  byte[] testContents = {0, 2, 20, 10};
  byte[] emptyContents = {};
  File testFile;
  File emptyFile;
  File missingFile;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final Context context = getContext();
    testFile = new File(context.getFilesDir(), "testFile");
    try (FileOutputStream fout = new FileOutputStream(testFile);
        ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
      stream.write(testContents);
      stream.writeTo(fout);
    }
    emptyFile = new File(context.getFilesDir(), "emptyFile");
    emptyFile.createNewFile();
    missingFile = new File(context.getFilesDir(), "missingFile");
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    testFile.delete();
    emptyFile.delete();
  }

  @Test
  public void testAsStream_convertsToStream() throws IOException {
    FileBackedNativeSessionFile nativeSessionFile =
        new FileBackedNativeSessionFile("file", testFile);
    byte[] readBytes = new byte[4];
    nativeSessionFile.getStream().read(readBytes);
    assertArrayEquals(testContents, readBytes);
  }

  @Test
  public void testAsStreamWhenEmpty_returnsEmpty() throws IOException {
    FileBackedNativeSessionFile nativeSessionFile =
        new FileBackedNativeSessionFile("file", emptyFile);
    byte[] readBytes = new byte[0];
    nativeSessionFile.getStream().read(readBytes);
    assertArrayEquals(emptyContents, readBytes);
  }

  @Test
  public void testAsStreamWhenMissing_returnsNull() {
    FileBackedNativeSessionFile nativeSessionFile =
        new FileBackedNativeSessionFile("file", missingFile);
    assertNull(nativeSessionFile.getStream());
  }

  @Test
  public void testAsFilePayload_convertsToFilePayload() {
    FileBackedNativeSessionFile nativeSessionFile =
        new FileBackedNativeSessionFile("file", testFile);
    CrashlyticsReport.FilesPayload.File filesPayload = nativeSessionFile.asFilePayload();
    assertNotNull(filesPayload);
    assertArrayEquals(testContents, filesPayload.getContents());
    assertEquals("file", filesPayload.getFilename());
  }

  @Test
  public void testAsFilePayloadWhenEmpty_returnsEmptyPayload() {
    FileBackedNativeSessionFile nativeSessionFile =
        new FileBackedNativeSessionFile("file", emptyFile);
    CrashlyticsReport.FilesPayload.File filesPayload = nativeSessionFile.asFilePayload();
    assertNotNull(filesPayload);
    assertArrayEquals(emptyContents, filesPayload.getContents());
    assertEquals("file", filesPayload.getFilename());
  }

  @Test
  public void testAsFilePayloadWhenMissing_convertsToNull() {
    FileBackedNativeSessionFile nativeSessionFile =
        new FileBackedNativeSessionFile("file", missingFile);
    assertNull(nativeSessionFile.asFilePayload());
  }
}
