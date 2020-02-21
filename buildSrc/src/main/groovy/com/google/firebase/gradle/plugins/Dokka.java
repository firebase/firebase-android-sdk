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

package com.google.firebase.gradle.plugins;

import com.android.build.gradle.LibraryExtension;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Optional;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RelativePath;
import org.gradle.api.tasks.Copy;
import org.jetbrains.dokka.DokkaConfiguration;
import org.jetbrains.dokka.gradle.DokkaAndroidTask;

final class Dokka {
  /**
   * Configures the dokka task for a 'firebase-library'.
   *
   * <p>Configuration includes:
   *
   * <ol>
   *   <li>Configure Metalava task for Java(non-Kotlin) libraries
   *   <li>Configure Dokka with the DAC format and full classpath for symbol resolution
   *   <li>Postprocessing of the produced Kotlindoc
   *       <ul>
   *         <li>Copy the _toc.yaml file to "client/{libName}/_toc.yaml"
   *         <li>Filter out unneeded files
   *         <li>Copy docs to the buildDir of the root project
   *         <li>Remove the "https://firebase.google.com" prefix from all urls
   */
  static void configure(
      Project project, LibraryExtension android, FirebaseLibraryExtension firebaseLibrary) {
    project.apply(ImmutableMap.of("plugin", "org.jetbrains.dokka-android"));

    if (!firebaseLibrary.publishJavadoc) {
      project.getTasks().register("kotlindoc");
      return;
    }
    DokkaAndroidTask dokkaAndroidTask =
        project
            .getTasks()
            .create(
                "kotlindocDokka",
                DokkaAndroidTask.class,
                dokka -> {
                  dokka.setOutputDirectory(project.getBuildDir() + "/dokka/firebase");
                  dokka.setOutputFormat("dac");

                  dokka.setGenerateClassIndexPage(false);
                  dokka.setGeneratePackageIndexPage(false);
                  if (!project.getPluginManager().hasPlugin("kotlin-android")) {
                    dokka.dependsOn("docStubs");
                    dokka.setSourceDirs(
                        Collections.singletonList(
                            project.file(project.getBuildDir() + "/doc-stubs")));
                  }

                  dokka.setNoAndroidSdkLink(true);

                  createLink(
                          project,
                          "https://developers.android.com/reference/kotlin/",
                          "kotlindoc/package-lists/android/package-list")
                      .map(dokka.getExternalDocumentationLinks()::add);
                  createLink(
                          project,
                          "https://developers.google.com/android/reference/",
                          "kotlindoc/package-lists/google/package-list")
                      .map(dokka.getExternalDocumentationLinks()::add);
                  createLink(
                          project,
                          "https://firebase.google.com/docs/reference/kotlin/",
                          "kotlindoc/package-lists/firebase/package-list")
                      .map(dokka.getExternalDocumentationLinks()::add);
                  createLink(
                          project,
                          "https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/",
                          "kotlindoc/package-lists/coroutines/package-list")
                      .map(dokka.getExternalDocumentationLinks()::add);

                  android
                      .getLibraryVariants()
                      .all(
                          v -> {
                            if (v.getName().equals("release")) {
                              project.afterEvaluate(
                                  p -> {
                                    FileCollection artifactFiles =
                                        v.getRuntimeConfiguration()
                                            .getIncoming()
                                            .artifactView(
                                                view -> {
                                                  view.attributes(
                                                      attrs ->
                                                          attrs.attribute(
                                                              Attribute.of(
                                                                  "artifactType", String.class),
                                                              "jar"));
                                                  view.componentFilter(
                                                      c ->
                                                          !c.getDisplayName()
                                                              .startsWith(
                                                                  "androidx.annotation:annotation:"));
                                                })
                                            .getArtifacts()
                                            .getArtifactFiles()
                                            .plus(project.files(android.getBootClasspath()));
                                    dokka.setClasspath(artifactFiles);
                                  });
                            }
                          });
                });
    project
        .getTasks()
        .create(
            "kotlindoc",
            Copy.class,
            copy -> {
              copy.dependsOn(dokkaAndroidTask);
              copy.setDestinationDir(
                  project.file(project.getRootProject().getBuildDir() + "/firebase-kotlindoc"));
              copy.from(
                  project.getBuildDir() + "/dokka/firebase",
                  cfg -> {
                    cfg.exclude("package-list");
                    cfg.filesMatching(
                        "_toc.yaml",
                        fileCopy -> {
                          fileCopy.setRelativePath(
                              new RelativePath(
                                  true, "client", firebaseLibrary.artifactId.get(), "_toc.yaml"));
                          fileCopy.filter(
                              line ->
                                  line.replaceFirst(
                                      "\\spath:\\s/", " path: /docs/reference/kotlin/"));
                        });
                    cfg.filesMatching(
                        "**/*.html",
                        fileCopy ->
                            fileCopy.filter(
                                line -> line.replaceAll("https://firebase.google.com", "")));
                  });
            });
  }

  private static Optional<DokkaConfiguration.ExternalDocumentationLink> createLink(
      Project project, String url, String packageListPath) {

    File packageListFile = project.getRootProject().file(packageListPath);
    if (!packageListFile.exists()) {
      return Optional.empty();
    }
    try {
      DokkaConfiguration.ExternalDocumentationLink.Builder builder =
          new DokkaConfiguration.ExternalDocumentationLink.Builder();
      builder.setUrl(new URL(url));
      builder.setPackageListUrl(packageListFile.toURI().toURL());
      return Optional.of(builder.build());
    } catch (MalformedURLException e) {
      throw new GradleException("Could not parse url", e);
    }
  }
}
