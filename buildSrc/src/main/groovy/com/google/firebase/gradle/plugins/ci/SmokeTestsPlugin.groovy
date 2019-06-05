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

package com.google.firebase.gradle.plugins.ci

import com.google.firebase.gradle.plugins.FirebaseLibraryExtension
import com.google.firebase.gradle.plugins.ci.AffectedProjectFinder
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.json.JSONArray
import org.json.JSONObject

/** Builds Firebase libraries for consumption by the smoke tests. */
class SmokeTestsPlugin implements Plugin<Project> {
  @Override
  public void apply(Project project) {
    def assembleAllTask = project.task("assembleAllForSmokeTests")

    // Wait until after the projects have been evaluated or else we might skip projects.
    project.gradle.projectsEvaluated {
      def changedProjects = getChangedProjects(project)
      def changedArtifacts = new HashSet<String>()
      def allArtifacts = new HashSet<String>()

      // Visit each project and add the artifacts to the appropriate sets.
      project.subprojects {
        def firebaseLibrary = it.extensions.findByType(FirebaseLibraryExtension)
	if (firebaseLibrary == null) {
	  return
	}

        def groupId = firebaseLibrary.groupId.get()
        def artifactId = firebaseLibrary.artifactId.get()
        def artifact = "$groupId:$artifactId:$it.version-SNAPSHOT"
        allArtifacts.add(artifact)

        if (changedProjects.contains(it)) {
          changedArtifacts.add(artifact)
        }
      }

      // Reuse the publish task for building the libraries.
      def publishAllTask = project.tasks.getByPath("publishAllToBuildDir")
      assembleAllTask.dependsOn(publishAllTask)

      // Generate a JSON file listing the artifacts after everything is complete.
      assembleAllTask.doLast {
        def changed = new JSONArray()
        changedArtifacts.each { changed.put(it) }

        def all = new JSONArray()
        allArtifacts.each { all.put(it) }

        def json = new JSONObject()
        json.put("all", all)
        json.put("changed", changed)

        def path = project.buildDir.toPath()
        path.resolve("m2repository/changed-artifacts.json").write(json.toString())
      }
    }
  }

  private static Set<Project> getChangedProjects(Project p) {
    Set<Project> roots = new AffectedProjectFinder(p, []).find()
    HashSet<Project> changed = new HashSet<>()

    getChangedProjectsLoop(roots, changed)
    return changed
  }

  private static void getChangedProjectsLoop(Collection<Project> projects, Set<Project> changed) {
    for (Project p : projects) {
      // Skip project if it is not a Firebase library.
      if (p.extensions.findByType(FirebaseLibraryExtension) == null) {
        continue;
      }

      // Skip processing and recursion if this project has already been added to the set.
      if (!changed.add(p)) {
        continue;
      }

      // Find all (head) dependencies to other projects in this respository.
      def all = p.configurations.releaseRuntimeClasspath.allDependencies
      def affected =
          all.findAll { it instanceof ProjectDependency }.collect { it.getDependencyProject() }

      // Recurse with the new dependencies.
      getChangedProjectsLoop(affected, changed)
    }
  }
}
