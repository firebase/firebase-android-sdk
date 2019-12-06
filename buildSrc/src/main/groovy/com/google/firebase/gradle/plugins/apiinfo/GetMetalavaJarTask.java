// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.gradle.plugins.apiinfo;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

public abstract class GetMetalavaJarTask extends DefaultTask {

  @OutputFile
  abstract File getOutputFile();

  public abstract void setOutputFile(File outputFile);

  @TaskAction
  void execute() {
    if (getOutputFile().exists()) {
      return;
    }

    try (InputStream stream =
        new URL("https://storage.googleapis.com/android-ci/metalava-full-1.3.0-SNAPSHOT.jar")
            .openStream()) {
      Files.copy(stream, getOutputFile().toPath(), StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new GradleException("Unable to read the jar file from GCS", e);
    }
  }
}
