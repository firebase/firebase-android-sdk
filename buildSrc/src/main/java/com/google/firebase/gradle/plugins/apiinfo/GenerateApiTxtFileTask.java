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
import com.google.firebase.gradle.plugins.SdkUtilKt;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;

public abstract class GenerateApiTxtFileTask extends DefaultTask {

  @Input
  abstract String getMetalavaJarPath();

  public abstract void setMetalavaJarPath(String value);

  @OutputFile
  abstract File getApiTxt();

  public abstract void setApiTxt(File value);

  @Nested
  abstract Object getSourceSet();

  public abstract void setSourceSet(Object value);

  @InputFiles
  public abstract FileCollection getClassPath();

  public abstract void setClassPath(FileCollection value);

  @OutputFile
  abstract File getBaselineFile();

  public abstract void setBaselineFile(File value);

  @Input
  abstract boolean getUpdateBaseline();

  public abstract void setUpdateBaseline(boolean value);

  private Set<File> getSourceDirs() {
    if (getSourceSet() instanceof SourceSet) {
      return ((SourceSet) getSourceSet()).getJava().getSrcDirs();
    } else if (getSourceSet() instanceof AndroidSourceSet) {
      return ((AndroidSourceSet) getSourceSet()).getJava().getSrcDirs();
    }
    throw new IllegalStateException("Unsupported sourceSet provided: " + getSourceSet().getClass());
  }

  @TaskAction
  void execute() {
    String sourcePath =
        getSourceDirs().stream()
            .filter(File::exists)
            .map(File::getAbsolutePath)
            .collect(Collectors.joining(":"));
    if (sourcePath.isEmpty()) {
      getLogger()
          .warn(
              "Project {} has no sources in main source set, skipping...", getProject().getPath());
      return;
    }
    String classPath =
        getClassPath().getFiles().stream()
            .map(File::getAbsolutePath)
            .collect(Collectors.joining(":"));

    File androidJar = SdkUtilKt.getAndroidJar(getProject());
    if (androidJar != null) {
      classPath += ":" + androidJar.getAbsolutePath();
    }
    List<String> args =
        new ArrayList<>(
            Arrays.asList(
                getMetalavaJarPath(),
                "--no-banner",
                "--source-path",
                sourcePath,
                "--classpath",
                classPath,
                "--api",
                getApiTxt().getAbsolutePath(),
                "--format=v2"));

    if (getUpdateBaseline()) {
      args.addAll(Arrays.asList("--update-baseline", getBaselineFile().getAbsolutePath()));
    } else if (getBaselineFile().exists()) {
      args.addAll(Arrays.asList("--baseline", getBaselineFile().getAbsolutePath()));
    }

    getProject()
        .javaexec(
            spec -> {
              spec.setMain("-jar");
              spec.setArgs(args);
              spec.setIgnoreExitValue(true);
            });
  }
}
