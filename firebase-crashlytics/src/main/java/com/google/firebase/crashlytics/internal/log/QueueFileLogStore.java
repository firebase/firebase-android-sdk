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

import com.google.firebase.crashlytics.internal.Logger;
import com.google.firebase.crashlytics.internal.common.CommonUtils;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Locale;

/** Class which manages the storage of log entries in a single QueueFile. */
class QueueFileLogStore implements FileLogStore {

  private static final Charset UTF_8 = Charset.forName("UTF-8");

  private final File workingFile;
  private final int maxLogSize;

  private QueueFile logFile;

  private class LogBytes {
    public final byte[] bytes;
    public final int offset;

    LogBytes(byte[] bytes, int offset) {
      this.bytes = bytes;
      this.offset = offset;
    }
  }

  QueueFileLogStore(File workingFile, int maxLogSize) {
    this.workingFile = workingFile;
    this.maxLogSize = maxLogSize;
  }

  @Override
  public void writeToLog(long timestamp, String msg) {
    openLogFile();
    doWriteToLog(timestamp, msg);
  }

  @Override
  public byte[] getLogAsBytes() {
    final LogBytes logBytes = getLogBytes();
    if (logBytes == null) {
      return null;
    }
    final byte[] rawBytes = new byte[logBytes.offset];
    System.arraycopy(logBytes.bytes, 0, rawBytes, 0, logBytes.offset);
    return rawBytes;
  }

  @Override
  public String getLogAsString() {
    final byte[] logBytes = getLogAsBytes();
    return (logBytes != null) ? new String(logBytes, UTF_8) : null;
  }

  private LogBytes getLogBytes() {
    if (!workingFile.exists()) {
      return null;
    }

    // Reopen if the file exists
    openLogFile();

    if (logFile == null) {
      // There was an error opening the file.
      return null;
    }

    // Need a final variable reference for use within the ElementReader anonymous class, but
    // also want to have it be mutated therein and the end value used afterwards. A single
    // element array is a common way to fudge this.
    final int[] offsetHolder = new int[] {0};

    final byte[] logBytes = new byte[logFile.usedBytes()];

    try {
      logFile.forEach(
          new QueueFile.ElementReader() {
            @Override
            public void read(InputStream in, int length) throws IOException {
              try {
                in.read(logBytes, offsetHolder[0], length);
                offsetHolder[0] += length;
              } finally {
                in.close();
              }
            }
          });
    } catch (IOException e) {
      Logger.getLogger().e("A problem occurred while reading the Crashlytics log file.", e);
    }

    return new LogBytes(logBytes, offsetHolder[0]);
  }

  @Override
  public void closeLogFile() {
    CommonUtils.closeOrLog(logFile, "There was a problem closing the Crashlytics log file.");
    logFile = null;
  }

  @Override
  public void deleteLogFile() {
    closeLogFile();
    workingFile.delete();
  }

  private void openLogFile() {
    if (logFile == null) {
      try {
        logFile = new QueueFile(workingFile);
      } catch (IOException e) {
        Logger.getLogger().e("Could not open log file: " + workingFile, e);
      }
    }
  }

  private void doWriteToLog(long timestamp, String msg) {
    if (logFile == null) {
      return;
    }
    if (msg == null) {
      msg = "null";
    }

    try {
      // This is a bit of a strange area. We want to truncate extremely long messages because
      // writing a message which is larger than the permitted file size has the effect of
      // forcing all other messages out of the file, and then the remaining new message is
      // also too large, and is also removed. Thus we wind up with an empty log.
      //
      // The message length is measured in characters, while the max file size is in bytes. We
      // truncate the message character count at 1/4 of the max log size in bytes. This is
      // because languages like Japanese use 3 bytes per character. When you take into account
      // that Tape also introduces per-file and per-message overhead in storage, I decided on
      // 1/4 rather than 1/3 as the cut-off.
      //
      // In practice this means that english messages can be up to 16000+ characters long, and
      // And Japanese messages can be up to 5000+ characters long.
      //
      // We could rewrite this to measure both cut-offs in bytes, but it makes the logic here
      // much more complex, so I took this approximate approach for now.
      final int quarterMaxLogSize = maxLogSize / 4;

      if (msg.length() > quarterMaxLogSize) {
        msg = "..." + msg.substring(msg.length() - quarterMaxLogSize);
      }

      msg = msg.replaceAll("\r", " ");
      msg = msg.replaceAll("\n", " ");

      final byte[] msgBytes = String.format(Locale.US, "%d %s%n", timestamp, msg).getBytes(UTF_8);

      logFile.add(msgBytes);

      // Keep the log file below the max size
      while (!logFile.isEmpty() && logFile.usedBytes() > maxLogSize) {
        logFile.remove();
      }
    } catch (IOException e) {
      Logger.getLogger().e("There was a problem writing to the Crashlytics log.", e);
    }
  }
}
