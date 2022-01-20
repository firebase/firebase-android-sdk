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

package com.google.firebase.crashlytics.internal.metadata;

import androidx.annotation.Nullable;
import com.google.firebase.crashlytics.internal.persistence.FileStore;
import java.io.File;

/**
 * Helper class which handles writing our log file using QueueFile. Methods of this class are not
 * synchronized or locked, and should be called on the single-threaded executor.
 */
public class LogFileManager {

  private static final String LOGFILE_NAME = "userlog";
  private static final NoopLogStore NOOP_LOG_STORE = new NoopLogStore();

  static final int MAX_LOG_SIZE = 65536;

  private final FileStore fileStore;

  private FileLogStore currentLog;

  public LogFileManager(FileStore fileStore) {
    this.fileStore = fileStore;
    this.currentLog = NOOP_LOG_STORE;
  }

  public LogFileManager(FileStore fileStore, String currentSessionId) {
    this(fileStore);
    setCurrentSession(currentSessionId);
  }

  /**
   * Set the session ID for which to manage logging.
   *
   * @param sessionId
   */
  public final void setCurrentSession(String sessionId) {
    currentLog.closeLogFile();
    currentLog = NOOP_LOG_STORE;

    if (sessionId == null) {
      return;
    }

    setLogFile(getWorkingFileForSession(sessionId), MAX_LOG_SIZE);
  }

  /** Log a timestamped string to the log file. */
  public void writeToLog(long timestamp, String msg) {
    currentLog.writeToLog(timestamp, msg);
  }

  public byte[] getBytesForLog() {
    return currentLog.getLogAsBytes();
  }

  @Nullable
  public String getLogString() {
    return currentLog.getLogAsString();
  }

  /** Empty the log. */
  public void clearLog() {
    currentLog.deleteLogFile();
  }

  /** package-private for testing */
  void setLogFile(File workingFile, int maxLogSize) {
    currentLog = new QueueFileLogStore(workingFile, maxLogSize);
  }

  private File getWorkingFileForSession(String sessionId) {
    return fileStore.getSessionFile(sessionId, LOGFILE_NAME);
  }

  /**
   * Used when file logging is disabled or not yet initialized. Provides simple and no-op
   * implementations for the FileLogStore interface.
   */
  private static final class NoopLogStore implements FileLogStore {
    @Override
    public void writeToLog(long timestamp, String msg) {}

    @Override
    public byte[] getLogAsBytes() {
      return null;
    }

    @Override
    public String getLogAsString() {
      return null;
    }

    @Override
    public void closeLogFile() {}

    @Override
    public void deleteLogFile() {}
  }
}
