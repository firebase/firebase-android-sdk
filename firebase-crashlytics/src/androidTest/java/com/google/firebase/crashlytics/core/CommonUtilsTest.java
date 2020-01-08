// Copyright 2019 Google LLC
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

package com.google.firebase.crashlytics.core;

import static org.mockito.Mockito.mock;

import android.content.Context;
import com.google.firebase.crashlytics.internal.CrashlyticsTestCase;
import com.google.firebase.crashlytics.internal.common.IdManager;
import com.google.firebase.iid.internal.FirebaseInstanceIdInternal;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class CommonUtilsTest extends CrashlyticsTestCase {

  private File createEmptyClsFile(File dir) throws IOException {
    final Context context = getContext();
    FirebaseInstanceIdInternal instanceIdMock = mock(FirebaseInstanceIdInternal.class);
    final CLSUUID id =
        new CLSUUID(new IdManager(context, context.getPackageName(), instanceIdMock));
    final File f = new File(dir, id.toString() + ".cls");
    f.createNewFile();
    return f;
  }

  public void testCapFileCount() throws Exception {
    final Context context = getContext();

    final File testDir = new File(context.getFilesDir(), "trim_test");
    testDir.mkdirs();
    // make sure the directory is empty. Doesn't recurse into subdirs, but that's OK since
    // we're only using this directory for this test and we won't create any subdirs.
    for (File f : testDir.listFiles()) {
      if (f.isFile()) {
        f.delete();
      }
    }

    final int maxFiles = 4;
    // create a bunch of non-cls files that don't match the capFileCount
    for (int i = 0; i < maxFiles + 2; ++i) {
      new File(testDir, "whatever" + i + ".blah").createNewFile();
    }
    assertEquals(maxFiles + 2, testDir.listFiles().length);
    final FilenameFilter clsFilter = CrashlyticsController.SESSION_FILE_FILTER;
    // This should have no effect - nothing matches the filter yet
    Utils.capFileCount(
        testDir, clsFilter, maxFiles, CrashlyticsController.SMALLEST_FILE_NAME_FIRST);
    assertEquals(maxFiles + 2, testDir.listFiles().length);

    // create two groups of empty crash files. The first set will be deleted after the 2nd
    // is created and trim is invoked.
    final Set<File> oldFiles = new HashSet<File>();
    for (int i = 0; i < maxFiles; ++i) {
      oldFiles.add(createEmptyClsFile(testDir));

      // This should have no effect - we're not over the limit
      Utils.capFileCount(
          testDir, clsFilter, maxFiles, CrashlyticsController.SMALLEST_FILE_NAME_FIRST);
      assertEquals(oldFiles.size(), testDir.listFiles(clsFilter).length);
    }
    // The filesystem only has 1sec precision, so we need to sleep long enough that
    // the timestamps in the 2nd set of files are later than the ones in the first.
    Thread.sleep(1500);

    final Set<File> newFiles = new HashSet<File>();
    for (int i = 0; i < maxFiles; ++i) {
      newFiles.add(createEmptyClsFile(testDir));
    }
    assertEquals(oldFiles.size() + newFiles.size(), testDir.listFiles(clsFilter).length);
    Utils.capFileCount(
        testDir, clsFilter, maxFiles, CrashlyticsController.SMALLEST_FILE_NAME_FIRST);
    assertEquals(maxFiles, testDir.listFiles(clsFilter).length);

    // make sure only the newer files remain
    final Set<File> currentFiles = new HashSet<File>(Arrays.asList(testDir.listFiles(clsFilter)));
    assertTrue(newFiles.containsAll(currentFiles));
    assertEquals(currentFiles.size(), newFiles.size());
  }
}
