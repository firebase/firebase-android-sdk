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
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * Contains information from the MODULE & INFO lines of a breakpad file.
 * See https://chromium.googlesource.com/breakpad/breakpad/+/master/docs/symbol_files.md
 */
public class BreakpadRecords {
  private final String os;
  private final String architecture;
  private final String moduleId;
  private final String name;
  private final String codeId;

  /**
   * Parses the module record as described in
   * https://chromium.googlesource.com/breakpad/breakpad/+/master/docs/symbol_files.md
   */
  public static BreakpadRecords createFromBreakpadFile(File breakpadFile) throws IOException {
    final BufferedReader reader = new BufferedReader(new FileReader(breakpadFile));
    String line = reader.readLine();
    final String[] splitModuleLine = line.split(" ");
    if (splitModuleLine.length < 5 || !splitModuleLine[0].equals("MODULE")) {
      throw new IOException(
          "Could not find valid module record for Breakpad file: "
              + breakpadFile.getAbsolutePath()
              + " Clean your build directory and try again. "
              + "Contact Firebase support if the problem persists.");
    }

    String[] splitInfoLine = {};
    while ((line = reader.readLine()) != null) {
      if (line.startsWith("INFO")) {
        splitInfoLine = line.split(" ");
        break;
      }
    }
    reader.close();

    final String codeId;
    if (splitInfoLine.length >= 3) {
      codeId = splitInfoLine[2];
    } else {
      codeId = null;
      Buildtools.logW(
          "Invalid or missing INFO line, no CODE_ID found for " + breakpadFile.getAbsolutePath());
    }
    return new BreakpadRecords(
        splitModuleLine[1],
        splitModuleLine[2],
        splitModuleLine[3].toLowerCase(), // Crashlytics presumes lowercase UUID
        splitModuleLine[4],
        codeId.toLowerCase());
  }

  public BreakpadRecords(
      String os, String architecture, String moduleId, String name, String codeId) {
    this.os = os;
    this.architecture = architecture;
    this.moduleId = moduleId;
    this.name = name;
    this.codeId = codeId;
  }

  public String getOs() {
    return os;
  }

  public String getModuleId() {
    return moduleId;
  }

  public String getArchitecture() {
    return architecture;
  }

  public String getName() {
    return name;
  }

  public String getCodeId() {
    return codeId;
  }
}
