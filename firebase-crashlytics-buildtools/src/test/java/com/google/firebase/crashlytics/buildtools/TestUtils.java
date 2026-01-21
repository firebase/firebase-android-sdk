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

package com.google.firebase.crashlytics.buildtools;

import com.google.common.io.Files;
import com.google.firebase.crashlytics.buildtools.utils.FileUtils;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedList;
import java.util.List;

/** Utilities used while testing DevTools. */
public class TestUtils {

  public static final File TEST_OUTPUT_DIR = Files.createTempDir();

  /** Creates the test output directory if it doesn't exist, otherwise deletes all contents. */
  public static void prepareTestDirectory() throws IOException {
    FileUtils.verifyDirectory(TEST_OUTPUT_DIR);
    cleanDirectory(TEST_OUTPUT_DIR);
  }

  /**
   * Deletes all the contents of director (leaves an empty directory).
   *
   * @throws IOException if a file could not be deleted.
   */
  public static void cleanDirectory(File directory) throws IOException {
    if (!directory.exists()) {
      return;
    }
    if (!directory.isDirectory()) {
      throw new IllegalArgumentException("Not a directory: " + directory);
    }

    LinkedList<File> toDelete = new LinkedList<File>();
    allFiles(directory, toDelete);

    for (File f : toDelete) {
      if (!f.delete()) {
        throw new IOException("Could not delete file: " + f);
      }
    }
  }

  /**
   * List all files and subdirs in a directory. Subdirs are traversed depth-first, such that the caller can
   * iterate over this list and validly delete each element in order.
   */
  private static void allFiles(File rootDir, List<File> fileList) {
    for (String filename : rootDir.list()) {
      File f = new File(rootDir, filename);
      if (f.isDirectory()) {
        allFiles(f, fileList);
      }
      fileList.add(f);
    }
  }

  /**
   * Creates a 500-line text file with 10 random numbers on each line. The returned StringBuilder contains
   * the contents of the file.
   */
  public static StringBuilder createDummyFile(File file) throws IOException {
    file.createNewFile();
    StringBuilder text = new StringBuilder();
    for (int i = 0; i < 500; ++i) {
      for (int j = 0; j < 10; ++j) {
        text.append((int) (Math.random() * 100) + " ");
      }
      text.append("\n");
    }

    PrintWriter writer = new PrintWriter(new FileOutputStream(file));
    writer.append(text.toString());
    writer.close();

    return text;
  }

  public static void createFileFromResource(String res, File dest) throws Exception {
    File resFile = new File(TestUtils.class.getClassLoader().getResource(res).toURI());
    Files.copy(resFile, dest);
  }
}
