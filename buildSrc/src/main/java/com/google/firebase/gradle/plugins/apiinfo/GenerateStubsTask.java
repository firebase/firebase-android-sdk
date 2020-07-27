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

import com.android.build.gradle.api.AndroidSourceSet;
import com.google.firebase.gradle.plugins.SdkUtil;
import java.io.File;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;

public abstract class GenerateStubsTask extends DefaultTask {
  @Input
  public abstract String getMetalavaJarPath();

  public abstract void setMetalavaJarPath(String path);

  public abstract Object getSourceSet();

  @InputFiles
  public abstract FileCollection getClassPath();

  public abstract void setSourceSet(Object sourceSet);

  public abstract void setClassPath(FileCollection value);

  @OutputDirectory
  public abstract File getOutputDir();

  public abstract void setOutputDir(File dir);

  private Set<File> getSourceDirs() {
    if (getSourceSet() instanceof SourceSet) {
      return ((SourceSet) getSourceSet()).getJava().getSrcDirs();
    } else if (getSourceSet() instanceof AndroidSourceSet) {
      return ((AndroidSourceSet) getSourceSet()).getJava().getSrcDirs();
    }
    throw new IllegalStateException("Unsupported sourceSet provided: " + getSourceSet().getClass());
  }

  @TaskAction
  public void run() {
    String sourcePath =
        getSourceDirs().stream()
            .filter(File::exists)
            .map(File::getAbsolutePath)
            .collect(Collectors.joining(":"));

    String classPath =
        getClassPath().getFiles().stream()
            .map(File::getAbsolutePath)
            .collect(Collectors.joining(":"));

    File androidJar = SdkUtil.getAndroidJar(getProject());
    if (androidJar != null) {
      classPath += ":" + androidJar.getAbsolutePath();
    }

    String cp = classPath;

    getProject()
        .javaexec(
            spec -> {
              spec.setMain("-jar");
              spec.setArgs(
                  Arrays.asList(
                      getMetalavaJarPath(),
                      "--no-banner",
                      "--source-path",
                      sourcePath,
                      "--classpath",
                      cp,
                      "--include-annotations",
                      "--doc-stubs",
                      getOutputDir().getAbsolutePath()));
            });
  }
}
