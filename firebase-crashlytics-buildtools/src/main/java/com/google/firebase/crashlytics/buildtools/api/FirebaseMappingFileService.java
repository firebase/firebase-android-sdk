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
import com.google.firebase.crashlytics.buildtools.Buildtools;
import com.google.firebase.crashlytics.buildtools.Obfuscator;
import com.google.firebase.crashlytics.buildtools.exception.ZeroByteFileException;
import com.google.firebase.crashlytics.buildtools.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import java.net.URL;

public class FirebaseMappingFileService implements MappingFileService {

  private static final String MAPPING_UPLOAD_REQUEST_FORMAT =
      "%s/v1/project/-/app/%s/upload/java/%s";

  private final WebApi webApi;

  public FirebaseMappingFileService(WebApi webApi) {
    this.webApi = webApi;
  }

  @Override
  public void uploadMappingFile(
      File mappingFile, String mappingFileId, AppBuildInfo appBuildInfo, Obfuscator obfuscator)
      throws IOException {

    if (mappingFile.length() == 0) {
      throw new ZeroByteFileException(
          String.format(
              "Mapping file '%s' is zero bytes, skipping upload.", mappingFile.getAbsolutePath()));
    }

    final File gZippedMappingFile =
        new File(
            getMappingBuildDir(appBuildInfo.getBuildDir()),
            mappingFileId + FileUtils.GZIPPED_FILE_SUFFIX);

    Buildtools.logD("Zipping mapping file: " + mappingFile + " -> " + gZippedMappingFile);
    FileUtils.gZipFile(mappingFile, gZippedMappingFile);

    final URL url =
        new URL(
            String.format(
                MAPPING_UPLOAD_REQUEST_FORMAT,
                webApi.getCodeMappingApiUrl(),
                appBuildInfo.getGoogleAppId(),
                mappingFileId));
    webApi.uploadFile(url, gZippedMappingFile);

    gZippedMappingFile.delete(); // otherwise they'll only get deleted after "gradlew clean"
  }

  /**
   * @return A directory to hold zipped mapping artifacts, which is a subdir of buildDir and will be
   * created if needed.
   */
  private static File getMappingBuildDir(File buildDir) throws IOException {
    File workingDir = new File(buildDir, ".crashlytics-mappings-tmp");
    FileUtils.verifyDirectory(workingDir);
    return workingDir;
  }
}
