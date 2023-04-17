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

package com.google.firebase.gradle.plugins.publish;

import com.google.firebase.gradle.plugins.CheckHeadDependencies;
import com.google.firebase.gradle.plugins.FirebaseLibraryExtension;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.bundling.Zip;

/**
 * Enables releasing of the SDKs.
 *
 * <p>The plugin supports multiple workflows.
 *
 * <p><strong>Build all SDK snapshots</strong>
 *
 * <pre>
 * ./gradlew publishAllToLocal # publishes to maven local repo
 * ./gradlew publishAllToBuildDir # publishes to build/m2repository
 * </pre>
 *
 * <p><strong>Prepare a release</strong>
 *
 * <pre>
 * ./gradlew -PpublishConfigFilePath=release.cfg
 *           -PpublishMode=(RELEASE|SNAPSHOT) \
 *           firebasePublish
 * </pre>
 *
 * <pre>
 * ./gradlew -PprojectsToPublish="firebase-inappmessaging,firebase-inappmessaging-display"\
 *           -PpublishMode=(RELEASE|SNAPSHOT) \
 *           firebasePublish
 * </pre>
 *
 * <ul>
 *   <li>{@code publishConfigFilePath} is the path to the configuration file from which to read the
 *       list of projects to release. The file format should be consistent with Python's
 *       configparser, and the list of projects should be in a section called "modules". If both
 *       this, and {@code projectsToPublish} are specified, this property takes precedence. <br>
 *       <br>
 *       Example config file content:
 *       <pre>
 *         [release]
 *         name = M126
 *         ...
 *         [modules]
 *         firebase-database
 *         firebase-common
 *         firebase-firestore
 *       </pre>
 *   <li>{@code projectsToPublish} is a list of projects to release separated by {@code
 *       projectsToPublishSeparator}(default: ","), these projects will have their versions depend
 *       on the {@code publishMode} parameter.
 *   <li>{@code publishMode} can one of two values: {@code SNAPSHOT} results in version to be {@code
 *       "${project.version}-SNAPSHOT"}. {@code RELEASE} results in versions to be whatever is
 *       specified in the SDKs gradle.properties. Additionally when {@code RELEASE} is specified,
 *       the release validates the pom to make sure no SDKs point to unreleased SDKs.
 *   <li>{@code projectsToPublishSeparator}: separates project names in the {@code
 *       projectsToPublish} parameter. Default is: ",".
 *       <p>The artifacts will be built to build/m2repository.zip
 *       <p><strong>Prepare release(to maven local)</strong>
 *       <p>Same as above but publishes artifacts to maven local.
 *       <pre>
 * ./gradlew -PprojectsToPublish="firebase-inappmessaging,firebase-inappmessaging-display"\
 *           -PpublishMode=(RELEASE|SNAPSHOT) \
 *           publishProjectsToMavenLocal
 * </pre>
 */
public class PublishingPlugin implements Plugin<Project> {

  public PublishingPlugin() {}

  private static String getPropertyOr(Project p, String property, String defaultValue) {
    Object value = p.findProperty(property);
    if (value != null) {
      return value.toString();
    }
    return defaultValue;
  }

  private static String getPublishTask(FirebaseLibraryExtension p, String repoName) {
    return p.getPath() + ":publishMavenAarPublicationTo" + repoName;
  }

  @Override
  public void apply(Project project) {
    String projectNamesToPublish = getPropertyOr(project, "projectsToPublish", "");
    String projectsToPublishSeparator = getPropertyOr(project, "projectsToPublishSeparator", ",");
    String publishConfigFilePath = getPropertyOr(project, "publishConfigFilePath", "");

    Task publishAllToLocal = project.task("publishAllToLocal");
    Task publishAllToBuildDir = project.task("publishAllToBuildDir");
    Task firebasePublish = project.task("firebasePublish");

    project
        .getGradle()
        .projectsEvaluated(
            gradle -> {
              List<String> projectsNames;
              if (!publishConfigFilePath.isEmpty()) {
                projectsNames = readReleaseConfigFile(publishConfigFilePath);
              } else {
                projectsNames =
                    List.of(projectNamesToPublish.split(projectsToPublishSeparator, -1));
              }

              Set<String> allFirebaseProjects =
                  project.getSubprojects().stream()
                      .map(sub -> sub.getExtensions().findByType(FirebaseLibraryExtension.class))
                      .filter(ext -> ext != null)
                      .map(ext -> ext.artifactId.get())
                      .collect(Collectors.toSet());

              Set<FirebaseLibraryExtension> projectsToPublish =
                  projectsNames.stream()
                      .filter(name -> !name.isEmpty())
                      .map(
                          name ->
                              project
                                  .project(name)
                                  .getExtensions()
                                  .findByType(FirebaseLibraryExtension.class))
                      .filter(ext -> ext != null)
                      .flatMap(lib -> lib.getLibrariesToRelease().stream())
                      .collect(Collectors.toSet());

              project
                  .getExtensions()
                  .getExtraProperties()
                  .set("projectsToPublish", projectsToPublish);

              project
                  .getTasks()
                  .register(
                      "semverCheckForRelease",
                      t -> {
                        for (FirebaseLibraryExtension toPublish : projectsToPublish) {
                          t.dependsOn(toPublish.getPath() + ":semverCheck");
                        }
                      });
              project
                  .getTasks()
                  .create(
                      "validatePomForRelease",
                      t -> {
                        for (FirebaseLibraryExtension toPublish : projectsToPublish) {
                          t.dependsOn(toPublish.getPath() + ":isPomDependencyValid");
                        }
                      });
              project.subprojects(
                  sub -> {
                    if (sub.getExtensions().findByType(FirebaseLibraryExtension.class) == null)
                      return;
                    publishAllToLocal.dependsOn(
                        sub.getPath() + ":publishMavenAarPublicationToMavenLocal");
                    publishAllToBuildDir.dependsOn(
                        sub.getPath() + ":publishMavenAarPublicationToBuildDirRepository");
                  });
              project
                  .getTasks()
                  .create(
                      "checkHeadDependencies",
                      CheckHeadDependencies.class,
                      t -> {
                        t.getProjectsToPublish().set(projectsToPublish);
                        t.getAllFirebaseProjects().set(allFirebaseProjects);
                        firebasePublish.dependsOn(t);
                      });

              project
                  .getTasks()
                  .register(
                      "publishProjectsToMavenLocal",
                      t -> {
                        for (FirebaseLibraryExtension toPublish : projectsToPublish) {
                          t.dependsOn(getPublishTask(toPublish, "MavenLocal"));
                        }
                      });

              Task publishProjectsToBuildDir =
                  project
                      .getTasks()
                      .create(
                          "publishProjectsToBuildDir",
                          t -> {
                            for (FirebaseLibraryExtension toPublish : projectsToPublish) {
                              t.dependsOn(getPublishTask(toPublish, "BuildDirRepository"));
                              t.dependsOn(toPublish.getPath() + ":kotlindoc");
                            }
                          });
              Zip buildMavenZip =
                  project
                      .getTasks()
                      .create(
                          "buildMavenZip",
                          Zip.class,
                          zip -> {
                            zip.dependsOn(publishProjectsToBuildDir);
                            zip.getArchiveFileName().set("m2repository.zip");
                            zip.getDestinationDirectory().set(project.getBuildDir());
                            zip.from(project.getBuildDir() + "/m2repository");
                          });
              Zip buildKotlindocZip =
                  project
                      .getTasks()
                      .create(
                          "buildKotlindocZip",
                          Zip.class,
                          zip -> {
                            zip.dependsOn(publishProjectsToBuildDir);
                            zip.getArchiveFileName().set("kotlindoc.zip");
                            zip.getDestinationDirectory().set(project.getBuildDir());
                            zip.from(project.getBuildDir() + "/firebase-kotlindoc");
                          });
              Task info =
                  project
                      .getTasks()
                      .create(
                          "publishPrintInfo",
                          t ->
                              publishAllToLocal.doLast(
                                  it ->
                                      project
                                          .getLogger()
                                          .lifecycle(
                                              "Publishing the following libraries:\n{}",
                                              projectsToPublish.stream()
                                                  .map(FirebaseLibraryExtension::getPath)
                                                  .collect(Collectors.joining("\n")))));
              buildMavenZip.mustRunAfter(info);
              buildKotlindocZip.mustRunAfter(info);
              firebasePublish.dependsOn(info, buildMavenZip, buildKotlindocZip);
            });
  }

  private List<String> readReleaseConfigFile(String publishConfigurationFilePath) {
    try (Stream<String> stream = Files.lines(Path.of(publishConfigurationFilePath))) {
      return stream
          .dropWhile((line) -> !line.equals("[modules]"))
          // We need to skip the "[modules]" line since it's not dropped
          .skip(1)
          .collect(Collectors.toList());
    } catch (IOException e) {
      throw new IllegalArgumentException(
          "Error reading configuration file " + publishConfigurationFilePath, e);
    }
  }
}
