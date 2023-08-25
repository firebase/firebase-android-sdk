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

import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.firebase.crashlytics.internal.Logger;
import com.google.firebase.crashlytics.internal.persistence.FileStore;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Objects;

/**
 * Handles persistence of App Quality Sessions session id for Crashlytics, keeps track of the
 * appQualitySessionId and the Crashlytics sessionId, which are different.
 *
 * <p>All public methods are intended to be called from background threads.
 */
class CrashlyticsAppQualitySessionsStore {
  @SuppressWarnings("CharsetObjectCanBeUsed") // StandardCharsets requires API level 19.
  private static final Charset UTF_8 = Charset.forName("UTF-8");

  private static final int AQS_SESSION_ID_LENGTH = 32;
  private static final String AQS_SESSION_ID_FILENAME = "app-quality-session-id";

  private final FileStore fileStore;

  @Nullable private String sessionId = null;
  @Nullable private String appQualitySessionId = null;

  CrashlyticsAppQualitySessionsStore(FileStore fileStore) {
    this.fileStore = fileStore;
  }

  /** Gets the App Quality Sessions session id for the given Crashlytics session id. */
  @Nullable
  public String getAppQualitySessionId(@NonNull String sessionId) {
    checkNotOnMainThread();
    if (Objects.equals(this.sessionId, sessionId)) {
      return appQualitySessionId;
    }

    File aqsFile = getAqsSessionIdFile(sessionId);
    if (!aqsFile.exists() || aqsFile.length() == 0) {
      Logger.getLogger().d("No aqs session id set for session " + sessionId);
      safeDeleteCorruptAqsFile(aqsFile);
      return null;
    }

    return readAAqsSessionIdFile(aqsFile);
  }

  /** Sets the App Quality Sessions session id. */
  public void setAppQualitySessionId(@NonNull String appQualitySessionId) {
    if (sessionId != null) {
      // Only do this check when the session has been opened because aqs sends an id right away.
      // This is fine since Sessions initializes before Crashlytics opens a session, so no persist.
      checkNotOnMainThread();
    }
    if (!Objects.equals(this.appQualitySessionId, appQualitySessionId)) {
      this.appQualitySessionId = appQualitySessionId;
      persist();
    }
  }

  /** Sets the Crashlytics session id, null means the session was closed. */
  public void setSessionId(String sessionId) {
    checkNotOnMainThread();
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
      File file = getAqsSessionIdFile(sessionId);
      try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
        fileOutputStream.write(appQualitySessionId.getBytes(UTF_8));

        // Update the local values to match what was persisted to avoid any inconsistencies.
        this.sessionId = sessionId;
        this.appQualitySessionId = appQualitySessionId;
      } catch (IOException ex) {
        Logger.getLogger().w("Unable to write App Quality Sessions session id.", ex);
      }
    }
  }

  @VisibleForTesting
  File getAqsSessionIdFile(@NonNull String sessionId) {
    return fileStore.getSessionFile(sessionId, AQS_SESSION_ID_FILENAME);
  }

  @VisibleForTesting
  String readAAqsSessionIdFile(File aqsFile) {
    try (FileInputStream fileInputStream = new FileInputStream(aqsFile)) {
      byte[] data = new byte[AQS_SESSION_ID_LENGTH];
      int read = fileInputStream.read(data);
      if (read == -1) {
        throw new IOException("Unexpected end of file.");
      }
      String appQualitySessionId = new String(data, 0, read);
      Logger.getLogger().d("Loaded aqs session id " + appQualitySessionId);
      return appQualitySessionId;
    } catch (IOException ex) {
      Logger.getLogger().w("Unable to read App Quality Sessions session id.", ex);
      safeDeleteCorruptAqsFile(aqsFile);
      return null;
    }
  }

  private static void safeDeleteCorruptAqsFile(File aqsFile) {
    if (aqsFile.exists() && aqsFile.delete()) {
      Logger.getLogger().i("Deleted corrupt aqs file: " + aqsFile.getAbsolutePath());
    }
  }

  private static void checkNotOnMainThread() {
    // TODO(mrober): Remove this after testing and validation.
    if (Looper.getMainLooper() == Looper.myLooper()) {
      throw new IllegalStateException("Running on main thread when expected not to!");
    }
  }
}
