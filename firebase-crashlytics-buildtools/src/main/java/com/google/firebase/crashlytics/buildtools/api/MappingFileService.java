/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.crashlytics.buildtools.api;

import com.google.firebase.crashlytics.buildtools.AppBuildInfo;
import com.google.firebase.crashlytics.buildtools.Obfuscator;
import com.google.firebase.crashlytics.buildtools.exception.ZeroByteFileException;
import java.io.File;
import java.io.IOException;

/** Represents a remote resource where obfuscator mapping files are uploaded. */
public interface MappingFileService {

  /** Upload the obfuscator's mapping file, such as mapping.txt from Proguard.
   *
   * @param mappingFile The file to upload.
   * @param mappingFileId The id that is associated with this mapping file, which should have been injected
   *                      into the APK at build time.
   * @param appBuildInfo Metadata for the app.
   * @param obfuscator Details of the obfuscation file.
   * @throws ZeroByteFileException if mappingFile was not uploaded successfully because it's empty
   * @throws IOException if mappingFile was not uploaded successfully, for any other reason
   */
  void uploadMappingFile(
      File mappingFile, String mappingFileId, AppBuildInfo appBuildInfo, Obfuscator obfuscator)
      throws ZeroByteFileException, IOException;
}
