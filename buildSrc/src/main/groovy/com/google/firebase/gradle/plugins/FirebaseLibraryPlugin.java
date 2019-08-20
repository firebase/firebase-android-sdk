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

package com.google.firebase.gradle.plugins;

import com.android.build.gradle.LibraryExtension;
import com.android.build.gradle.api.AndroidSourceSet;
import com.android.build.gradle.api.LibraryVariant;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.gradle.plugins.apiinfo.GenerateApiTxtFileTask;
import com.google.firebase.gradle.plugins.apiinfo.ApiInformationTask;
import com.google.firebase.gradle.plugins.apiinfo.GetMetalavaJarTask;
import com.google.firebase.gradle.plugins.ci.device.FirebaseTestServer;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile;
import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class FirebaseLibraryPlugin implements Plugin<Project> {

  @Override
  public void apply(Project project) {
    project.apply(ImmutableMap.of("plugin", "com.android.library"));

    FirebaseLibraryExtension firebaseLibrary =
        project.getExtensions().create("firebaseLibrary", FirebaseLibraryExtension.class, project);

    LibraryExtension android = project.getExtensions().getByType(LibraryExtension.class);

    // In the case of and android library signing config only affects instrumentation test APK.
    // We need it signed with default debug credentials in order for FTL to accept the APK.
    android.buildTypes(
        types ->
            types
                .getByName("release")
                .setSigningConfig(types.getByName("debug").getSigningConfig()));

    // skip debug tests in CI
    // TODO(vkryachko): provide ability for teams to control this if needed
    if (System.getenv().containsKey("FIREBASE_CI")) {
      android.setTestBuildType("release");
      project
          .getTasks()
          .all(
              task -> {
                if ("testDebugUnitTest".equals(task.getName())) {
                  task.setEnabled(false);
                }
              });
    }

    setupApiInformationAnalysis(project, android);

    android.testServer(new FirebaseTestServer(project, firebaseLibrary.testLab));

    setupStaticAnalysis(project, android, firebaseLibrary);

    // reduce the likelihood of kotlin module files colliding.
    project
        .getTasks()
        .withType(
            KotlinCompile.class,
            kotlin ->
                kotlin
                    .getKotlinOptions()
                    .setFreeCompilerArgs(
                        ImmutableList.of("-module-name", kotlinModuleName(project))));
  }

  private static void setupApiInformationAnalysis(Project project, LibraryExtension android) {
    File metalavaOutputJarFile = new File(project.getRootProject().getBuildDir(), "metalava.jar");
    AndroidSourceSet mainSourceSet = android.getSourceSets().getByName("main");
    File outputFile = project.getRootProject().file(Paths.get(
        project.getRootProject().getBuildDir().getPath(),
        "apiinfo",
        project.getPath().substring(1).replace(":", "_")));
    File outputApiFile = new File(outputFile.getAbsolutePath() + "_api.txt");
    List<File> sourcePath = mainSourceSet.getJava().getSrcDirs().stream().collect(Collectors.toList());
    if(mainSourceSet.getJava().getSrcDirs().stream().noneMatch(File::exists)) {
      return;
    }
    project.getTasks().register("getMetalavaJar", GetMetalavaJarTask.class, task -> {
      task.setOutputFile(metalavaOutputJarFile);
    });
    project.getTasks().register("apiInformation", ApiInformationTask.class, task -> {
      task.setApiTxt(project.file("api.txt"));
      task.setMetalavaJarPath(metalavaOutputJarFile.getAbsolutePath());
      task.setSourcePath(sourcePath);
      task.setOutputFile(outputFile);
      task.setBaselineFile(project.file("baseline.txt"));
      task.setOutputApiFile(outputApiFile);
      if (project.hasProperty("updateBaseline")) {
        task.setUpdateBaseline(true);
      } else {
        task.setUpdateBaseline(false);
      }
      task.dependsOn("getMetalavaJar");
    });

    project.getTasks().register("generateApiTxtFile", GenerateApiTxtFileTask.class, task -> {
      task.setApiTxt(project.file("api.txt"));
      task.setMetalavaJarPath(metalavaOutputJarFile.getAbsolutePath());
      task.setSourcePath(sourcePath);
      task.setBaselineFile(project.file("baseline.txt"));
      if (project.hasProperty("updateBaseline")) {
        task.setUpdateBaseline(true);
      } else {
        task.setUpdateBaseline(false);
      }
      task.dependsOn("getMetalavaJar");
    });
  }

  private static void setupStaticAnalysis(
      Project project, LibraryExtension android, FirebaseLibraryExtension library) {
    project.afterEvaluate(
        p ->
            project
                .getConfigurations()
                .all(
                    c -> {
                      if ("annotationProcessor".equals(c.getName())) {
                        for (String checkProject : library.staticAnalysis.errorproneCheckProjects) {
                          project
                              .getDependencies()
                              .add("annotationProcessor", project.project(checkProject));
                        }
                      }
                      if ("lintChecks".equals(c.getName())) {
                        for (String checkProject :
                            library.staticAnalysis.androidLintCheckProjects) {
                          project
                              .getDependencies()
                              .add("lintChecks", project.project(checkProject));
                        }
                      }
                    }));

    project.getTasks().register("firebaseLint", task -> task.dependsOn("lint"));
  }

  private static String kotlinModuleName(Project project) {

    String fullyQualifiedProjectPath = project.getPath().replaceAll(":", "-");

    return project.getRootProject().getName() + fullyQualifiedProjectPath;
  }
}
