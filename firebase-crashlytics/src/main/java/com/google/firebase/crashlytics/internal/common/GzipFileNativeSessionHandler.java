// Copyright 2020 Google LLC
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

package com.google.firebase.crashlytics.internal.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.crashlytics.core.MetaDataStore;
import com.google.firebase.crashlytics.internal.CrashlyticsNativeComponent;
import com.google.firebase.crashlytics.internal.Logger;
import com.google.firebase.crashlytics.internal.NativeSessionFileProvider;
import com.google.firebase.crashlytics.internal.log.LogFileManager;
import com.google.firebase.crashlytics.internal.ndk.NativeFileUtils;
import com.google.firebase.crashlytics.internal.persistence.FileStore;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

class GzipFileNativeSessionHandler implements NativeComponentSessionHandler<Void> {

  private CrashlyticsNativeComponent nativeComponent;
  private FileStore fileStore;

  @Override
  public Void handlePreviousNativeSession(String sessionId) throws IOException {
    Logger.getLogger().d("Finalizing native report for session " + sessionId);
    NativeSessionFileProvider nativeSessionFileProvider =
        nativeComponent.getSessionFileProvider(sessionId);

    final File minidump = nativeSessionFileProvider.getMinidumpFile();
    final File binaryImages = nativeSessionFileProvider.getBinaryImagesFile();
    final File metadata = nativeSessionFileProvider.getMetadataFile();
    final File sessionFile = nativeSessionFileProvider.getSessionFile();
    final File sessionApp = nativeSessionFileProvider.getAppFile();
    final File sessionDevice = nativeSessionFileProvider.getDeviceFile();
    final File sessionOs = nativeSessionFileProvider.getOsFile();

    if (minidump == null || !minidump.exists()) {
      Logger.getLogger().w("No minidump data found for session " + sessionId);
      return null;
    }

    final File filesDir = fileStore.getFilesDir();
    final MetaDataStore metaDataStore = new MetaDataStore(filesDir);
    final File sessionUser = metaDataStore.getUserDataFileForSession(sessionId);
    final File sessionKeys = metaDataStore.getKeysFileForSession(sessionId);

    final LogFileManager previousSessionLogManager =
        new LogFileManager(getContext(), logFileDirectoryProvider, sessionId);
    final byte[] logs = previousSessionLogManager.getBytesForLog();

    final File nativeSessionDirectory = new File(getNativeSessionFilesDir(), sessionId);

    if (!nativeSessionDirectory.mkdirs()) {
      Logger.getLogger().d("Couldn't create native sessions directory");
      return;
    }

    gzipFile(minidump, new File(nativeSessionDirectory, "minidump"));
    gzipIfNotEmpty(
        NativeFileUtils.binaryImagesJsonFromMapsFile(binaryImages, context),
        new File(nativeSessionDirectory, "binaryImages"));
    gzipFile(metadata, new File(nativeSessionDirectory, "metadata"));
    gzipFile(sessionFile, new File(nativeSessionDirectory, "session"));
    gzipFile(sessionApp, new File(nativeSessionDirectory, "app"));
    gzipFile(sessionDevice, new File(nativeSessionDirectory, "device"));
    gzipFile(sessionOs, new File(nativeSessionDirectory, "os"));
    gzipFile(sessionUser, new File(nativeSessionDirectory, "user"));
    gzipFile(sessionKeys, new File(nativeSessionDirectory, "keys"));
    gzipIfNotEmpty(logs, new File(nativeSessionDirectory, "logs"));

    return null;
  }

  private static void gzipFile(@NonNull File input, @NonNull File output) throws IOException {
    if (!input.exists() || !input.isFile()) {
      return;
    }
    byte[] buffer = new byte[1024];
    FileInputStream fis = null;
    GZIPOutputStream gos = null;
    try {
      fis = new FileInputStream(input);
      gos = new GZIPOutputStream(new FileOutputStream(output));

      int read;

      while ((read = fis.read(buffer)) > 0) {
        gos.write(buffer, 0, read);
      }

      gos.finish();
    } finally {
      CommonUtils.closeQuietly(fis);
      CommonUtils.closeQuietly(gos);
    }
  }

  private static void gzipIfNotEmpty(@Nullable byte[] content, @NonNull File path)
      throws IOException {
    if (content != null && content.length > 0) {
      gzip(content, path);
    }
  }

  private static void gzip(@NonNull byte[] bytes, @NonNull File path) throws IOException {
    GZIPOutputStream gos = null;
    try {
      gos = new GZIPOutputStream(new FileOutputStream(path));
      gos.write(bytes, 0, bytes.length);
      gos.finish();
    } finally {
      CommonUtils.closeQuietly(gos);
    }
  }
}
