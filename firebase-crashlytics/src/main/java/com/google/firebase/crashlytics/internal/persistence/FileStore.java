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

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.firebase.crashlytics.internal.Logger;
import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Controls creation files that are used by Crashlytics.
 *
 * <p>The following types of files are used by Crashlytics:
 *
 * <ul>
 *   <li>"Common" files, that exist independent of a specific session id.
 *   <li>"Open session" files, which contain a varierty of temporary files specific ot a Crashlytics
 *       session. These files may or may not eventually be combined into a Crashlytics crash report
 *       file.
 *   <li>"Report" files, which are processed reports, ready to be uploaded to Crashlytics servers.
 *       There are currently 3 types of report files: priority reports, (standard) reports, and
 *       native.
 * </ul>
 *
 * The distinction allows us to put intermediate session files in session-specific directories, so
 * that all files for a session can be easily cleaned up by deleting that subdirectory. It also
 * allows us to grab all prepared reports quickly, etc.
 *
 * <p>The files are stored in an versioned Crashlytics-specific directory, which is versioned to
 * make it straightforward to change how / where we store files in the future. </code> By
 * convention, any use of new File(...) or similar outside of this class is a code smell.
 */
public class FileStore {

  private static final String FILES_PATH = ".com.google.firebase.crashlytics.files.v1";
  private static final String SESSIONS_PATH = "open-sessions";
  private static final String NATIVE_SESSION_SUBDIR = "native";
  private static final String REPORTS_PATH = "reports";
  private static final String PRIORITY_REPORTS_PATH = "priority-reports";
  private static final String NATIVE_REPORTS_PATH = "native-reports";

  private final File rootDir;
  private final File sessionsDir;
  private final File reportsDir;
  private final File priorityReportsDir;
  private final File nativeReportsDir;

  public FileStore(Context context) {
    rootDir = prepareBaseDir(new File(context.getFilesDir(), FILES_PATH));
    sessionsDir = prepareBaseDir(new File(rootDir, SESSIONS_PATH));
    reportsDir = prepareBaseDir(new File(rootDir, REPORTS_PATH));
    priorityReportsDir = prepareBaseDir(new File(rootDir, PRIORITY_REPORTS_PATH));
    nativeReportsDir = prepareBaseDir(new File(rootDir, NATIVE_REPORTS_PATH));
  }

  @VisibleForTesting
  public void deleteAllCrashlyticsFiles() {
    recursiveDelete(rootDir);
  }

  public void cleanupLegacyFiles() {
    // Fixes b/195664514
    // :TODO: consider removing this method in mid 2023, to give all clients time to upgrade
    File[] legacyDirs =
        new File[] {
          new File(rootDir.getParent(), ".com.google.firebase.crashlytics"),
          new File(rootDir.getParent(), ".com.google.firebase.crashlytics-ndk")
        };

    for (File legacyDir : legacyDirs) {
      if (legacyDir.exists() && recursiveDelete(legacyDir)) {
        Logger.getLogger().d("Deleted legacy Crashlytics files from " + legacyDir.getPath());
      }
    }
  }

  static boolean recursiveDelete(File fileOrDirectory) {
    File[] files = fileOrDirectory.listFiles();
    if (files != null) {
      for (File file : files) {
        recursiveDelete(file);
      }
    }
    return fileOrDirectory.delete();
  }

  /** @return internal File used by Crashlytics, that is not specific to a session */
  public File getCommonFile(String filename) {
    return new File(rootDir, filename);
  }

  /** @return all common (non session specific) files matching the given filter. */
  public List<File> getCommonFiles(FilenameFilter filter) {
    return safeArrayToList(rootDir.listFiles(filter));
  }

  private File getSessionDir(String sessionId) {
    return prepareDir(new File(sessionsDir, sessionId));
  }

  /**
   * @return A file with the given filename, in the session-specific directory corresponding to the
   *     given session Id.
   */
  public File getSessionFile(String sessionId, String filename) {
    return new File(getSessionDir(sessionId), filename);
  }

  public List<File> getSessionFiles(String sessionId, FilenameFilter filter) {
    return safeArrayToList(getSessionDir(sessionId).listFiles(filter));
  }

  public File getNativeSessionDir(String sessionId) {
    return prepareDir(new File(getSessionDir(sessionId), NATIVE_SESSION_SUBDIR));
  }

  public boolean deleteSessionFiles(String sessionId) {
    File sessionDir = new File(sessionsDir, sessionId);
    return recursiveDelete(sessionDir);
  }

  public List<String> getAllOpenSessionIds() {
    return safeArrayToList(sessionsDir.list());
  }

  public File getReport(String sessionId) {
    return new File(reportsDir, sessionId);
  }

  public List<File> getReports() {
    return safeArrayToList(reportsDir.listFiles());
  }

  public File getPriorityReport(String sessionId) {
    return new File(priorityReportsDir, sessionId);
  }

  public List<File> getPriorityReports() {
    return safeArrayToList(priorityReportsDir.listFiles());
  }

  public File getNativeReport(String sessionId) {
    return new File(nativeReportsDir, sessionId);
  }

  public List<File> getNativeReports() {
    return safeArrayToList(nativeReportsDir.listFiles());
  }

  private static File prepareDir(File file) {
    //noinspection ResultOfMethodCallIgnored
    file.mkdirs();
    return file;
  }

  private static synchronized File prepareBaseDir(File file) {
    if (file.exists()) {
      if (file.isDirectory()) {
        return file;
      } else {
        Logger.getLogger()
            .d(
                "Unexpected non-directory file: "
                    + file
                    + "; deleting file and creating new directory.");
        //noinspection ResultOfMethodCallIgnored
        file.delete();
      }
    }
    if (!file.mkdirs()) {
      Logger.getLogger().e("Could not create Crashlytics-specific directory: " + file);
    }
    return file;
  }

  private static <T> List<T> safeArrayToList(@Nullable T[] array) {
    return (array == null) ? Collections.emptyList() : Arrays.asList(array);
  }
}
