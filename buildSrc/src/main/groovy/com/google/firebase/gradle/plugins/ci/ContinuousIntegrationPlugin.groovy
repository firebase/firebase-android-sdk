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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task


/**
 * Provides 'checkChanged' and 'connectedCheckChanged' tasks to the root project.
 *
 * <p>The task definition is dynamic and depends on the latest git changes in the project. Namely
 * it gets a list of changed files from the latest Git pull/merge and determines which subprojects
 * the files belong to. Then, for each affected project, it declares a dependency on the
 * 'checkDependents' or 'connectedCheckChanged' task respectively in that project.
 *
 * <p>Note: If the commits contain a file that does not belong to any subproject, *all* subprojects
 * will be built.
 */
class ContinuousIntegrationPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {

        def extension = project.extensions.create(
                "firebaseContinuousIntegration",
                ContinuousIntegrationExtension)

        project.configure(project.subprojects) {
            def checkDependents = it.task('checkDependents') {}
            def connectedCheckDependents = it.task('connectedCheckDependents')
            def deviceCheckDependents = it.task('deviceCheckDependents')

            configurations.all {
                if (it.name == 'debugUnitTestRuntimeClasspath') {
                    checkDependents.dependsOn(configurations
                            .debugUnitTestRuntimeClasspath.getTaskDependencyFromProjectDependency(
                            false, "checkDependents"))
                    checkDependents.dependsOn 'check'
                }

                if (it.name == 'debugAndroidTestRuntimeClasspath') {
                    connectedCheckDependents.dependsOn(configurations
                            .debugAndroidTestRuntimeClasspath.getTaskDependencyFromProjectDependency(
                            false, "connectedCheckDependents"))
                    connectedCheckDependents.dependsOn 'connectedCheck'

                    deviceCheckDependents.dependsOn(configurations
                            .debugAndroidTestRuntimeClasspath.getTaskDependencyFromProjectDependency(
                            false, "deviceCheckDependents"))
                    deviceCheckDependents.dependsOn 'deviceCheck'
                }

                if (it.name == 'annotationProcessor') {
                    connectedCheckDependents.dependsOn(configurations
                            .annotationProcessor.getTaskDependencyFromProjectDependency(
                            false, "connectedCheckDependents"))
                    checkDependents.dependsOn(configurations
                            .annotationProcessor.getTaskDependencyFromProjectDependency(
                            false, "checkDependents"))
                    deviceCheckDependents.dependsOn(configurations
                            .annotationProcessor.getTaskDependencyFromProjectDependency(
                            false, "deviceCheckDependents"))
                }
            }

            afterEvaluate {
                // non-android projects need to define the custom configurations due to the way
                // getTaskDependencyFromProjectDependency works.
                if (!isAndroidProject(it)) {
                    configurations {
                        debugUnitTestRuntimeClasspath
                        debugAndroidTestRuntimeClasspath
                        annotationProcessor
                    }
                    // noop task to avoid having to handle the edge-case of tasks not being
                    // defined.
                    tasks.maybeCreate('connectedCheck')
                    tasks.maybeCreate('check')
                    tasks.maybeCreate('deviceCheck')
                }
            }
        }

        def affectedProjects = AffectedProjectFinder.builder()
                .project(project)
                .changedPaths(changedPaths(project.rootDir))
                .ignorePaths(extension.ignorePaths)
                .build()
                .find()

        project.task('checkChanged') { task ->
            task.group = 'verification'
            task.description = 'Runs the check task in all changed projects.'
            affectedProjects.each {
                task.dependsOn("$it.path:checkDependents")
            }
        }
        project.task('connectedCheckChanged') { task ->
            task.group = 'verification'
            task.description = 'Runs the connectedCheck task in all changed projects.'
            affectedProjects.each {
                task.dependsOn("$it.path:connectedCheckDependents")
            }
        }

        project.task('deviceCheckChanged') { task ->
            task.group = 'verification'
            task.description = 'Runs the deviceCheck task in all changed projects.'
            affectedProjects.each {
                task.dependsOn("$it.path:deviceCheckDependents")
            }
        }

        project.task('ciTasksSanityCheck') {
            doLast {
                [':firebase-common', ':tools:errorprone'].each { projectName ->
                    def task = project.project(projectName).tasks.findByName('checkDependents')
                    def dependents = task.taskDependencies.getDependencies(task).collect { it.path}

                    def expectedDependents = [
                            'database',
                            'firestore',
                            'functions',
                            'storage'].collect { ":firebase-$it:checkDependents"}
                    assert expectedDependents.intersect(dependents) == expectedDependents :
                            "$projectName:checkDependents does not depend on expected projects"
                }
            }
        }
    }

    private static Set<String> changedPaths(File workDir) {
        return 'git diff --name-only --submodule=diff HEAD@{0} HEAD@{1}'
                .execute([], workDir)
                .text
                .readLines()
    }

    private static final ANDROID_PLUGINS = ["com.android.application", "com.android.library",
                                           "com.android.test"]

    private static boolean isAndroidProject(Project project) {
        ANDROID_PLUGINS.find { plugin -> project.plugins.hasPlugin(plugin) }
    }
}
