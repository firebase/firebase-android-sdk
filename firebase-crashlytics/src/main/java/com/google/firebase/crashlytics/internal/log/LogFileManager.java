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

import android.content.Context;
import androidx.annotation.Nullable;
import com.google.firebase.crashlytics.internal.Logger;
import com.google.firebase.crashlytics.internal.common.CommonUtils;
import java.io.File;
import java.util.Set;

/**
 * Helper class which handles writing our log file using QueueFile. Methods of this class are not
 * synchronized or locked, and should be called on the single-threaded executor.
 */
public class LogFileManager {

  private static final String COLLECT_CUSTOM_LOGS = "com.crashlytics.CollectCustomLogs";

  private static final String LOGFILE_EXT = ".temp";
  private static final String LOGFILE_PREFIX = "crashlytics-userlog-";
  private static final NoopLogStore NOOP_LOG_STORE = new NoopLogStore();

  static final int MAX_LOG_SIZE = 65536;

  public interface DirectoryProvider {
    File getLogFileDir();
  }

  private final Context context;
  private final DirectoryProvider directoryProvider;

  private FileLogStore currentLog;

  public LogFileManager(Context context, DirectoryProvider directoryProvider) {
    this(context, directoryProvider, null);
  }

  public LogFileManager(
      Context context, DirectoryProvider directoryProvider, String currentSessionId) {
    this.context = context;
    this.directoryProvider = directoryProvider;
    this.currentLog = NOOP_LOG_STORE;
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

    final boolean isLoggingEnabled =
        CommonUtils.getBooleanResourceValue(context, COLLECT_CUSTOM_LOGS, true);

    if (!isLoggingEnabled) {
      Logger.getLogger().d("Preferences requested no custom logs. Aborting log file creation.");
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

  /**
   * Discards all old log files, except for ones identified by the given session IDs.
   *
   * @param sessionIdsToKeep the session IDs of log files to retain.
   */
  public void discardOldLogFiles(Set<String> sessionIdsToKeep) {
    final File[] logFiles = directoryProvider.getLogFileDir().listFiles();
    if (logFiles != null) {
      for (File file : logFiles) {
        if (!sessionIdsToKeep.contains(getSessionIdForFile(file))) {
          file.delete();
        }
      }
    }
  }

  /** package-private for testing */
  void setLogFile(File workingFile, int maxLogSize) {
    currentLog = new QueueFileLogStore(workingFile, maxLogSize);
  }

  private File getWorkingFileForSession(String sessionId) {
    final String fileName = LOGFILE_PREFIX + sessionId + LOGFILE_EXT;
    return new File(directoryProvider.getLogFileDir(), fileName);
  }

  private String getSessionIdForFile(File workingFile) {
    final String filename = workingFile.getName();
    final int indexOfExtension = filename.lastIndexOf(LOGFILE_EXT);
    if (indexOfExtension == -1) {
      return filename;
    }
    return filename.substring(LOGFILE_PREFIX.length(), indexOfExtension);
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
