// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.crashlytics.internal.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.firebase.crashlytics.internal.Logger;
import com.google.firebase.crashlytics.internal.persistence.FileStore;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Handles persistence of App Quality Sessions session id for Crashlytics, keeps track of the
 * appQualitySessionId and the Crashlytics sessionId, which are different.
 *
 * <p>All public methods are intended to be called from background threads.
 */
class CrashlyticsAppQualitySessionsStore {
  private static final String AQS_SESSION_ID_FILENAME_PREFIX = "aqs.";

  private static final FilenameFilter AQS_SESSION_ID_FILE_FILTER =
      (dir, name) -> name.startsWith(AQS_SESSION_ID_FILENAME_PREFIX);

  private static final Comparator<File> FILE_RECENCY_COMPARATOR =
      (file1, file2) -> Long.compare(file2.lastModified(), file1.lastModified());

  private final FileStore fileStore;

  @Nullable private String sessionId = null;
  @Nullable private String appQualitySessionId = null;

  CrashlyticsAppQualitySessionsStore(FileStore fileStore) {
    this.fileStore = fileStore;
  }

  /** Gets the App Quality Sessions session id for the given Crashlytics session id. */
  @Nullable
  public String getAppQualitySessionId(@NonNull String sessionId) {
    if (Objects.equals(this.sessionId, sessionId)) {
      return appQualitySessionId;
    }

    return readAqsSessionIdFile(sessionId);
  }

  /** Sets the App Quality Sessions session id. */
  public void setAppQualitySessionId(@NonNull String appQualitySessionId) {
    if (!Objects.equals(this.appQualitySessionId, appQualitySessionId)) {
      this.appQualitySessionId = appQualitySessionId;
      persist();
    }
  }

  /** Sets the Crashlytics session id, null means the session was closed. */
  public void setSessionId(String sessionId) {
    if (sessionId == null) {
      // Do not write a aqs id in a closed session.
      this.sessionId = null;
    } else {
      if (!sessionId.equals(this.sessionId)) {
        this.sessionId = sessionId;
        persist();
      }
    }
  }

  /** Persists the current session ids to disk, only if they are all non-null. */
  private void persist() {
    // Make local immutable copies to avoid needing to synchronize the setters.
    String sessionId = this.sessionId;
    String appQualitySessionId = this.appQualitySessionId;

    if (sessionId != null && appQualitySessionId != null) {
      try {
        // Instead of storing the AQS session id in the file's contents, write the id as part of
        // the file name. In all circumstances a file needs to necessarily be created. By writing
        // the id in the file name, an IO operation of writing the id into the contents of the file
        // is eliminated. Profiling shows this is an order of magnitude more performant.

        //noinspection ResultOfMethodCallIgnored Noop if the file already exists.
        fileStore
            .getSessionFile(sessionId, AQS_SESSION_ID_FILENAME_PREFIX + appQualitySessionId)
            .createNewFile();

        // Update the local values to match what was persisted to avoid any inconsistencies.
        this.sessionId = sessionId;
        this.appQualitySessionId = appQualitySessionId;
      } catch (IOException ex) {
        Logger.getLogger().w("Failed to persist App Quality Sessions session id.", ex);
      }
    }
  }

  @VisibleForTesting
  @Nullable
  String readAqsSessionIdFile(@NonNull String sessionId) {
    List<File> aqsFiles = fileStore.getSessionFiles(sessionId, AQS_SESSION_ID_FILE_FILTER);
    if (aqsFiles.isEmpty()) {
      Logger.getLogger().w("Unable to read App Quality Sessions session id.");
      return null;
    }
    File mostRecentAqsFile = Collections.min(aqsFiles, FILE_RECENCY_COMPARATOR);
    return mostRecentAqsFile.getName().substring(AQS_SESSION_ID_FILENAME_PREFIX.length());
  }
}
