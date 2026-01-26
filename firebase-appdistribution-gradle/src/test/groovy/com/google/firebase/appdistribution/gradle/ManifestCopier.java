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

package com.google.firebase.appdistribution.gradle;

import java.io.File;
import org.apache.commons.io.FileUtils;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class ManifestCopier implements TestRule {

  private static final String MANIFEST_FILE = "AndroidManifest.xml";

  private String _fixtureDataPath;
  private final TemporaryFolder _destTempFolder;

  public ManifestCopier(String fixtureDataPath, TemporaryFolder destTempFolder) {
    _fixtureDataPath = fixtureDataPath;
    _destTempFolder = destTempFolder;
  }

  @Override
  public Statement apply(final Statement base, Description description) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        File srcMainDir = new File(_destTempFolder.getRoot(), "src/main");
        srcMainDir.mkdirs();

        File srcFile = new File(_fixtureDataPath, MANIFEST_FILE);
        File destFile = new File(srcMainDir, MANIFEST_FILE);
        FileUtils.copyFile(srcFile, destFile);

        base.evaluate();
      }
    };
  }
}
