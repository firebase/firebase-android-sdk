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

import com.google.firebase.crashlytics.buildtools.Buildtools;
import com.google.firebase.crashlytics.buildtools.ndk.NativeSymbolGenerator;
import com.google.firebase.crashlytics.buildtools.ndk.internal.CodeMappingException;
import com.google.firebase.crashlytics.buildtools.utils.FileUtils;
import java.io.File;
import java.io.IOException;

/**
 * Handles creation of cSYM files based on shared object files within a given NDK_OUT directory.
 */
public class NdkCSymGenerator implements NativeSymbolGenerator {

  public static final String CSYM_SUFFIX = ".cSYM";

  @Override
  public File generateSymbols(File nativeLib, File outputDir)
      throws IOException, CodeMappingException {
    final CSymFactory cSymFactory = new ElfCSymFactory(true);
    final CSymFileWriter cSymFileWriter = new StandardCSymFileWriter();
    return generateSymbolFileFromFile(nativeLib, outputDir, cSymFactory, cSymFileWriter);
  }

  File generateSymbolFileFromFile(
      File nativeLib, File outputDir, CSymFactory cSymFactory, CSymFileWriter cSymFileWriter)
      throws IOException, CodeMappingException {
    Buildtools.logD("Generating native symbol file from: " + nativeLib);

    if (nativeLib == null || !nativeLib.isFile()) {
      throw new CodeMappingException("Specified path is not a file: " + nativeLib);
    }

    FileUtils.verifyDirectory(outputDir);

    final CSym cSym;
    try {
      cSym = cSymFactory.createCSymFromFile(nativeLib);
    } catch (CSymException e) {
      throw new CodeMappingException(e);
    }
    if (cSym.getUUID() == null || cSym.getUUID().equals("")) {
      Buildtools.logD("Crashlytics could not generate a UUID for " + nativeLib + ", skipping.");
    }
    if (cSym.getSymbols().isEmpty()) {
      Buildtools.logD("Crashlytics found no symbols for " + nativeLib + ", skipping.");
      return null;
    }

    final String cSymFilename =
        NativeSymbolGenerator.createSymbolFileBasename(
                nativeLib.getName(), cSym.getArchitecture(), cSym.getUUID())
            + CSYM_SUFFIX;
    final File outputFile = new File(outputDir, cSymFilename);
    cSymFileWriter.writeCSymFile(cSym, outputFile);
    return outputFile;
  }
}
