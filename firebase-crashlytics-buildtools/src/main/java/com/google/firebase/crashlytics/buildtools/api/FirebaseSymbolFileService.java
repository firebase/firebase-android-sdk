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

import com.google.firebase.crashlytics.buildtools.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import org.apache.commons.io.FilenameUtils;

public abstract class FirebaseSymbolFileService implements SymbolFileService {

  private final String uploadRequestFormat;

  protected FirebaseSymbolFileService(String uploadRequestFormat) {
    this.uploadRequestFormat = uploadRequestFormat;
  }

  protected abstract String extractUuid(File symbolFile) throws IOException;

  @Override
  public void uploadNativeSymbolFile(WebApi webApi, File symbolFile, String googleAppId)
      throws IOException {

    final File parent = symbolFile.getParentFile();
    final File gZippedSymbolFile =
        (parent == null)
            ? new File(
                FilenameUtils.removeExtension(symbolFile.getName()) + FileUtils.GZIPPED_FILE_SUFFIX)
            : new File(
                parent,
                FilenameUtils.removeExtension(symbolFile.getName())
                    + FileUtils.GZIPPED_FILE_SUFFIX);

    FileUtils.gZipFile(symbolFile, gZippedSymbolFile);

    final URL url =
        new URL(
            String.format(
                uploadRequestFormat,
                webApi.getCodeMappingApiUrl(),
                googleAppId,
                extractUuid(symbolFile)));

    webApi.uploadFile(url, gZippedSymbolFile);

    gZippedSymbolFile.delete();
  }
}
