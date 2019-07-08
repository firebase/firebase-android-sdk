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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.firebase.gradle.plugins.ci.device.FirebaseTestServer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile;

import java.util.Set;

public class FirebaseLibraryPlugin implements Plugin<Project> {

  private static final Set<String> KOTLIN_CHECKS =
      ImmutableSet.of(
          "FirebaseNoHardKeywords",
          "FirebaseLambdaLast",
          "FirebaseUnknownNullness",
          "FirebaseKotlinPropertyAccess");

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

    library.staticAnalysis.subscribeToKotlinInteropLintDisabled(
        () ->
            android.lintOptions(
                lintOptions -> lintOptions.disable(KOTLIN_CHECKS.toArray(new String[0]))));

    project.getTasks().register("firebaseLint", task -> task.dependsOn("lint"));
  }

  private static String kotlinModuleName(Project project) {

    String fullyQualifiedProjectPath = project.getPath().replaceAll(":", "-");

    return project.getRootProject().getName() + fullyQualifiedProjectPath;
  }
}
