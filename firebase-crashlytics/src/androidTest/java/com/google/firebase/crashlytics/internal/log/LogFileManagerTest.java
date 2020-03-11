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

package com.google.firebase.crashlytics.internal.log;

import static com.google.firebase.crashlytics.internal.log.LogFileManager.MAX_LOG_SIZE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.Context;
import androidx.test.runner.AndroidJUnit4;
import java.io.File;
import java.util.Arrays;
import java.util.Locale;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class LogFileManagerTest {

  private static final int SMALL_MAX_LOG_SIZE = 100;
  private static final String logFormat = "%d %s\n";

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Mock private Context mockContext;

  @Mock private LogFileManager.DirectoryProvider mockDirectoryProvider;

  private LogFileManager logFileManager;
  private File testLogFile;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    final File tempDir = temporaryFolder.newFolder();
    Mockito.when(mockDirectoryProvider.getLogFileDir()).thenReturn(tempDir);

    testLogFile = new File(tempDir, "testLogFile.log");
    logFileManager = new LogFileManager(mockContext, mockDirectoryProvider);
  }

  @Test
  public void testLogEmpty() throws Exception {
    logFileManager.setLogFile(testLogFile, SMALL_MAX_LOG_SIZE);

    final String logString = logFileManager.getLogString();

    assertNull(logString);

    logFileManager.clearLog();
  }

  @Test
  public void testLogMultiEntryAscii() throws Exception {
    final int maxLogSizeBytes = SMALL_MAX_LOG_SIZE;
    final int messageLength = 5;
    final StringBuffer sb = new StringBuffer();

    logFileManager.setLogFile(testLogFile, maxLogSizeBytes);

    for (int i = 0; i < 3; i++) {
      final char[] msgChars = new char[messageLength];
      Arrays.fill(msgChars, Integer.toString(i).charAt(0));
      final String msg = new String(msgChars);

      logFileManager.writeToLog(1, msg);
      sb.append("1 ");
      sb.append(msg);
      sb.append("\n");
    }

    final String logString = logFileManager.getLogString();

    final String expected = sb.toString();

    assertEquals(expected, logString);

    logFileManager.clearLog();
  }

  @Test
  public void testLogMultiEntryUnicode() throws Exception {
    final int maxLogSizeBytes = SMALL_MAX_LOG_SIZE;
    final int messageLength = 5;
    final StringBuffer sb = new StringBuffer();
    final char[] chars = new char[] {'あ', 'い', 'う'};

    logFileManager.setLogFile(testLogFile, maxLogSizeBytes);

    for (int i = 0; i < 3; i++) {
      final char[] msgChars = new char[messageLength];
      Arrays.fill(msgChars, chars[i]);
      final String msg = new String(msgChars);

      logFileManager.writeToLog(1, msg);
      sb.append("1 ");
      sb.append(msg);
      sb.append("\n");
    }

    final String logString = logFileManager.getLogString();

    final String expected = sb.toString();

    assertEquals(expected, logString);

    logFileManager.clearLog();
  }

  @Test
  public void testLogOversizeMultiEntryUnicode() throws Exception {
    final int maxLogSizeBytes = SMALL_MAX_LOG_SIZE;
    final int messageLength = 5;
    final StringBuffer sb = new StringBuffer();
    final char[] chars = new char[] {'あ', 'い', 'う', 'え', 'お', 'ら', 'り', 'る', 'れ', 'ろ'};

    logFileManager.setLogFile(testLogFile, maxLogSizeBytes);

    for (int i = 0; i < 10; i++) {
      final char[] msgChars = new char[messageLength];
      Arrays.fill(msgChars, chars[i]);
      final String msg = new String(msgChars);

      logFileManager.writeToLog(1, msg);

      // We expect the first 7 messages to get removed from the file due to the size
      // restriction and therefore not be present in the expected output. This is because of
      // file size overhead imposed by Tape metadata stored about the file and about each
      // record.
      if (i > 6) {
        sb.append("1 ");
        sb.append(msg);
        sb.append("\n");
      }
    }

    final String logString = logFileManager.getLogString();

    final String expected = sb.toString();

    assertEquals(expected, logString);

    logFileManager.clearLog();
  }

  @Test
  public void testLogFullSizeMultiEntryUnicode() throws Exception {
    final int maxLogSizeBytes = MAX_LOG_SIZE;
    final int messageLength = 3412;
    final StringBuffer sb = new StringBuffer();
    final char[] chars = new char[] {'あ', 'い', 'う', 'え', 'お', 'ら', 'り', 'る', 'れ', 'ろ'};

    logFileManager.setLogFile(testLogFile, maxLogSizeBytes);

    for (int i = 0; i < 10; i++) {
      final char[] msgChars = new char[messageLength];
      Arrays.fill(msgChars, chars[i]);
      final String msg = new String(msgChars);

      // We expect the first 4 messages to get removed from the file due to the size
      // restriction and therefore not be present in the expected output. This is because of
      // file size overhead imposed by Tape metadata stored about the file and about each
      // record.

      logFileManager.writeToLog(1, msg);

      if (i > 3) {
        sb.append("1 ");
        sb.append(msg);
        sb.append("\n");
      }
    }

    final String logString = logFileManager.getLogString();

    final String expected = sb.toString();

    assertEquals(expected, logString);

    logFileManager.clearLog();
  }

  @Test
  public void testLogOversizeMultiEntryAscii() throws Exception {
    final int maxLogSizeBytes = SMALL_MAX_LOG_SIZE;
    final int messageLength = 10;
    final StringBuffer sb = new StringBuffer();

    logFileManager.setLogFile(testLogFile, maxLogSizeBytes);

    for (int i = 0; i < 10; i++) {
      final char[] msgChars = new char[messageLength];
      Arrays.fill(msgChars, Integer.toString(i).charAt(0));
      final String msg = new String(msgChars);

      logFileManager.writeToLog(1, msg);

      // We expect the first 6 messages to get removed from the file due to the size
      // restriction and therefore not be present in the expected output. This is because of
      // file size overhead imposed by Tape metadata stored about the file and about each
      // record.
      if (i > 5) {
        sb.append("1 ");
        sb.append(msg);
        sb.append("\n");
      }
    }

    final String logString = logFileManager.getLogString();

    final String expected = sb.toString();

    assertEquals(expected, logString);

    logFileManager.clearLog();
  }

  @Test
  public void testLogFullSizeOversizeMultiEntryAscii() throws Exception {
    final int maxLogSizeBytes = MAX_LOG_SIZE;
    final int messageLength = 10240;
    final StringBuffer sb = new StringBuffer();

    logFileManager.setLogFile(testLogFile, maxLogSizeBytes);

    for (int i = 0; i < 10; i++) {
      final char[] msgChars = new char[messageLength];
      Arrays.fill(msgChars, Integer.toString(i).charAt(0));
      final String msg = new String(msgChars);

      logFileManager.writeToLog(1, msg);

      // We expect the first 4 messages to get removed from the file due to the size
      // restriction and therefore not be present in the expected output.
      if (i > 3) {
        sb.append("1 ");
        sb.append(msg);
        sb.append("\n");
      }
    }

    final String logString = logFileManager.getLogString();

    final String expected = sb.toString();

    assertEquals(expected, logString);

    logFileManager.clearLog();
  }

  @Test
  public void testLogTruncateTextAscii() throws Exception {
    final int maxLogSizeBytes = SMALL_MAX_LOG_SIZE;
    final char[] msgChars = new char[50];
    Arrays.fill(msgChars, 'a');
    msgChars[49] = 'z';
    final String msg = new String(msgChars);

    logFileManager.setLogFile(testLogFile, maxLogSizeBytes);

    logFileManager.writeToLog(1, msg);
    final String logString = logFileManager.getLogString();

    final char[] expectedChars = new char[25];
    Arrays.fill(expectedChars, 'a');
    expectedChars[24] = 'z';
    final String expected = "1 ..." + new String(expectedChars) + "\n";

    assertEquals(expected, logString);

    logFileManager.clearLog();
  }

  @Test
  public void testLogTruncateTextUnicode() throws Exception {
    // Needed a little extra room in the file to make this work b/c of the ratio of characters
    // to Tape metadata overhead.
    final int maxLogSizeBytes = SMALL_MAX_LOG_SIZE + 20;
    final char[] msgChars = new char[50];
    Arrays.fill(msgChars, '行');
    msgChars[49] = '止';
    final String msg = new String(msgChars);

    logFileManager.setLogFile(testLogFile, maxLogSizeBytes);

    logFileManager.writeToLog(1, msg);
    final String logString = logFileManager.getLogString();

    final char[] expectedChars = new char[30];
    Arrays.fill(expectedChars, '行');
    expectedChars[29] = '止';
    final String expected = "1 ..." + new String(expectedChars) + "\n";

    assertEquals(expected, logString);

    logFileManager.clearLog();
  }

  @Test
  public void testInitialLogState() throws Exception {
    assertNull(new LogFileManager(mockContext, mockDirectoryProvider).getBytesForLog());
  }

  @Test
  public void testCloseAndReopen() throws Exception {
    logFileManager.setLogFile(testLogFile, MAX_LOG_SIZE);

    String logString = "test";
    long timestamp = 7;

    logFileManager.writeToLog(timestamp, logString);

    String expected = String.format(Locale.ENGLISH, logFormat, timestamp, logString);
    assertEquals(expected, logFileManager.getLogString());

    logFileManager.clearLog();

    assertNull(logFileManager.getBytesForLog());

    logString = "reopened";
    timestamp = 12;
    logFileManager.writeToLog(timestamp, logString);

    expected = String.format(Locale.ENGLISH, logFormat, timestamp, logString);
    assertEquals(expected, logFileManager.getLogString());
  }

  @Test
  public void testCloseIsIdempotent() throws Exception {
    logFileManager.setLogFile(testLogFile, MAX_LOG_SIZE);
    logFileManager.writeToLog(1, "test");
    logFileManager.clearLog();
    assertNull(logFileManager.getBytesForLog());
    logFileManager.clearLog();
    assertNull(logFileManager.getBytesForLog());
  }

  @Test
  public void testSessionChangeClearsLog() throws Exception {
    logFileManager.setCurrentSession("1");

    String logString = "test";
    long timestamp = 7;

    logFileManager.writeToLog(timestamp, logString);

    String expected = String.format(Locale.ENGLISH, logFormat, timestamp, logString);
    assertEquals(expected, logFileManager.getLogString());

    logFileManager.setCurrentSession("2");

    assertNull(logFileManager.getBytesForLog());

    logString = "changed session";
    timestamp = 12;
    logFileManager.writeToLog(timestamp, logString);

    expected = String.format(Locale.ENGLISH, logFormat, timestamp, logString);
    assertEquals(expected, logFileManager.getLogString());
  }
}
