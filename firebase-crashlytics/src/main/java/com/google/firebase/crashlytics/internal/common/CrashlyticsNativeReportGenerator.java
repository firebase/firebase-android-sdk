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

import com.google.firebase.crashlytics.core.MetaDataStore;
import com.google.firebase.crashlytics.internal.CrashlyticsNativeComponent;
import com.google.firebase.crashlytics.internal.Logger;
import com.google.firebase.crashlytics.internal.NativeSessionFileProvider;
import com.google.firebase.crashlytics.internal.log.LogFileManager;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport;
import java.io.File;
import java.io.IOException;

class CrashlyticsNativeReportGenerator implements NativeComponentSessionHandler<CrashlyticsReport> {

  private CrashlyticsNativeComponent nativeComponent;

  @Override
  public CrashlyticsReport handlePreviousNativeSession(String sessionId) throws IOException {
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

    return null;
  }
}
