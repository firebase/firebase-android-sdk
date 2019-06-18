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

import groovy.transform.builder.Builder

import java.util.regex.Pattern
import org.gradle.api.Project

/** Determines a set of subprojects that own the 'changedPaths'. */
class AffectedProjectFinder {
    Project project;
    Set<String> changedPaths;

    @Builder
    AffectedProjectFinder(Project project, List<Pattern> ignorePaths) {
        this(project, changedPaths(project.rootDir), ignorePaths)
    }

    AffectedProjectFinder(Project project,
                          Set<String> changedPaths,
                          List<Pattern> ignorePaths) {
        this.project = project
        this.changedPaths = changedPaths.findAll {
            for(def path : ignorePaths) {
                if(it ==~ path) {
                    return false
                }
            }
            return true
        }
    }

    Set<Project> find() {
        Set<String> paths = changedPaths.collect()
        def projects = changedSubProjects(project, paths)

        if(!containsRootProject(projects)) {
            return projects
        }
        return project.subprojects
    }

    private static Set<String> changedPaths(File workDir) {
        return 'git diff --name-only --submodule=diff HEAD@{0} HEAD@{1}'
                .execute([], workDir)
                .text
                .readLines()
    }

    /**
     * Performs a post-order project tree traversal and returns a set of projects that own the
     * 'changedPaths'.
     */
    private static Set<Project> changedSubProjects(Project project, Set<String> changedPaths) {
        // project.subprojects include all descendents of a given project, we only want immediate
        // children.
        Set<Project> immediateChildProjects = project.subprojects.findAll { it.parent == project }

        Set<Project> projects = immediateChildProjects.collectMany {
            changedSubProjects(it, changedPaths)
        }
        def relativePath = project.rootDir.toURI().relativize(project.projectDir.toURI()).toString()

        Iterator itr = changedPaths.iterator()
        while (itr.hasNext()) {
            def file = itr.next()
            if (file.startsWith(relativePath)) {
                itr.remove()
                projects.add(project)
            }
        }
        return projects
    }

    private static boolean containsRootProject(Set<Project> projects) {
        return projects.any { it.rootProject == it };
    }
}
