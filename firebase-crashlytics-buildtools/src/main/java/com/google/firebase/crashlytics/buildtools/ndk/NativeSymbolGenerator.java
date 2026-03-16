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

package com.google.firebase.crashlytics.buildtools.ndk;

import com.google.firebase.crashlytics.buildtools.ndk.internal.CodeMappingException;
import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOCase;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.SuffixFileFilter;

public interface NativeSymbolGenerator {
  IOFileFilter SO_FILE_FILTER =
      new SuffixFileFilter(/* suffixes= */ new String[] {".so", ".symbols"}, IOCase.INSENSITIVE);

  File generateSymbols(File nativeLib, File outputDir) throws IOException, CodeMappingException;

  String LIB_PREFIX = "lib";

  static String createSymbolFileBasename(String libFilename, String arch, String uuid) {
    String moduleName = FilenameUtils.removeExtension(libFilename);
    if (moduleName.startsWith(LIB_PREFIX)) {
      moduleName = moduleName.substring(LIB_PREFIX.length());
    }
    return String.format("%s-%s-%s", moduleName, arch, uuid);
  }
}
