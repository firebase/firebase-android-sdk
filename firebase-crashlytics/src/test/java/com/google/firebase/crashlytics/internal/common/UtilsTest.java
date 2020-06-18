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

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Comparator;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class UtilsTest {

  static FilenameFilter ALL_FILES =
      new FilenameFilter() {
        @Override
        public boolean accept(File dir, String filename) {
          return true;
        }
      };

  static Comparator<File> SMALLEST_FILENAME_FIRST =
      new Comparator<File>() {
        @Override
        public int compare(File file1, File file2) {
          return file1.getName().compareTo(file2.getName());
        }
      };

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  @Test
  public void testCapFileCount_returnsNumberOfRetainedFilesIfLessThanMax() throws Exception {
    folder.newFile();
    folder.newFile();
    final int numRetained =
        Utils.capFileCount(folder.getRoot(), ALL_FILES, 3, SMALLEST_FILENAME_FIRST);
    assertEquals(2, numRetained);
  }

  @Test
  public void testCapFileCount_returnsNumberOfRetainedFilesIfMoreThanMax() throws Exception {
    folder.newFile();
    folder.newFile();
    folder.newFile();
    folder.newFile();
    final int numRetained =
        Utils.capFileCount(folder.getRoot(), ALL_FILES, 3, SMALLEST_FILENAME_FIRST);
    assertEquals(3, numRetained);
  }

  @Test
  public void testCapFileCount_returnsNumberOfRetainedFilesIfSameAsMax() throws Exception {
    folder.newFile();
    folder.newFile();
    folder.newFile();
    final int numRetained =
        Utils.capFileCount(folder.getRoot(), ALL_FILES, 3, SMALLEST_FILENAME_FIRST);
    assertEquals(3, numRetained);
  }

  @Test
  public void testCapFileCount_returnsZeroRetainedIfFolderEmpty() throws Exception {
    final int numRetained =
        Utils.capFileCount(folder.getRoot(), ALL_FILES, 3, SMALLEST_FILENAME_FIRST);
    assertEquals(0, numRetained);
  }

  @Test
  public void testCapFileCount_returnsZeroRetainedIfFolderMissing() throws Exception {
    final int numRetained = Utils.capFileCount(new File(""), ALL_FILES, 3, SMALLEST_FILENAME_FIRST);
    assertEquals(0, numRetained);
  }
}
