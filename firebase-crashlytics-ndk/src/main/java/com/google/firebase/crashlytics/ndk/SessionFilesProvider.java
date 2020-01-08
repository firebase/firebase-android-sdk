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

import com.google.firebase.crashlytics.internal.NativeSessionFileProvider;
import java.io.File;

class SessionFilesProvider implements NativeSessionFileProvider {

  private final SessionFiles sessionFiles;

  SessionFilesProvider(SessionFiles sessionFiles) {
    this.sessionFiles = sessionFiles;
  }

  @Override
  public File getMinidumpFile() {
    return sessionFiles.minidump;
  }

  @Override
  public File getBinaryImagesFile() {
    return sessionFiles.binaryImages;
  }

  @Override
  public File getMetadataFile() {
    return sessionFiles.metadata;
  }

  @Override
  public File getSessionFile() {
    return sessionFiles.session;
  }

  @Override
  public File getAppFile() {
    return sessionFiles.app;
  }

  @Override
  public File getDeviceFile() {
    return sessionFiles.device;
  }

  @Override
  public File getOsFile() {
    return sessionFiles.os;
  }
}
