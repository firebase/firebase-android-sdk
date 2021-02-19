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
package com.google.firebase.gradle.plugins;

import java.io.File;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.bundling.Zip;

/**
 * Injects release related artifact into the maven publication.
 *
 * <p>The artifact contains the following files in it:
 *
 * <ul>
 *   <li>api-diff.txt
 *   <li>api.txt
 *   <li>mapping.txt(optional,only present if library is proguarded)
 * </ul>
 */
public class FireEscapeArtifactPlugin implements Plugin<Project> {
  private Project project;

  @Override
  public void apply(Project project) {
    this.project = project;
    project.afterEvaluate(
        p -> {
          FirebaseLibraryExtension firebaseLibrary =
              p.getExtensions().findByType(FirebaseLibraryExtension.class);
          if (firebaseLibrary == null) {
            return;
          }

          Zip fireEscapeTask =
              p.getTasks()
                  .create(
                      "mavenAarFireEscapeArtifact", Zip.class, t -> t.setClassifier("fireescape"));

          p.getPlugins()
              .withType(
                  MavenPublishPlugin.class,
                  plugin ->
                      p.getExtensions()
                          .configure(
                              PublishingExtension.class,
                              pub ->
                                  pub.getPublications()
                                      .withType(
                                          MavenPublication.class,
                                          publication -> {
                                            if ("mavenAar".equals(publication.getName())) {
                                              configurePublication(
                                                  publication,
                                                  fireEscapeTask,
                                                  firebaseLibrary.type);
                                            }
                                          })));
        });
  }

  private void configurePublication(
      MavenPublication publication, Zip artifactTask, LibraryType libraryType) {
    publication.artifact(artifactTask);
    artifactTask.from(apiTxtFileTask());
    if (libraryType.equals(LibraryType.ANDROID)) {
      artifactTask.from(proguardMappingFileTask());
    }
    publication.artifact(javadocTask());
  }

  private Task proguardMappingFileTask() {
    return project
        .getTasks()
        .create(
            "fireEscapeProguardMapping",
            task -> {
              project
                  .getTasks()
                  .all(
                      it -> {
                        if (it.getName().equals("assembleRelease")) {
                          task.dependsOn(it);
                        }
                      });
              task.getOutputs()
                  .file(new File(project.getBuildDir(), "outputs/mapping/release/mapping.txt"));
            });
  }

  private Task apiTxtFileTask() {
    return project
        .getTasks()
        .create(
            "fireEscapeApiTxt",
            task -> {
              task.dependsOn(TasksKt.JAVADOC_TASK_NAME);
              task.getOutputs().file(new File(project.getBuildDir(), "tmp/javadoc/api.txt"));
            });
  }

  private Task javadocTask() {
    return project
        .getTasks()
        .create(
            "fireescapeJavadocJar",
            Jar.class,
            javadoc -> {
              javadoc.dependsOn(TasksKt.JAVADOC_TASK_NAME);
              javadoc.from(new File(project.getBuildDir(), "/docs/javadoc/reference"));
              javadoc.include("**/*");
              javadoc.setArchiveName("fireescape-javadoc.jar");
              javadoc.setClassifier("javadoc");
            });
  }
}
