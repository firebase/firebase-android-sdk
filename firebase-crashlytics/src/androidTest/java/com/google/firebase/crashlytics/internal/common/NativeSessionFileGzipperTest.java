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

import static java.util.Arrays.stream;

import android.content.Context;
import com.google.firebase.crashlytics.internal.CrashlyticsTestCase;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.mockito.internal.util.collections.Sets;

@SuppressWarnings("ResultOfMethodCallIgnored") // Convenient use of files.
public class NativeSessionFileGzipperTest extends CrashlyticsTestCase {
  byte[] testContents = {0, 2, 20, 10};
  File testFile;
  File missingFile;
  File gzipDir;

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
    File baseDirectory = context.getFilesDir();
    missingFile = new File(baseDirectory, "missingFile");
    gzipDir = new File(baseDirectory, "gzip");
    gzipDir.mkdirs();
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    File[] gzipFiles = gzipDir.listFiles();
    if (gzipFiles != null) {
      stream(gzipFiles).forEach(File::delete);
    }
  }

  @Test
  public void testProcessNativeSessions_putsFilesInCorrectLocation() {
    String fileBackedSessionName = "file";
    String byteBackedSessionName = "byte";
    FileBackedNativeSessionFile fileSession =
        new FileBackedNativeSessionFile("not_applicable", fileBackedSessionName, testFile);
    BytesBackedNativeSessionFile byteSession =
        new BytesBackedNativeSessionFile("not_applicable", byteBackedSessionName, testContents);
    List<NativeSessionFile> files = Arrays.asList(fileSession, byteSession);
    NativeSessionFileGzipper.processNativeSessions(gzipDir, files);

    File[] gzipFiles = gzipDir.listFiles();
    assertNotNull(gzipFiles);
    assertEquals(
        Sets.newSet(
            new File(gzipDir, fileBackedSessionName), new File(gzipDir, byteBackedSessionName)),
        Sets.newSet(gzipFiles));
  }

  @Test
  public void testProcessNativeSessionsWhenDataIsNull_putsFilesInCorrectLocation() {
    String fileBackedSessionName = "file";
    String byteBackedSessionName = "byte";
    FileBackedNativeSessionFile fileSession =
        new FileBackedNativeSessionFile("not_applicable", fileBackedSessionName, missingFile);
    BytesBackedNativeSessionFile byteSession =
        new BytesBackedNativeSessionFile("not_applicable", byteBackedSessionName, testContents);
    List<NativeSessionFile> files = Arrays.asList(fileSession, byteSession);
    NativeSessionFileGzipper.processNativeSessions(gzipDir, files);

    File[] gzipFiles = gzipDir.listFiles();
    assertNotNull(gzipFiles);
    assertEquals(Sets.newSet(new File(gzipDir, byteBackedSessionName)), Sets.newSet(gzipFiles));
  }
}
