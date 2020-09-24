// Copyright 2018 Google LLC
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

import com.google.firebase.gradle.plugins.FirebaseLibraryExtension;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ProjectDependency;
import org.json.JSONArray;
import org.json.JSONObject;

/** Builds Firebase libraries for consumption by the smoke tests. */
public class SmokeTestsPlugin implements Plugin<Project> {
  @Override
  public void apply(Project project) {
    Task assembleAllTask = project.task("assembleAllForSmokeTests");

    // Wait until after the projects have been evaluated or else we might skip projects.
    project
        .getGradle()
        .projectsEvaluated(
            gradle -> {
              Set<Project> changedProjects = getChangedProjects(project);
              Set<String> changedArtifacts = new HashSet<>();
              Set<String> allArtifacts = new HashSet<>();

              // Visit each project and add the artifacts to the appropriate sets.
              project.subprojects(
                  sub -> {
                    FirebaseLibraryExtension firebaseLibrary =
                        sub.getExtensions().findByType(FirebaseLibraryExtension.class);
                    if (firebaseLibrary == null) {
                      return;
                    }

                    String groupId = firebaseLibrary.groupId.get();
                    String artifactId = firebaseLibrary.artifactId.get();
                    String artifact =
                        String.format("%s:%s:%s-SNAPSHOT", groupId, artifactId, sub.getVersion());
                    allArtifacts.add(artifact);

                    if (changedProjects.contains(sub)) {
                      changedArtifacts.add(artifact);
                    }
                  });

              // Reuse the publish task for building the libraries.
              Task publishAllTask = project.getTasks().getByPath("publishAllToBuildDir");
              assembleAllTask.dependsOn(publishAllTask);

              // Generate a JSON file listing the artifacts after everything is complete.
              assembleAllTask.doLast(
                  task -> {
                    JSONArray changed = new JSONArray();
                    changedArtifacts.forEach(changed::put);

                    JSONArray all = new JSONArray();
                    allArtifacts.forEach(all::put);

                    JSONObject json = new JSONObject();
                    json.put("headGit", all);
                    json.put("default", changed);

                    Path path = project.getBuildDir().toPath();
                    Path jsonFile =
                        path.resolve(
                            "m2repository/changed-artifacts.json"); // .write(json.toString())
                    try {
                      Files.write(
                          jsonFile,
                          Collections.singleton(json.toString()),
                          Charset.defaultCharset());
                    } catch (IOException e) {
                      throw new GradleException("Failed to write '" + jsonFile + "' json file.", e);
                    }
                  });
            });
  }

  private static Set<Project> getChangedProjects(Project p) {
    Set<Project> roots = new AffectedProjectFinder(p, Collections.emptyList()).find();
    HashSet<Project> changed = new HashSet<>();

    getChangedProjectsLoop(roots, changed);
    return changed;
  }

  private static void getChangedProjectsLoop(Collection<Project> projects, Set<Project> changed) {
    for (Project p : projects) {
      // Skip project if it is not a Firebase library.
      FirebaseLibraryExtension library =
          p.getExtensions().findByType(FirebaseLibraryExtension.class);
      if (library == null) {
        continue;
      }

      // Skip processing and recursion if this project has already been added to the set.
      if (!changed.add(p)) {
        continue;
      }

      // Find all (head) dependencies to other projects in this repository.
      DependencySet all =
          p.getConfigurations().getByName(library.getRuntimeClasspath()).getAllDependencies();
      Set<Project> affected =
          all.stream()
              .filter(it -> it instanceof ProjectDependency)
              .map(it -> (ProjectDependency) it)
              .map(ProjectDependency::getDependencyProject)
              .collect(Collectors.toSet());

      // Recurse with the new dependencies.
      getChangedProjectsLoop(affected, changed);
    }
  }
}
