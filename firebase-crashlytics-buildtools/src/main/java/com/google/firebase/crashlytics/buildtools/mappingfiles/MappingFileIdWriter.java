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

package com.google.firebase.crashlytics.buildtools.mappingfiles;

import com.google.firebase.crashlytics.buildtools.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * Provides utility methods for managing the Android resource file which contains
 * Crashlytics-specific information to be accessed by the app at runtime.
 */
public class MappingFileIdWriter {

  public static final String MAPPING_FILE_ID_RESOURCE_FILENAME =
      "com_google_firebase_crashlytics_mappingfileid.xml";

  private final File resourceFile;

  /**
   * Initializes this ResourceFileManager. Does not attempt to confirm the existence of the
   * values directory or the resource file itself; this is done lazily later.
   */
  public MappingFileIdWriter(File resourceFile) {
    this.resourceFile = resourceFile;
  }

  /**
   * Writes the given mapping file id to disk as an Android resource file. Overwrites any
   * existing resource file.
   */
  public void writeMappingFileId(String id) throws IOException {
    try {
      InputStream is = XmlResourceUtils.createResourceFileStream(id);
      if (resourceFile.getParentFile() != null) {
        FileUtils.verifyDirectory(resourceFile.getParentFile());
      }
      FileUtils.writeInputStreamToFile(is, resourceFile);
    } catch (Exception e) {
      throw new IOException("Crashlytics could not create: " + resourceFile, e);
    }
  }
}
