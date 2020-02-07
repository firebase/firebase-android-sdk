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

package com.google.firebase.crashlytics.internal;

import androidx.annotation.Nullable;
import java.io.File;

/** Provides references to the files that make up a native crash report. */
public interface NativeSessionFileProvider {
  // TODO: Consider alternative approaches to making all these nullable.
  // TODO: Implement equality checks

  @Nullable
  File getMinidumpFile();

  @Nullable
  File getBinaryImagesFile();

  @Nullable
  File getMetadataFile();

  @Nullable
  File getSessionFile();

  @Nullable
  File getAppFile();

  @Nullable
  File getDeviceFile();

  @Nullable
  File getOsFile();
}
