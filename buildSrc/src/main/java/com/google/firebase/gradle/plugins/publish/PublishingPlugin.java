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

import com.google.common.collect.ImmutableMap;
import com.google.firebase.gradle.plugins.FirebaseLibraryExtension;
import digital.wup.android_maven_publish.AndroidMavenPublishPlugin;
import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.tasks.bundling.Jar;
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
 * ./gradlew -PprojectsToPublish="firebase-inappmessaging,firebase-inappmessaging-display"\
 *           -PpublishMode=(RELEASE|SNAPSHOT) \
 *           firebasePublish
 * </pre>
 *
 * <ul>
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

  @Override
  public void apply(Project project) {
    String projectNamesToPublish = getPropertyOr(project, "projectsToPublish", "");
    String projectsToPublishSeparator = getPropertyOr(project, "projectsToPublishSeparator", ",");
    Mode publishMode = Enum.valueOf(Mode.class, getPropertyOr(project, "publishMode", "SNAPSHOT"));

    Task publishAllToLocal = project.task("publishAllToLocal");
    Task publishAllToBuildDir = project.task("publishAllToBuildDir");
    Task firebasePublish = project.task("firebasePublish");

    project
        .getGradle()
        .projectsEvaluated(
            gradle -> {
              Set<FirebaseLibraryExtension> projectsToPublish =
                  Arrays.stream(projectNamesToPublish.split(projectsToPublishSeparator, -1))
                      .filter(name -> !name.isEmpty())
                      .map(
                          name ->
                              project
                                  .project(name)
                                  .getExtensions()
                                  .getByType(FirebaseLibraryExtension.class))
                      .flatMap(lib -> lib.getLibrariesToRelease().stream())
                      .collect(Collectors.toSet());
              project
                  .getExtensions()
                  .getExtraProperties()
                  .set("projectsToPublish", projectsToPublish);

              Publisher publisher = new Publisher(publishMode, projectsToPublish);
              project.subprojects(
                  sub -> {
                    FirebaseLibraryExtension firebaseLibrary =
                        sub.getExtensions().findByType(FirebaseLibraryExtension.class);
                    if (firebaseLibrary == null) {
                      return;
                    }

                    sub.apply(ImmutableMap.of("plugin", AndroidMavenPublishPlugin.class));
                    PublishingExtension publishing =
                        sub.getExtensions().getByType(PublishingExtension.class);
                    publishing.repositories(
                        repos ->
                            repos.maven(
                                repo -> {
                                  repo.setUrl(
                                      URI.create(
                                          "file://"
                                              + sub.getRootProject().getBuildDir()
                                              + "/m2repository"));
                                  repo.setName("BuildDir");
                                }));
                    publishing.publications(
                        publications ->
                            publications.create(
                                "mavenAar",
                                MavenPublication.class,
                                publication -> {
                                  publication.from(
                                      sub.getComponents()
                                          .findByName(firebaseLibrary.type.getComponentName()));
                                  publication.setArtifactId(firebaseLibrary.artifactId.get());
                                  publication.setGroupId(firebaseLibrary.groupId.get());
                                  if (firebaseLibrary.publishSources) {
                                    publication.artifact(
                                        sub.getTasks()
                                            .create(
                                                "sourceJar",
                                                Jar.class,
                                                jar -> {
                                                  jar.from(firebaseLibrary.getSrcDirs());
                                                  jar.getArchiveClassifier().set("sources");
                                                }));
                                  }
                                  firebaseLibrary.applyPomCustomization(publication.getPom());
                                  publisher.decorate(firebaseLibrary, publication);
                                }));
                    publishAllToLocal.dependsOn(
                        sub.getPath() + ":publishMavenAarPublicationToMavenLocal");
                    publishAllToBuildDir.dependsOn(
                        sub.getPath() + ":publishMavenAarPublicationToBuildDirRepository");
                  });
              project
                  .getTasks()
                  .create(
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
}
