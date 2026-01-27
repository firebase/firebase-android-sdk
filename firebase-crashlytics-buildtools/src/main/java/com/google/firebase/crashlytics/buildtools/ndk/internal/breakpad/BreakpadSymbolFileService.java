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

package com.google.firebase.crashlytics.buildtools.ndk.internal.breakpad;

import com.google.firebase.crashlytics.buildtools.Buildtools;
import com.google.firebase.crashlytics.buildtools.api.FirebaseSymbolFileService;
import java.io.File;
import java.io.IOException;

public class BreakpadSymbolFileService extends FirebaseSymbolFileService {

  private static final String BREAKPAD_UPLOAD_REQUEST_FORMAT =
      "%s/v1/project/-/app/%s/upload/breakpad/%s";

  public BreakpadSymbolFileService() {
    super(BREAKPAD_UPLOAD_REQUEST_FORMAT);
  }

  @Override
  protected String extractUuid(File symbolFile) throws IOException {
    BreakpadRecords breakpadRecords = BreakpadRecords.createFromBreakpadFile(symbolFile);
    String uuid = breakpadRecords.getCodeId();

    if (uuid == null) {
      Buildtools.logD(
          "Could not find valid INFO record for Breakpad file. Using MODULE ID instead.");
      uuid = breakpadRecords.getModuleId();
    }
    return uuid;
  }
}
