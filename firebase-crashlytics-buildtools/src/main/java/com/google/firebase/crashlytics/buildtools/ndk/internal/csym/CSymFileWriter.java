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

import java.io.File;
import java.io.IOException;

/**
 * Writes a CSym to a file.
 */
public interface CSymFileWriter {

  /**
   * Write the given CSym to a file.
   * @param cSym the CSym to write.
   * @return A reference to the file on disk.
   * @throws IOException if there is a problem writing the file.
   */
  void writeCSymFile(CSym cSym, File outputFile) throws IOException;
}
