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

package com.google.firebase.crashlytics.internal.persistence;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.crashlytics.internal.persistence.FileStore.sanitizeName;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import com.google.firebase.crashlytics.internal.CrashlyticsTestCase;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

@SuppressWarnings("ResultOfMethodCallIgnored") // Convenient use of files.
public class FileStoreTest extends CrashlyticsTestCase {
  FileStore fileStore;

  @Before
  public void setUp() throws Exception {
    fileStore = new FileStore(getContext());
  }

  @Test
  public void testProcessName() {
    // If this test fails, the test setup is missing a process name so fileStore is using v1.
    assertThat(fileStore.processName).isEqualTo("com.google.firebase.crashlytics.test");
  }

  @Test
  public void testGetCommonFile() {
    File commonFile = fileStore.getCommonFile("testCommonFile");
    assertFalse(commonFile.exists());
    assertNotNull(commonFile.getParentFile());
    assertTrue(commonFile.getParentFile().exists());
  }

  @Test
  public void testGetSessionFile() {
    String sessionId = "sessionId";
    String filename = "testSessionFile";
    File sessionFile = fileStore.getSessionFile(sessionId, filename);
    assertFalse(sessionFile.exists());
    assertNotNull(sessionFile.getParentFile());
    assertTrue(sessionFile.getParentFile().exists());
    assertEquals(sessionFile.getParentFile().getName(), sessionId);

    // ensure different session ids  result in different files.
    String sessionId2 = "sessionId2";
    File sessionFile2 = fileStore.getSessionFile(sessionId2, filename);
    assertNotSame(sessionFile, sessionFile2);
    assertEquals(sessionFile.getName(), sessionFile2.getName());
  }

  @Test
  public void testGetOpenSessionIds() {
    String[] ids = {"session0", "session1", "session2", "session3"};
    String filename = "testSessionFile";
    for (String id : ids) {
      File f = fileStore.getSessionFile(id, filename);
      assertFalse(f.exists());
    }
    List<String> openIds = fileStore.getAllOpenSessionIds();
    assertTrue(openIds.containsAll(Arrays.asList(ids)));
    assertEquals(ids.length, openIds.size());
  }

  @Test
  public void testDeleteSessionFiles() throws Exception {
    String[] ids = {"session0", "session1", "session2", "session3"};
    String filename = "testSessionFile";
    for (String id : ids) {
      File f = fileStore.getSessionFile(id, filename);
      f.createNewFile();
    }
    assertEquals(ids.length, fileStore.getAllOpenSessionIds().size());

    File session0File = fileStore.getSessionFile(ids[0], filename);
    assertTrue(session0File.exists());

    fileStore.deleteSessionFiles(ids[0]);
    List<String> openSessions = fileStore.getAllOpenSessionIds();
    assertEquals(ids.length - 1, openSessions.size());
    assertFalse(session0File.exists());
    assertFalse(openSessions.contains(ids[0]));

    for (String id : openSessions) {
      File f = fileStore.getSessionFile(id, filename);
      assertTrue(f.exists());
    }
  }

  @Test
  public void testGetReports() throws Exception {
    String session1 = "session1";
    String session2 = "session2";

    assertEquals(0, fileStore.getReports().size());
    assertEquals(0, fileStore.getNativeReports().size());
    assertEquals(0, fileStore.getPriorityReports().size());

    File report1 = fileStore.getReport(session1);
    assertEquals(report1, fileStore.getReport(session1));
    File report2 = fileStore.getReport(session2);
    assertEquals(0, fileStore.getReports().size());
    report1.createNewFile();
    report2.createNewFile();
    assertEquals(2, fileStore.getReports().size());
    report2.delete();
    assertEquals(1, fileStore.getReports().size());

    File priorityReport = fileStore.getPriorityReport(session1);
    assertEquals(0, fileStore.getPriorityReports().size());
    priorityReport.createNewFile();
    assertNotSame(priorityReport, report1);
    assertEquals(1, fileStore.getPriorityReports().size());
    assertEquals(priorityReport, fileStore.getPriorityReport(session1));
    assertEquals(priorityReport, fileStore.getPriorityReports().get(0));
    priorityReport.delete();
    assertEquals(0, fileStore.getPriorityReports().size());

    File nativeReport = fileStore.getNativeReport(session1);
    assertEquals(0, fileStore.getNativeReports().size());
    nativeReport.createNewFile();
    assertEquals(1, fileStore.getNativeReports().size());
    assertEquals(nativeReport, fileStore.getNativeReport(session1));
    assertEquals(nativeReport, fileStore.getNativeReports().get(0));
    nativeReport.delete();
    assertEquals(0, fileStore.getNativeReports().size());
  }

  @Test
  public void testSanitizeShortName() {
    assertThat(sanitizeName("com.google.Process_Name123$%^"))
        .isEqualTo("com.google.Process_Name123___");
  }

  @Test
  public void testSanitizeLongName() {
    assertThat(sanitizeName("com.google.my.awesome.app:big.stuff.Happens_Here123$%^"))
        .isEqualTo("ef6c01fc7a1a8d10ecb062a81707f769d39d4210");
  }
}
