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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

public abstract class GenerateStubsTask extends DefaultTask {
  @Input
  public abstract String getMetalavaJarPath();

  public abstract void setMetalavaJarPath(String path);

  public abstract AndroidSourceSet getSourceSet();

  @InputFiles
  public abstract FileCollection getClassPath();

  public abstract void setSourceSet(AndroidSourceSet sourceSet);

  public abstract void setClassPath(FileCollection value);

  @OutputDirectory
  public abstract File getOutputDir();

  public abstract void setOutputDir(File dir);

  @TaskAction
  public void run() {
    String sourcePath =
        getSourceSet().getJava().getSrcDirs().stream()
            .filter(File::exists)
            .map(File::getAbsolutePath)
            .collect(Collectors.joining(":"));

    String classPath =
        Stream.concat(
                getClassPath().getFiles().stream(), Stream.of(SdkUtil.getAndroidJar(getProject())))
            .map(File::getAbsolutePath)
            .collect(Collectors.joining(":"));

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
                      classPath,
                      "--include-annotations",
                      "--doc-stubs",
                      getOutputDir().getAbsolutePath()));
            });
  }
}
