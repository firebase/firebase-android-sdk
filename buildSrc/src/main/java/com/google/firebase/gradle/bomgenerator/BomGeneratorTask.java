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

package com.google.firebase.gradle.bomgenerator;

import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.firebase.gradle.bomgenerator.model.Dependency;
import com.google.firebase.gradle.bomgenerator.model.VersionBump;
import com.google.firebase.gradle.bomgenerator.tagging.GitClient;
import com.google.firebase.gradle.bomgenerator.tagging.ShellExecutor;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public abstract class BomGeneratorTask extends DefaultTask {
  private static final List<String> BOM_ARTIFACTS =
      ImmutableList.of(
          "com.google.firebase:firebase-analytics",
          "com.google.firebase:firebase-analytics-ktx",
          "com.google.firebase:firebase-appcheck-debug",
          "com.google.firebase:firebase-appcheck-debug-testing",
          "com.google.firebase:firebase-appcheck-ktx",
          "com.google.firebase:firebase-appcheck-playintegrity",
          "com.google.firebase:firebase-appcheck",
          "com.google.firebase:firebase-auth",
          "com.google.firebase:firebase-auth-ktx",
          "com.google.firebase:firebase-common",
          "com.google.firebase:firebase-common-ktx",
          "com.google.firebase:firebase-config",
          "com.google.firebase:firebase-config-ktx",
          "com.google.firebase:firebase-crashlytics",
          "com.google.firebase:firebase-crashlytics-ktx",
          "com.google.firebase:firebase-crashlytics-ndk",
          "com.google.firebase:firebase-database",
          "com.google.firebase:firebase-database-ktx",
          "com.google.firebase:firebase-dynamic-links",
          "com.google.firebase:firebase-dynamic-links-ktx",
          "com.google.firebase:firebase-encoders",
          "com.google.firebase:firebase-firestore",
          "com.google.firebase:firebase-firestore-ktx",
          "com.google.firebase:firebase-functions",
          "com.google.firebase:firebase-functions-ktx",
          "com.google.firebase:firebase-inappmessaging",
          "com.google.firebase:firebase-inappmessaging-display",
          "com.google.firebase:firebase-inappmessaging-display-ktx",
          "com.google.firebase:firebase-inappmessaging-ktx",
          "com.google.firebase:firebase-installations",
          "com.google.firebase:firebase-installations-ktx",
          "com.google.firebase:firebase-messaging",
          "com.google.firebase:firebase-messaging-directboot",
          "com.google.firebase:firebase-messaging-ktx",
          "com.google.firebase:firebase-ml-modeldownloader",
          "com.google.firebase:firebase-ml-modeldownloader-ktx",
          "com.google.firebase:firebase-perf",
          "com.google.firebase:firebase-perf-ktx",
          "com.google.firebase:firebase-storage",
          "com.google.firebase:firebase-storage-ktx");
  private static final List<String> IGNORED_ARTIFACTS =
      ImmutableList.of(
          "crash-plugin",
          "firebase-ml-vision",
          "crashlytics",
          "firebase-ads",
          "firebase-ads-lite",
          "firebase-abt",
          "firebase-analytics-impl",
          "firebase-analytics-impl-license",
          "firebase-analytics-license",
          "firebase-annotations",
          "firebase-appcheck-interop",
          "firebase-appcheck-safetynet",
          "firebase-appdistribution-gradle",
          "firebase-appindexing-license",
          "firebase-appindexing",
          "firebase-iid",
          "firebase-core",
          "firebase-auth-common",
          "firebase-auth-impl",
          "firebase-auth-interop",
          "firebase-auth-license",
          "firebase-encoders-json",
          "firebase-encoders-proto",
          "firebase-auth-module",
          "firebase-bom",
          "firebase-common-license",
          "firebase-components",
          "firebase-config-license",
          "firebase-config-interop",
          "firebase-crash",
          "firebase-crash-license",
          "firebase-crashlytics-buildtools",
          "firebase-crashlytics-gradle",
          "firebase-database-collection",
          "firebase-database-connection",
          "firebase-database-connection-license",
          "firebase-database-license",
          "firebase-dataconnect",
          "firebase-datatransport",
          "firebase-appdistribution-ktx",
          "firebase-appdistribution",
          "firebase-appdistribution-api",
          "firebase-appdistribution-api-ktx",
          "firebase-dynamic-module-support",
          "firebase-dynamic-links-license",
          "firebase-functions-license",
          "firebase-iid-interop",
          "firebase-iid-license",
          "firebase-invites",
          "firebase-measurement-connector",
          "firebase-measurement-connector-impl",
          "firebase-messaging-license",
          "firebase-ml-common",
          "firebase-ml-vision-internal-vkp",
          "firebase-ml-model-interpreter",
          "firebase-perf-license",
          "firebase-plugins",
          "firebase-sessions",
          "firebase-storage-common",
          "firebase-storage-common-license",
          "firebase-storage-license",
          "firebase-vertexai",
          "perf-plugin",
          "play-services-ads",
          "protolite-well-known-types",
          "testlab-instr-lib",
          "firebase-installations-interop",
          "google-services",
          "gradle",
          "firebase-ml-vision-automl",
          "firebase-ml-vision-barcode-model",
          "firebase-ml-vision-face-model",
          "firebase-ml-vision-image-label-model",
          "firebase-ml-vision-object-detection-model",
          "firebase-ml-natural-language",
          "firebase-ml-natural-language-language-id-model",
          "firebase-ml-natural-language-smart-reply",
          "firebase-ml-natural-language-smart-reply-model",
          "firebase-ml-natural-language-translate",
          "firebase-ml-natural-language-translate-model");
  private static final List<String> IMPORTANT_NON_FIREBASE_LIBRARIES =
      ImmutableList.of(
          "com.google.android.gms:play-services-ads",
          "com.google.gms:google-services",
          "com.android.tools.build:gradle",
          "com.google.firebase:perf-plugin",
          "com.google.firebase:firebase-crashlytics-gradle",
          "com.google.firebase:firebase-appdistribution-gradle");

  private Set<String> ignoredFirebaseArtifacts;
  private Set<String> bomArtifacts;
  private Set<String> allFirebaseArtifacts;

  public Map<String, String> versionOverrides = new HashMap<>();

  @OutputDirectory
  public abstract DirectoryProperty getBomDirectory();

  /**
   * This task generates a current Bill of Materials (BoM) based on the latest versions of
   * everything in gMaven. This is meant to be a post-release task so that the BoM contains the most
   * recent versions of all artifacts.
   *
   * <p>This task also tags the release candidate commit with the BoM version, the new version of
   * releasing products, and the M version of the current release.
   *
   * <p>Version overrides may be given to this task in a map like so: versionOverrides =
   * ["com.google.firebase:firebase-firestore": "17.0.1"]
   */
  @TaskAction
  // TODO(yifany): needs a more accurate name
  public void generateBom() throws Exception {
    // Repo Access Setup
    RepositoryClient depPopulator = new RepositoryClient();

    // Prepare script by pulling the state of the world (checking configuration files and gMaven
    // artifacts)
    bomArtifacts = new HashSet(BOM_ARTIFACTS);
    ignoredFirebaseArtifacts = new HashSet(IGNORED_ARTIFACTS);
    allFirebaseArtifacts = depPopulator.getAllFirebaseArtifacts();
    allFirebaseArtifacts.addAll(IMPORTANT_NON_FIREBASE_LIBRARIES);

    // Find version for BoM artifact. First version released should be 15.0.0
    String currentVersion =
        depPopulator
            .getLastPublishedVersion(Dependency.create("com.google.firebase", "firebase-bom"))
            .orElse("15.0.0");

    // We need to get the content of the current BoM to compute version bumps.
    Map<String, String> previousBomVersions = getBomMap(currentVersion);

    // Generate list of firebase libraries, ping gmaven for current versions, and override as needed
    // from local settings
    List<Dependency> allFirebaseDependencies =
        buildVersionedDependencyList(depPopulator, previousBomVersions);

    List<Dependency> bomDependencies =
        allFirebaseDependencies.stream()
            .filter(dep -> bomArtifacts.contains(dep.fullArtifactId()))
            .collect(toList());

    // Sanity check that there are no unaccounted for artifacts that we might want in the BoM
    Set<String> bomArtifactIds =
        bomArtifacts.stream().map(x -> x.split(":")[1]).collect(Collectors.toSet());
    Set<String> allFirebaseArtifactIds =
        allFirebaseArtifacts.stream().map(x -> x.split(":")[1]).collect(Collectors.toSet());
    Set<String> invalidArtifacts =
        Sets.difference(
            Sets.difference(allFirebaseArtifactIds, bomArtifactIds), ignoredFirebaseArtifacts);

    if (!invalidArtifacts.isEmpty()) {
      throw new RuntimeException(
          "Some dependencies are unaccounted for, add to BomGeneratorTask#IGNORED_ARTIFACTS or "
              + "BomGeneratorTask#BOM_ARTIFACTS. Unaccounted for dependencies: "
              + invalidArtifacts.toString());
    }
    String version = findArtifactVersion(bomDependencies, currentVersion, previousBomVersions);

    // Surface generated pom for sanity checking and testing, and then write it.
    Path bomDir = getBomDirectory().getAsFile().get().toPath();
    PomXmlWriter xmlWriter = new PomXmlWriter(bomDependencies, version, bomDir);
    MarkdownDocumentationWriter documentationWriter =
        new MarkdownDocumentationWriter(
            bomDependencies, version, previousBomVersions, currentVersion);
    RecipeVersionWriter recipeWriter = new RecipeVersionWriter(allFirebaseDependencies);
    Document outputXmlDoc = xmlWriter.generatePomXml();
    String outputDocumentation = documentationWriter.generateDocumentation();
    String outputRecipe = recipeWriter.generateVersionUpdate();
    xmlWriter.writeXmlDocument(outputXmlDoc);
    documentationWriter.writeDocumentation(outputDocumentation);
    recipeWriter.writeVersionUpdate(outputRecipe);

    tagVersions(version, bomDependencies);
  }

  // Finds the version for the BoM artifact.
  private String findArtifactVersion(
      List<Dependency> firebaseDependencies,
      String currentVersion,
      Map<String, String> previousBomVersions)
      throws VersionRangeResolutionException {
    Optional<VersionBump> bump =
        firebaseDependencies.stream().map(Dependency::versionBump).distinct().sorted().findFirst();

    if (firebaseDependencies.size() < previousBomVersions.size()) {
      bump = Optional.of(VersionBump.MAJOR);
    }

    return bump.map(x -> VersionBump.bumpVersionBy(currentVersion, x))
        .orElseThrow(() -> new RuntimeException("Could not figure out how to bump version"));
  }

  private Dependency overrideVersion(Dependency dep) {
    if (versionOverrides.containsKey(dep.fullArtifactId())) {
      return Dependency.create(
          dep.groupId(),
          dep.artifactId(),
          versionOverrides.get(dep.fullArtifactId()),
          VersionBump.PATCH);
    } else {
      return dep;
    }
  }

  private List<Dependency> buildVersionedDependencyList(
      RepositoryClient depPopulator, Map<String, String> previousBomVersions) {
    return allFirebaseArtifacts.stream()
        .map(
            dep -> {
              String[] splitDep = dep.split(":");
              return Dependency.create(splitDep[0], splitDep[1]);
            })
        .map(dep -> depPopulator.populateDependencyVersion(dep, previousBomVersions))
        .map(this::overrideVersion)
        .collect(toList());
  }

  private Map<String, String> getBomMap(String bomVersion) {
    String bomUrl =
        "https://dl.google.com/dl/android/maven2/com/google/firebase/firebase-bom/"
            + bomVersion
            + "/firebase-bom-"
            + bomVersion
            + ".pom";
    try (InputStream index = new URL(bomUrl).openStream()) {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setValidating(true);
      factory.setIgnoringElementContentWhitespace(true);
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document doc = builder.parse(index);
      NodeList dependencyList = doc.getElementsByTagName("dependency");
      ImmutableMap.Builder<String, String> outputBuilder = ImmutableMap.builder();
      for (int i = 0; i < dependencyList.getLength(); i++) {
        Element artifact = (Element) dependencyList.item(i);
        String groupId = artifact.getElementsByTagName("groupId").item(0).getTextContent();
        String artifactId = artifact.getElementsByTagName("artifactId").item(0).getTextContent();
        String version = artifact.getElementsByTagName("version").item(0).getTextContent();
        outputBuilder.put(groupId + ":" + artifactId, version);
      }
      return outputBuilder.build();
    } catch (SAXException | IOException | ParserConfigurationException e) {
      throw new RuntimeException("Failed to get contents of BoM version " + bomVersion, e);
    }
  }

  private void tagVersions(String bomVersion, List<Dependency> firebaseDependencies) {
    Logger logger = this.getProject().getLogger();
    if (!System.getenv().containsKey("FIREBASE_CI")) {
      logger.warn("Tagging versions is skipped for non-CI environments.");
      return;
    }

    String mRelease = System.getenv("PULL_BASE_REF");
    String rcCommit = System.getenv("PULL_BASE_SHA");
    ShellExecutor executor = new ShellExecutor(Paths.get(".").toFile(), logger::lifecycle);
    GitClient git = new GitClient(mRelease, rcCommit, executor, logger::lifecycle);
    git.tagReleaseVersion();
    git.tagBomVersion(bomVersion);
    firebaseDependencies.stream()
        .filter(d -> d.versionBump() != VersionBump.NONE)
        .forEach(d -> git.tagProductVersion(d.artifactId(), d.version()));
    git.pushCreatedTags();
  }
}
