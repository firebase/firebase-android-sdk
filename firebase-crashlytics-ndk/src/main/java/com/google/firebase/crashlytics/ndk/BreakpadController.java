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

package com.google.firebase.crashlytics.ndk;

import android.content.Context;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.crashlytics.internal.Logger;
import com.google.firebase.crashlytics.internal.common.CommonUtils;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;

class BreakpadController implements NativeComponentController {

  private static final Charset UTF_8 = Charset.forName("UTF-8");
  private static final String SESSION_METADATA_FILE = "session.json";
  private static final String APP_METADATA_FILE = "app.json";
  private static final String DEVICE_METADATA_FILE = "device.json";
  private static final String OS_METADATA_FILE = "os.json";

  private final Context context;
  private final NativeApi nativeApi;
  private final CrashFilesManager filesManager;

  BreakpadController(Context context, NativeApi nativeApi, CrashFilesManager filesManager) {
    this.context = context;
    this.nativeApi = nativeApi;
    this.filesManager = filesManager;
  }

  @Override
  public boolean initialize(String sessionId) {
    boolean initSuccess = false;
    final File crashReportDirectory = filesManager.getSessionFileDirectory(sessionId);
    try {
      if (crashReportDirectory != null) {
        final String crashReportPath = crashReportDirectory.getCanonicalPath();
        initSuccess = nativeApi.initialize(crashReportPath, context.getAssets());
      }
    } catch (IOException e) {
      Logger.getLogger().e("Error initializing CrashlyticsNdk", e);
    }
    return initSuccess;
  }

  @Override
  public boolean hasCrashDataForSession(String sessionId) {
    if (filesManager.hasSessionFileDirectory(sessionId)) {
      final File crashFile = getFilesForSession(sessionId).minidump;
      return crashFile != null && crashFile.exists();
    }
    return false;
  }

  @Override
  public boolean finalizeSession(String sessionId) {
    filesManager.deleteSessionFilesDirectory(sessionId);
    return true;
  }

  @Override
  @NonNull
  public SessionFiles getFilesForSession(String sessionId) {
    final File sessionFileDirectory = filesManager.getSessionFileDirectory(sessionId);
    final File sessionFileDirectoryForMinidump = new File(sessionFileDirectory, "pending");

    Logger.getLogger()
        .d("Minidump directory: " + sessionFileDirectoryForMinidump.getAbsolutePath());

    File minidump = getSingleFileWithExtension(sessionFileDirectoryForMinidump, ".dmp");

    Logger.getLogger()
        .d("Minidump " + (minidump != null && minidump.exists() ? "exists" : "does not exist"));

    final SessionFiles.Builder builder = new SessionFiles.Builder();
    if (sessionFileDirectory != null
        && sessionFileDirectory.exists()
        && sessionFileDirectoryForMinidump.exists()) {
      builder
          .minidumpFile(getSingleFileWithExtension(sessionFileDirectoryForMinidump, ".dmp"))
          .metadataFile(getSingleFileWithExtension(sessionFileDirectory, ".device_info"))
          .sessionFile(new File(sessionFileDirectory, SESSION_METADATA_FILE))
          .appFile(new File(sessionFileDirectory, APP_METADATA_FILE))
          .deviceFile(new File(sessionFileDirectory, DEVICE_METADATA_FILE))
          .osFile(new File(sessionFileDirectory, OS_METADATA_FILE));
    }
    return builder.build();
  }

  @Override
  public void writeBeginSession(String sessionId, String generator, long startedAtSeconds) {
    final String json =
        SessionMetadataJsonSerializer.serializeBeginSession(sessionId, generator, startedAtSeconds);
    writeSessionJsonFile(sessionId, json, SESSION_METADATA_FILE);
  }

  @Override
  public void writeSessionApp(
      String sessionId,
      String appIdentifier,
      String versionCode,
      String versionName,
      String installUuid,
      int deliveryMechanism,
      String unityVersion) {
    unityVersion = !TextUtils.isEmpty(unityVersion) ? unityVersion : "";
    final String json =
        SessionMetadataJsonSerializer.serializeSessionApp(
            appIdentifier, versionCode, versionName, installUuid, deliveryMechanism, unityVersion);
    writeSessionJsonFile(sessionId, json, APP_METADATA_FILE);
  }

  @Override
  public void writeSessionOs(
      String sessionId, String osRelease, String osCodeName, boolean isRooted) {
    final String json =
        SessionMetadataJsonSerializer.serializeSessionOs(osRelease, osCodeName, isRooted);
    writeSessionJsonFile(sessionId, json, OS_METADATA_FILE);
  }

  @Override
  public void writeSessionDevice(
      String sessionId,
      int arch,
      String model,
      int availableProcessors,
      long totalRam,
      long diskSpace,
      boolean isEmulator,
      int state,
      String manufacturer,
      String modelClass) {
    final String json =
        SessionMetadataJsonSerializer.serializeSessionDevice(
            arch,
            model,
            availableProcessors,
            totalRam,
            diskSpace,
            isEmulator,
            state,
            manufacturer,
            modelClass);
    writeSessionJsonFile(sessionId, json, DEVICE_METADATA_FILE);
  }

  private void writeSessionJsonFile(String sessionId, String json, String fileName) {
    final File sessionDirectory = filesManager.getSessionFileDirectory(sessionId);
    final File jsonFile = new File(sessionDirectory, fileName);
    writeTextFile(jsonFile, json);
  }

  private static void writeTextFile(File file, String text) {
    BufferedWriter writer = null;
    try {
      writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), UTF_8));
      writer.write(text);
    } catch (IOException e) {
      // TODO
    } finally {
      CommonUtils.closeOrLog(writer, "Failed to close " + file);
    }
  }

  /** Returns a single file matching the given extension from the given directory. */
  @Nullable
  private static File getSingleFileWithExtension(File directory, String extension) {
    File[] files = directory.listFiles();

    if (files == null) {
      return null;
    }

    for (File file : files) {
      if (file.getName().endsWith(extension)) {
        return file;
      }
    }

    return null;
  }
}
