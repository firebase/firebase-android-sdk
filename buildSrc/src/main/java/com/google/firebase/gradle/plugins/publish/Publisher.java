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

import com.google.common.collect.ImmutableSet;
import com.google.firebase.gradle.plugins.FirebaseLibraryExtension;
import com.google.firebase.gradle.plugins.LibraryType;
import java.io.File;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.publish.maven.MavenPublication;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class Publisher {
  private static String UNRELEASED_VERSION = "unreleased";

  private static final Set<String> SUPPORT_GROUP_IDS =
      ImmutableSet.of("com.android.support", "androidx.multidex");

  private final Mode mode;
  private final Set<FirebaseLibraryExtension> librariesToPublish;

  Publisher(Mode mode, Set<FirebaseLibraryExtension> librariesToPublish) {
    this.mode = mode;
    this.librariesToPublish = librariesToPublish;
  }

  public String determineVersion(FirebaseLibraryExtension library) {
    if (librariesToPublish.isEmpty() || librariesToPublish.contains(library)) {
      return renderVersion(library.getVersion(), mode);
    }
    return library.getLatestReleasedVersion().orElse(UNRELEASED_VERSION);
  }

  public void decorate(FirebaseLibraryExtension library, MavenPublication publication) {
    publication.setVersion(determineVersion(library));
    publication
        .getPom()
        .withXml(
            xml -> {
              Element rootElement = xml.asElement();
              validatePomXml(library, rootElement);
              processDependencies(library, rootElement);
            });
  }

  private static void validatePomXml(FirebaseLibraryExtension library, Element rootElement) {
    NodeList dependencies = rootElement.getElementsByTagName("dependency");
    List<String> unreleased = new ArrayList<>();
    for (int i = 0; i < dependencies.getLength(); i++) {
      Element dep = (Element) dependencies.item(i);
      Element version = (Element) dep.getElementsByTagName("version").item(0);
      if (UNRELEASED_VERSION.equals(version.getTextContent().trim())) {
        String groupId = dep.getElementsByTagName("groupId").item(0).getTextContent();
        String artifactId = dep.getElementsByTagName("artifactId").item(0).getTextContent();
        unreleased.add(String.format("%s:%s", groupId, artifactId));
      }
    }
    if (!unreleased.isEmpty()) {
      throw new GradleException(
          String.format(
              "Failed to release %s. Some of its dependencies don't have a released version: %s",
              library.getMavenName(), unreleased));
    }
  }

  private static void processDependencies(FirebaseLibraryExtension library, Element rootElement) {
    Map<String, String> deps = getDependencyTypes(library);

    NodeList dependencies = rootElement.getElementsByTagName("dependency");
    List<Element> depsToRemove = new ArrayList<>();
    for (int i = 0; i < dependencies.getLength(); i++) {
      Element dep = (Element) dependencies.item(i);
      String groupId = dep.getElementsByTagName("groupId").item(0).getTextContent();
      String artifactId = dep.getElementsByTagName("artifactId").item(0).getTextContent();

      if (SUPPORT_GROUP_IDS.contains(groupId) && "multidex".equals(artifactId)) {
        depsToRemove.add(dep);
        continue;
      }

      Element type = dep.getOwnerDocument().createElement("type");
      type.setTextContent(deps.get(groupId + ":" + artifactId));
      dep.appendChild(type);

      NodeList scopes = dep.getElementsByTagName("scope");
      if (scopes.getLength() > 0) {
        dep.removeChild(scopes.item(0));
      }
      Element scope = dep.getOwnerDocument().createElement("scope");
      scope.setTextContent("compile");
      dep.appendChild(scope);
    }
    for (Element dep : depsToRemove) {
      dep.getParentNode().removeChild(dep);
    }
  }

  private static String renderVersion(String baseVersion, Mode mode) {
    return baseVersion + (Mode.SNAPSHOT.equals(mode) ? "-SNAPSHOT" : "");
  }

  private static Map<String, String> getDependencyTypes(FirebaseLibraryExtension firebaseLibrary) {
    Project project = firebaseLibrary.project;
    Configuration dummyDependencyConfiguration =
        project.getConfigurations().create("publisherDummyConfig");
    Set<Dependency> nonProjectDependencies =
        project
            .getConfigurations()
            .getByName(firebaseLibrary.getRuntimeClasspath())
            .getAllDependencies()
            .stream()
            .filter(dep -> !(dep instanceof ProjectDependency))
            .collect(Collectors.toSet());

    dummyDependencyConfiguration.getDependencies().addAll(nonProjectDependencies);
    try {
      return project
          .getConfigurations()
          .getByName(firebaseLibrary.getRuntimeClasspath())
          .getAllDependencies()
          .stream()
          .map(dep -> getType(dummyDependencyConfiguration, dep))
          .filter(Objects::nonNull)
          .collect(Collectors.toMap(lib -> lib.name, lib -> lib.type.getFormat()));
    } finally {
      project.getConfigurations().remove(dummyDependencyConfiguration);
    }
  }

  private static Library getType(Configuration config, Dependency d) {
    if (d instanceof ProjectDependency) {
      FirebaseLibraryExtension library =
          getFirebaseLibrary(((ProjectDependency) d).getDependencyProject());
      return new Library(library.getMavenName(), library.type);
    }

    Optional<Library> path =
        StreamSupport.stream(config.spliterator(), false)
            .map(File::getAbsolutePath)
            .filter(
                absPath ->
                    absPath.matches(
                        MessageFormat.format(
                            ".*\\Q{0}/{1}/{2}/\\E[a-zA-Z0-9]+/\\Q{1}-{2}.\\E[aj]ar",
                            d.getGroup(), d.getName(), d.getVersion())))
            .findFirst()
            .map(absPath -> absPath.endsWith(".aar") ? LibraryType.ANDROID : LibraryType.JAVA)
            .map(type -> new Library(d.getGroup() + ":" + d.getName(), type));
    return path.orElse(null);
  }

  private static FirebaseLibraryExtension getFirebaseLibrary(Project project) {
    return project.getExtensions().getByType(FirebaseLibraryExtension.class);
  }

  private static final class Library {
    private final String name;
    private final LibraryType type;

    private Library(String name, LibraryType type) {
      this.name = name;
      this.type = type;
    }
  }
}
