/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.crashlytics.buildtools.utils;

import com.google.common.io.CharStreams;
import com.google.firebase.crashlytics.buildtools.TestUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.zip.GZIPInputStream;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FileUtilsTest {

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  private static final File WORKING_DIR = new File(TestUtils.TEST_OUTPUT_DIR, "fileUtilsTest");

  @Before
  public void setupFilesUnderTest() throws Exception {
    TestUtils.prepareTestDirectory();
    FileUtils.verifyDirectory(WORKING_DIR);
  }

  @Test
  public void testVerifyDirectory() throws Exception {

    // make sure the directory doesn't exit
    File testDir = new File(TestUtils.TEST_OUTPUT_DIR, "testdir");
    if (testDir.exists()) {
      testDir.delete();
    }

    Assert.assertFalse(testDir.exists());
    FileUtils.verifyDirectory(testDir);
    Assert.assertTrue(testDir.exists());

    // create a temporary file, verify again, and check the file still exists
    File tempFile = File.createTempFile("temp", "txt", testDir);
    FileUtils.verifyDirectory(testDir);
    Assert.assertTrue(testDir.exists());
    Assert.assertTrue(tempFile.exists());

    // remove the testDir and replace with a file to make sure an exception gets thrown
    tempFile.delete();
    testDir.delete();

    testDir.createNewFile();
    boolean caughtException = false;

    try {
      FileUtils.verifyDirectory(testDir);
    } catch (IOException e) {
      caughtException = true;
    }
    Assert.assertTrue(caughtException);
    testDir.delete();
  }

  @Test
  public void testGZip() throws Exception {
    File sourceFile = new File(WORKING_DIR, "zipTest.txt");
    if (sourceFile.exists()) {
      sourceFile.delete();
    }
    StringBuilder contents = TestUtils.createDummyFile(sourceFile);

    File destFile = new File(WORKING_DIR, "zipTest.zip");
    if (destFile.exists()) {
      destFile.delete();
    }
    FileUtils.gZipFile(sourceFile, destFile);

    InputStream unzippedStream = new GZIPInputStream(new FileInputStream(destFile));
    String unzippedContents = CharStreams.toString(new InputStreamReader(unzippedStream));

    Assert.assertEquals(contents.toString(), unzippedContents);
  }
}
