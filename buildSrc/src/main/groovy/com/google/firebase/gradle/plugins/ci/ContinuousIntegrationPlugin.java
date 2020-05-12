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
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;

public class ContinuousIntegrationPlugin implements Plugin<Project> {

  @Override
  public void apply(Project project) {
    ContinuousIntegrationExtension extension =
        project
            .getExtensions()
            .create("firebaseContinuousIntegration", ContinuousIntegrationExtension.class);

    project.subprojects(
        sub -> {
          Task checkDependents = sub.task("checkDependents");
          Task checkCoverageDependents = sub.task("checkCoverageDependents");
          Task connectedCheckDependents = sub.task("connectedCheckDependents");
          Task deviceCheckDependents = sub.task("deviceCheckDependents");

          sub
              .getConfigurations()
              .all(
                  cfg -> {
                    if ("releaseUnitTestRuntimeClasspath".equals(cfg.getName())) {
                      checkDependents.dependsOn(
                          cfg.getTaskDependencyFromProjectDependency(false, "checkDependents"));
                      checkDependents.dependsOn("check");

                      checkCoverageDependents.dependsOn(
                          cfg.getTaskDependencyFromProjectDependency(false, "checkDependents"));
                      checkCoverageDependents.dependsOn("checkCoverage");
                    }

                    if ("releaseAndroidTestRuntimeClasspath".equals(cfg.getName())) {
                      connectedCheckDependents.dependsOn(
                          cfg.getTaskDependencyFromProjectDependency(
                              false, "connectedCheckDependents"));
                      connectedCheckDependents.dependsOn("connectedCheck");

                      deviceCheckDependents.dependsOn(
                          cfg.getTaskDependencyFromProjectDependency(
                              false, "deviceCheckDependents"));
                      deviceCheckDependents.dependsOn("deviceCheck");
                    }

                    if ("annotationProcessor".equals(cfg.getName())) {
                      connectedCheckDependents.dependsOn(
                          cfg.getTaskDependencyFromProjectDependency(
                              false, "connectedCheckDependents"));
                      checkDependents.dependsOn(
                          cfg.getTaskDependencyFromProjectDependency(false, "checkDependents"));
                      checkCoverageDependents.dependsOn(
                          cfg.getTaskDependencyFromProjectDependency(
                              false, "checkCoverageDependents"));

                      deviceCheckDependents.dependsOn(
                          cfg.getTaskDependencyFromProjectDependency(
                              false, "deviceCheckDependents"));
                    }
                  });
          sub.afterEvaluate(
              p -> {
                if (!isAndroidProject(sub)) {
                  sub.getConfigurations().maybeCreate("releaseUnitTestRuntimeClasspath");
                  sub.getConfigurations().maybeCreate("releaseAndroidTestRuntimeClasspath");
                  sub.getConfigurations().maybeCreate("annotationProcessor");

                  sub.getTasks().maybeCreate("connectedCheck");
                  sub.getTasks().maybeCreate("check");
                  sub.getTasks().maybeCreate("deviceCheck");
                }

                if (!isFirebaseLibrary(sub)) {
                  sub.getTasks().maybeCreate("checkCoverage");
                }
              });
        });

    Set<Project> affectedProjects =
        new AffectedProjectFinder(project, extension.getIgnorePaths()).find();

    setupChangedTask(project, affectedProjects, "check");
    setupChangedTask(project, affectedProjects, "checkCoverage");
    setupChangedTask(project, affectedProjects, "deviceCheck");
  }

  private static void setupChangedTask(
      Project project, Set<Project> affectedProjects, String check) {
    project
        .getTasks()
        .create(
            check + "Changed",
            task -> {
              task.setGroup("verification");
              task.setDescription("Runs the " + check + "Changed task in all changed projects.");
              task.setDependsOn(
                  affectedProjects.stream()
                      .map(p -> p.getPath() + ":" + check + "Dependents")
                      .collect(Collectors.toList()));
            });
  }

  private static final List<String> ANDROID_PLUGINS =
      ImmutableList.of("com.android.application", "com.android.library", "com.android.test");

  private static boolean isAndroidProject(Project project) {
    return ANDROID_PLUGINS.stream()
        .anyMatch(plugin -> project.getPluginManager().hasPlugin(plugin));
  }

  private static boolean isFirebaseLibrary(Project project) {
    return project.getExtensions().findByType(FirebaseLibraryExtension.class) != null;
  }
}
