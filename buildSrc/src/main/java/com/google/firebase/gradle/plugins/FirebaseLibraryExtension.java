// Copyright 2019 Google LLC
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
import com.google.common.collect.ImmutableSet;
import com.google.firebase.gradle.plugins.ci.device.FirebaseTestLabExtension;
import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.internal.provider.DefaultProvider;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.provider.Property;
import org.gradle.api.publish.maven.MavenPom;

public class FirebaseLibraryExtension {

  public final Project project;
  public final LibraryType type;
  private final Set<FirebaseLibraryExtension> librariesToCoRelease = new HashSet<>();

  /** Indicates whether the library has public javadoc. */
  public boolean publishJavadoc = true;

  /** Indicates whether sources are published alongside the library. */
  public boolean publishSources;

  /** Static analysis configuration. */
  public final FirebaseStaticAnalysis staticAnalysis;

  /** Firebase Test Lab configuration/ */
  public final FirebaseTestLabExtension testLab;

  public Property<String> groupId;
  public Property<String> artifactId;

  private Action<MavenPom> customizePomAction =
      pom -> {
        pom.licenses(
            licenses ->
                licenses.license(
                    license -> {
                      license.getName().set("The Apache Software License, Version 2.0");
                      license.getUrl().set("http://www.apache.org/licenses/LICENSE-2.0.txt");
                    }));
        pom.scm(
            scm -> {
              scm.getConnection()
                  .set("scm:git:https://github.com/firebase/firebase-android-sdk.git");
              scm.getUrl().set("https://github.com/firebase/firebase-android-sdk");
            });
      };

  @Inject
  public FirebaseLibraryExtension(Project project, LibraryType type) {
    this.project = project;
    this.type = type;
    this.testLab = new FirebaseTestLabExtension(project.getObjects());
    this.artifactId = project.getObjects().property(String.class);
    this.groupId = project.getObjects().property(String.class);

    if ("ktx".equals(project.getName()) && project.getParent() != null) {
      artifactId.set(new DefaultProvider<>(() -> project.getParent().getName() + "-ktx"));
      groupId.set(new DefaultProvider<>(() -> project.getParent().getGroup().toString()));
    } else {
      artifactId.set(new DefaultProvider<>(project::getName));
      groupId.set(new DefaultProvider<>(() -> project.getGroup().toString()));
    }
    this.staticAnalysis = initializeStaticAnalysis(project);
  }

  private FirebaseStaticAnalysis initializeStaticAnalysis(Project project) {
    return new FirebaseStaticAnalysis(
        projectsFromProperty(project, "firebase.checks.errorproneProjects"),
        projectsFromProperty(project, "firebase.checks.lintProjects"));
  }

  private Set<String> projectsFromProperty(Project project, String propertyName) {
    if (!project.hasProperty(propertyName)) {
      return Collections.emptySet();
    }
    return ImmutableSet.copyOf(project.property(propertyName).toString().split(",", -1));
  }

  /** Configure Firebase Test Lab. */
  public void testLab(Action<FirebaseTestLabExtension> action) {
    action.execute(testLab);
  }

  /**
   * Register to be released alongside another Firebase Library project.
   *
   * <p>This will force the released version of the current project to match the one it's released
   * with.
   */
  public void releaseWith(Project releaseWithProject) {
    try {
      FirebaseLibraryExtension releaseWithLibrary =
          releaseWithProject.getExtensions().getByType(FirebaseLibraryExtension.class);
      releaseWithLibrary.librariesToCoRelease.add(this);
      this.project.setVersion(releaseWithProject.getVersion());

      String latestRelease = "latestReleasedVersion";
      if (releaseWithProject.hasProperty(latestRelease)) {
        this.project
            .getExtensions()
            .getExtraProperties()
            .set(latestRelease, releaseWithProject.property(latestRelease));
      }

    } catch (UnknownDomainObjectException ex) {
      throw new GradleException(
          "Library cannot be released with a project that is not a Firebase Library itself");
    }
  }

  public Set<Project> getProjectsToRelease() {
    return ImmutableSet.<Project>builder()
        .add(project)
        .addAll(librariesToCoRelease.stream().map(l -> l.project).collect(Collectors.toSet()))
        .build();
  }

  public Set<FirebaseLibraryExtension> getLibrariesToRelease() {
    return ImmutableSet.<FirebaseLibraryExtension>builder()
        .add(this)
        .addAll(librariesToCoRelease)
        .build();
  }

  /** Provides a hook to customize pom generation. */
  public void customizePom(Action<MavenPom> action) {
    customizePomAction = action;
  }

  public void applyPomCustomization(MavenPom pom) {
    if (customizePomAction != null) {
      customizePomAction.execute(pom);
    }
  }

  public void staticAnalysis(Action<FirebaseStaticAnalysis> action) {
    action.execute(staticAnalysis);
  }

  public String getVersion() {
    return project.getVersion().toString();
  }

  public Optional<String> getLatestReleasedVersion() {
    if (project.hasProperty("latestReleasedVersion")) {
      return Optional.of(project.property("latestReleasedVersion").toString());
    }
    return Optional.empty();
  }

  public String getMavenName() {
    return groupId.get() + ":" + artifactId.get();
  }

  public String getPath() {
    return project.getPath();
  }

  public Set<File> getSrcDirs() {
    switch (type) {
      case ANDROID:
        return project
            .getExtensions()
            .getByType(LibraryExtension.class)
            .getSourceSets()
            .getByName("main")
            .getJava()
            .getSrcDirs();
      case JAVA:
        return project
            .getConvention()
            .getPlugin(JavaPluginConvention.class)
            .getSourceSets()
            .getByName("main")
            .getJava()
            .getSrcDirs();
      default:
        throw new IllegalStateException("Unsupported project type: " + type);
    }
  }

  public String getRuntimeClasspath() {
    if (type.equals(LibraryType.ANDROID)) {
      return "releaseRuntimeClasspath";
    }
    return "runtimeClasspath";
  }

  @Override
  public String toString() {
    return String.format(
        "FirebaseLibraryExtension{name=\"%s:%s\", project=\"%s\", type=\"%s\"}",
        groupId.get(), artifactId.get(), getPath(), type);
  }
}
