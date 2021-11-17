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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.crashlytics.internal.Logger;
import com.google.firebase.crashlytics.internal.common.CommonUtils;
import com.google.firebase.crashlytics.internal.model.StaticSessionData;
import com.google.firebase.crashlytics.internal.persistence.FileStore;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;

public class CrashpadController {

  @SuppressWarnings("CharsetObjectCanBeUsed") // StandardCharsets requires API level 19.
  private static final Charset UTF_8 = Charset.forName("UTF-8");

  private static final String SESSION_METADATA_FILE = "session.json";
  private static final String APP_METADATA_FILE = "app.json";
  private static final String DEVICE_METADATA_FILE = "device.json";
  private static final String OS_METADATA_FILE = "os.json";

  private final Context context;
  private final NativeApi nativeApi;
  private final FileStore fileStore;

  CrashpadController(Context context, NativeApi nativeApi, FileStore fileStore) {
    this.context = context;
    this.nativeApi = nativeApi;
    this.fileStore = fileStore;
  }

  public boolean initialize(
      String sessionId, String generator, long startedAtSeconds, StaticSessionData sessionData) {

    final File crashReportDirectory = fileStore.getNativeSessionDir(sessionId);
    try {
      if (crashReportDirectory != null) {
        final String crashReportPath = crashReportDirectory.getCanonicalPath();
        if (nativeApi.initialize(crashReportPath, context.getAssets())) {
          writeBeginSession(sessionId, generator, startedAtSeconds);
          writeSessionApp(sessionId, sessionData.appData());
          writeSessionOs(sessionId, sessionData.osData());
          writeSessionDevice(sessionId, sessionData.deviceData());
          return true;
        }
      }
    } catch (IOException e) {
      Logger.getLogger().e("Error initializing Crashlytics NDK", e);
    }
    return false;
  }

  public boolean hasCrashDataForSession(String sessionId) {
    File crashFile = getFilesForSession(sessionId).minidump;
    return crashFile != null && crashFile.exists();
  }

  @NonNull
  public SessionFiles getFilesForSession(String sessionId) {
    final File sessionFileDirectory = fileStore.getNativeSessionDir(sessionId);
    final File sessionFileDirectoryForMinidump = new File(sessionFileDirectory, "pending");

    Logger.getLogger()
        .v("Minidump directory: " + sessionFileDirectoryForMinidump.getAbsolutePath());

    File minidump = getSingleFileWithExtension(sessionFileDirectoryForMinidump, ".dmp");

    Logger.getLogger()
        .v(
            "Minidump file "
                + (minidump != null && minidump.exists() ? "exists" : "does not exist"));

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

  public void writeBeginSession(String sessionId, String generator, long startedAtSeconds) {
    final String json =
        SessionMetadataJsonSerializer.serializeBeginSession(sessionId, generator, startedAtSeconds);
    writeSessionJsonFile(fileStore, sessionId, json, SESSION_METADATA_FILE);
  }

  public void writeSessionApp(String sessionId, StaticSessionData.AppData appData) {
    final String json =
        SessionMetadataJsonSerializer.serializeSessionApp(
            appData.appIdentifier(),
            appData.versionCode(),
            appData.versionName(),
            appData.installUuid(),
            appData.deliveryMechanism(),
            appData.developmentPlatform(),
            appData.developmentPlatformVersion());
    writeSessionJsonFile(fileStore, sessionId, json, APP_METADATA_FILE);
  }

  public void writeSessionOs(String sessionId, StaticSessionData.OsData osData) {
    final String json =
        SessionMetadataJsonSerializer.serializeSessionOs(
            osData.osRelease(), osData.osCodeName(), osData.isRooted());
    writeSessionJsonFile(fileStore, sessionId, json, OS_METADATA_FILE);
  }

  public void writeSessionDevice(String sessionId, StaticSessionData.DeviceData deviceData) {
    final String json =
        SessionMetadataJsonSerializer.serializeSessionDevice(
            deviceData.arch(),
            deviceData.model(),
            deviceData.availableProcessors(),
            deviceData.totalRam(),
            deviceData.diskSpace(),
            deviceData.isEmulator(),
            deviceData.state(),
            deviceData.manufacturer(),
            deviceData.modelClass());
    writeSessionJsonFile(fileStore, sessionId, json, DEVICE_METADATA_FILE);
  }

  private static void writeSessionJsonFile(
      FileStore fileStore, String sessionId, String json, String fileName) {
    final File sessionDirectory = fileStore.getNativeSessionDir(sessionId);
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
