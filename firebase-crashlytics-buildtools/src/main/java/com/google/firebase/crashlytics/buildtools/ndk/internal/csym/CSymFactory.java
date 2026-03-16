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
 * Creates a CSym object from a given file.
 */
public interface CSymFactory {
  /**
   * Create and return the CSym object for the given file.
   * @param unstrippedLibrary the file (containing symbols) for which to create a CSym.
   * @return the CSym object created from reading the input file data.
   * @throws IOException if there is a problem reading the input file.
   */
  CSym createCSymFromFile(File unstrippedLibrary) throws CSymException, IOException;
}
