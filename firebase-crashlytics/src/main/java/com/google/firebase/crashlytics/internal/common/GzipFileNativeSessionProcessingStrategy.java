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

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.crashlytics.internal.CrashlyticsNativeComponent;
import com.google.firebase.crashlytics.internal.Logger;
import com.google.firebase.crashlytics.internal.NativeSessionFileProvider;
import com.google.firebase.crashlytics.internal.ndk.NativeFileUtils;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPOutputStream;

class GzipFileNativeSessionProcessingStrategy implements NativeSessionProcessingStrategy<Void> {

  interface OutputDirectoryProvider {
    File getOutputDirectory(String sessionId);
  }

  private final Context context;
  private final OutputDirectoryProvider outputDirectoryProvider;

  public GzipFileNativeSessionProcessingStrategy(
      Context context, OutputDirectoryProvider outputDirectoryProvider) {
    this.context = context;
    this.outputDirectoryProvider = outputDirectoryProvider;
  }

  @Override
  public Void processNativeSession(
      CrashlyticsNativeComponent nativeComponent,
      String sessionId,
      InputStream keysInput,
      InputStream logsInput,
      InputStream userInput)
      throws IOException {
    // TODO: Move all this back into the controller, and feed these files and keys/logs/user into
    //  a list of input stream makers to be passed to the gzipper/CrashlyticsReport maker
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

    final File nativeSessionDirectory = outputDirectoryProvider.getOutputDirectory(sessionId);

    if (!nativeSessionDirectory.mkdirs()) {
      Logger.getLogger().d("Couldn't create native sessions directory");
      return null;
    }

    // TODO: A class that takes something and turns it into a new object - filename/inputstream

    gzipFile(minidump, new File(nativeSessionDirectory, "minidump"));
    gzipBytes(
        NativeFileUtils.binaryImagesJsonFromMapsFile(binaryImages, context),
        new File(nativeSessionDirectory, "binaryImages"));
    gzipFile(metadata, new File(nativeSessionDirectory, "metadata"));
    gzipFile(sessionFile, new File(nativeSessionDirectory, "session"));
    gzipFile(sessionApp, new File(nativeSessionDirectory, "app"));
    gzipFile(sessionDevice, new File(nativeSessionDirectory, "device"));
    gzipFile(sessionOs, new File(nativeSessionDirectory, "os"));
    gzipInputStream(userInput, new File(nativeSessionDirectory, "user"));
    gzipInputStream(keysInput, new File(nativeSessionDirectory, "keys"));
    gzipInputStream(logsInput, new File(nativeSessionDirectory, "logs"));

    return null;
  }

  private static void gzipBytes(@Nullable byte[] bytes, @NonNull File outputFile)
      throws IOException {
    if (bytes == null || bytes.length == 0) {
      return;
    }
    ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
    try {
      gzipInputStream(bis, outputFile);
    } finally {
      CommonUtils.closeQuietly(bis);
    }
  }

  private static void gzipFile(@Nullable File input, @NonNull File outputFile) throws IOException {
    if (input == null) {
      return;
    }
    FileInputStream fis = null;
    try {
      fis = new FileInputStream(input);
      gzipInputStream(fis, outputFile);
    } finally {
      CommonUtils.closeQuietly(fis);
    }
  }

  private static void gzipInputStream(@Nullable InputStream input, @NonNull File output)
      throws IOException {
    if (input == null) {
      return;
    }
    byte[] buffer = new byte[1024];
    GZIPOutputStream gos = null;
    try {
      gos = new GZIPOutputStream(new FileOutputStream(output));

      int read;

      while ((read = input.read(buffer)) > 0) {
        gos.write(buffer, 0, read);
      }

      gos.finish();
    } finally {
      CommonUtils.closeQuietly(gos);
    }
  }
}
