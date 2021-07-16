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

import static com.google.firebase.gradle.plugins.ProjectUtilsKt.toBoolean;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.gradle.bomgenerator.BomGeneratorTask;
import com.google.firebase.gradle.plugins.FireEscapeArtifactPlugin;
import com.google.firebase.gradle.plugins.FirebaseLibraryExtension;
import com.google.firebase.gradle.plugins.JavadocPlugin;
import com.google.firebase.gradle.plugins.TasksKt;
import com.google.firebase.gradle.plugins.publish.PublishingPlugin;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.Delete;
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

    boolean releaseJavadocs = toBoolean(System.getProperty("releaseJavadocs", "true"));

    File firebaseDevsiteJavadoc = new File(project.getBuildDir(), "firebase-javadocs/");
    File firebaseClientBuildDest = new File(firebaseDevsiteJavadoc, "client/");
    firebaseClientBuildDest.mkdirs();

    project.subprojects(
        sub -> {
          sub.afterEvaluate(
              p -> {
                sub.apply(ImmutableMap.of("plugin", JavadocPlugin.class));
                sub.apply(ImmutableMap.of("plugin", FireEscapeArtifactPlugin.class));
              });
        });

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
              Task firebasePublish = project.getTasks().findByName("firebasePublish");
              firebasePublish.dependsOn(validateProjectsToPublish);

              Task generateAllJavadocs =
                  project.task(
                      "generateAllJavadocs",
                      task -> {
                        for (Project p : projectsToPublish) {
                          task.dependsOn(p.getPath() + ":" + TasksKt.JAVADOC_TASK_NAME);

                          task.doLast(
                              t -> {
                                for (Project publishableProject : projectsToPublish) {
                                  publishableProject.copy(
                                      copy -> {
                                        copy.from(
                                            publishableProject.getBuildDir()
                                                + "/docs/javadoc/reference");
                                        copy.include("**/*");
                                        copy.into(firebaseDevsiteJavadoc);
                                      });

                                  publishableProject.copy(
                                      copy -> {
                                        copy.from(
                                            publishableProject.getBuildDir()
                                                + "/docs/javadoc/reference/_toc.yaml");
                                        copy.include("**/*");
                                        copy.into(
                                            firebaseClientBuildDest
                                                + "/"
                                                + publishableProject.getName());
                                      });
                                }
                              });
                        }
                      });

              Delete prepareJavadocs =
                  project
                      .getTasks()
                      .create(
                          "prepareJavadocs",
                          Delete.class,
                          del -> {
                            del.dependsOn(generateAllJavadocs);
                            del.doLast(
                                d -> {
                                  // cleanup docs
                                  project.delete(
                                      delSpec -> {
                                        ImmutableList<String> relativeDeletablePaths =
                                            ImmutableList.of(
                                                "timestamp.js",
                                                "navtree_data.js",
                                                "assets/",
                                                "classes.html",
                                                "hierarchy.html",
                                                "lists.js",
                                                "package-list",
                                                "packages.html",
                                                "index.html",
                                                "current.xml",
                                                "_toc.yaml");
                                        delSpec.delete(
                                            relativeDeletablePaths.stream()
                                                .map(
                                                    path ->
                                                        firebaseDevsiteJavadoc.getPath()
                                                            + "/"
                                                            + path)
                                                .collect(Collectors.toList()));
                                      });
                                  // Transform
                                  project.exec(
                                      execSpec -> {
                                        execSpec.setIgnoreExitValue(true);
                                        execSpec.setWorkingDir(firebaseDevsiteJavadoc);
                                        execSpec.setCommandLine(
                                            project.getRootProject().file("buildSrc").getPath()
                                                + "/firesite_transform.sh");
                                      });

                                  // Tidy
                                  String tidyBinary = System.getProperty("tidyBinaryPath", null);
                                  String tidyConfig = System.getProperty("tidyConfigPath", null);
                                  if (tidyBinary != null && tidyConfig != null) {
                                    try {
                                      Files.walk(firebaseDevsiteJavadoc.toPath())
                                          .filter(
                                              p ->
                                                  p.toFile().isFile()
                                                      && p.toString().endsWith(".html"))
                                          .forEach(
                                              p -> {
                                                project.exec(
                                                    execSpec -> {
                                                      System.out.println("Tidying " + p);
                                                      execSpec.setIgnoreExitValue(true);
                                                      execSpec.commandLine(
                                                          tidyBinary, "-config", tidyConfig, p);
                                                    });
                                              });
                                    } catch (IOException e) {
                                      throw new GradleException("Directory walk failed.", e);
                                    }
                                  }
                                });
                          });

              Zip assembleFirebaseJavadocZip =
                  project
                      .getTasks()
                      .create(
                          "assembleFirebaseJavadocZip",
                          Zip.class,
                          zip -> {
                            zip.dependsOn(prepareJavadocs);
                            zip.getDestinationDirectory().set(project.getBuildDir());
                            zip.getArchiveFileName().set("firebase-javadoc.zip");
                            zip.from(firebaseDevsiteJavadoc);
                            zip.include("**/*");
                          });

              if (releaseJavadocs) {
                firebasePublish.dependsOn(assembleFirebaseJavadocZip);
              }
            });
  }
}
