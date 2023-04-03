// Copyright 2021 Google LLC
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
package com.google.firebase.gradle;

import com.google.common.collect.ImmutableMap;
import com.google.firebase.gradle.bomgenerator.BomGeneratorTask;
import com.google.firebase.gradle.plugins.FirebaseLibraryExtension;
import com.google.firebase.gradle.plugins.publish.PublishingPlugin;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.bundling.Zip;

/**
 * Orchestrates the release process by automating validations, documentation and prebuilts
 * generation.
 *
 * <ul>
 *   <li>Pre-release validations:
 *       <ul>
 *         <li>Build maven artifacts.
 *       </ul>
 *   <li>Documentation:
 *       <ul>
 *         <li>Generates javadoc for all SDKs being released.
 *       </ul>
 *   <li>Artifact generation:
 *       <ul>
 *         <li>Releases artifacts into a maven repo in build/m2repository.
 *         <li>Bundles all artifacts into a distributable .zip file.
 *       </ul>
 * </ul>
 */
public class MultiProjectReleasePlugin implements Plugin<Project> {

  @Override
  public void apply(Project project) {
    project.apply(ImmutableMap.of("plugin", PublishingPlugin.class));

    project
        .getTasks()
        .create(
            "buildBomZip",
            Zip.class,
            task -> {
              task.dependsOn(project.getTasks().create("generateBom", BomGeneratorTask.class));
              task.from("bom");
              task.getArchiveFileName().set("bom.zip");
              task.getDestinationDirectory().set(project.getRootDir());
            });

    project.getTasks().create("generateReleaseConfig", ReleaseGenerator.class);

    project
        .getGradle()
        .projectsEvaluated(
            gradle -> {
              Set<FirebaseLibraryExtension> librariesToPublish =
                  (Set<FirebaseLibraryExtension>)
                      project.getExtensions().getExtraProperties().get("projectsToPublish");

              Set<Project> projectsToPublish =
                  librariesToPublish.stream().map(lib -> lib.project).collect(Collectors.toSet());

              Task validateProjectsToPublish =
                  project.task(
                      "validateProjectsToPublish",
                      task ->
                          task.doLast(
                              t -> {
                                if (projectsToPublish.isEmpty()) {
                                  throw new GradleException(
                                      "Required projectsToPublish parameter missing.");
                                }
                              }));

              project.getTasks().findByName("firebasePublish").dependsOn(validateProjectsToPublish);
            });
  }
}
