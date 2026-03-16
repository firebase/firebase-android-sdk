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

package com.google.firebase.crashlytics.buildtools.ndk.internal.csym;

import com.google.firebase.crashlytics.buildtools.api.FirebaseSymbolFileService;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class CsymSymbolFileService extends FirebaseSymbolFileService {

  private static final String CSYM_UPLOAD_REQUEST_FORMAT =
      "%s/v1/project/-/app/%s/upload/native/%s";

  public CsymSymbolFileService() {
    super(CSYM_UPLOAD_REQUEST_FORMAT);
  }

  @Override
  protected String extractUuid(File cSymFile) throws IOException {
    final String cSymHeader = readCSymHeader(cSymFile);

    // The first line of the cSym is a header with metadata we need for the upload.
    // Header format is as follows, tab-delimited:
    // code_mapping {cSYM version} {cSYM source} {UUID} {architecture} {file count} {symbol count}
    // {range count}
    final String[] headerTokens = cSymHeader.split("\t");
    if (headerTokens.length != 8 || !headerTokens[0].equals("code_mapping")) {
      throw new IOException("Invalid cSYM header for " + cSymFile.getAbsolutePath());
    }

    // uuid.
    return headerTokens[3];
  }

  /**
   * Utility to return the header of cSymFile, which is simply the first line of the file.
   */
  private static String readCSymHeader(File cSymFile) throws IOException {
    String cSymHeader;
    try (BufferedReader reader = new BufferedReader(new FileReader(cSymFile))) {
      cSymHeader = reader.readLine();
    }

    if (cSymHeader == null || cSymHeader.length() == 0) {
      throw new IOException("Could not read cSYM header for " + cSymFile.getPath());
    }
    return cSymHeader;
  }
}
