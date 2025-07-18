// Copyright 2020 Google LLC
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

package com.google.firebase.gradle.plugins.ci;

import com.google.common.collect.ImmutableList;
import com.google.firebase.gradle.plugins.FirebaseLibraryExtension;
import java.io.File;
import java.util.List;
import java.util.Map;
import org.gradle.api.Project;
import org.gradle.api.tasks.testing.Test;
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension;
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension;
import org.gradle.testing.jacoco.tasks.JacocoReport;

// TODO(b/372719915): Migrate to Kotlin/Modern gradle plugin
public final class Coverage {

  private Coverage() {}

  public static void apply(FirebaseLibraryExtension firebaseLibrary) {
    Project project = firebaseLibrary.getProject();
    project.apply(Map.of("plugin", "jacoco"));
    File reportsDir = new File(project.getBuildDir(), "/reports/jacoco");
    JacocoPluginExtension jacoco = project.getExtensions().getByType(JacocoPluginExtension.class);

    jacoco.setToolVersion("0.8.13");
    jacoco.getReportsDirectory().set(reportsDir);
    project
        .getTasks()
        .withType(
            Test.class,
            test -> {
              JacocoTaskExtension testJacoco =
                  test.getExtensions().getByType(JacocoTaskExtension.class);
              testJacoco.setExcludeClassLoaders(List.of("jdk.internal.*"));
              testJacoco.setIncludeNoLocationClasses(true);
            });

    project
        .getTasks()
        .create(
            "checkCoverage",
            JacocoReport.class,
            task -> {
              task.dependsOn("check");
              task.setDescription("Generates JaCoCo check coverage report.");
              task.setGroup("verification");

              List<String> excludes =
                  ImmutableList.<String>builder()
                      .add("**/R.class")
                      .add("**/R$*.class")
                      .add("**/BuildConfig.*")
                      .add("**/proto/**")
                      .add("**Manifest*.*")
                      .build();

              task.getClassDirectories()
                  .setFrom(
                      project.files(
                          project.fileTree(
                              Map.of(
                                  "dir",
                                  project.getBuildDir() + "/intermediates/javac/release",
                                  "excludes",
                                  excludes)),
                          project.fileTree(
                              Map.of(
                                  "dir",
                                  project.getBuildDir() + "/tmp/kotlin-classes/release",
                                  "excludes",
                                  excludes))));
              task.getSourceDirectories()
                  .setFrom(project.files("src/main/java", "src/main/kotlin"));
              task.getExecutionData()
                  .setFrom(
                      project.fileTree(
                          Map.of(
                              "dir",
                              project.getBuildDir(),
                              "includes",
                              ImmutableList.of("jacoco/*.exec"))));
              task.reports(
                  reports -> {
                    reports
                        .getHtml()
                        .setDestination(
                            new File(reportsDir, firebaseLibrary.getArtifactId().get() + "/html"));
                    reports.getXml().getRequired().set(true);
                    reports
                        .getXml()
                        .setDestination(
                            new File(reportsDir, firebaseLibrary.getArtifactId().get() + ".xml"));
                  });
              task.getOutputs().upToDateWhen(t -> false);
            });
  }
}
